package com.jobdiscovery.notify;

import com.jobdiscovery.application.ApplicationService;
import com.jobdiscovery.explain.MatchExplainer;
import com.jobdiscovery.job.JobListingRepository;
import com.jobdiscovery.scoring.JobScore;
import com.jobdiscovery.scoring.JobScoringService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Selects what is worth telling you about, sends it, and remembers that it did.
 *
 * <p>The last part is what makes a digest tolerable. The shortlist is recomputed
 * from the whole table every time, so without a record of what has already been
 * sent, every morning's message would be near-identical to the last — and a
 * notification that repeats itself is one you stop opening.
 *
 * <p>Listings are marked notified <b>only after the send succeeds</b>. Marking
 * first would be simpler and would silently drop those jobs from every future
 * digest the moment a send failed.
 */
@Service
public class DigestService {

    private static final Logger log = LoggerFactory.getLogger(DigestService.class);

    /**
     * How deep to score before filtering. Generous, because most of the ranked
     * list will already have been notified — taking only the top few would
     * quickly leave nothing new to say.
     */
    private static final int RANK_DEPTH = 200;

    private final JobScoringService scoringService;
    private final MatchExplainer explainer;
    private final JobListingRepository jobRepository;
    private final ApplicationService applicationService;
    private final TelegramClient client;
    private final DigestFormatter formatter;
    private final TelegramProperties properties;

    public DigestService(JobScoringService scoringService, MatchExplainer explainer,
                         JobListingRepository jobRepository,
                         ApplicationService applicationService, TelegramClient client,
                         DigestFormatter formatter, TelegramProperties properties) {
        this.scoringService = scoringService;
        this.explainer = explainer;
        this.jobRepository = jobRepository;
        this.applicationService = applicationService;
        this.client = client;
        this.formatter = formatter;
        this.properties = properties;
    }

    public boolean isConfigured() {
        return client.isConfigured();
    }

    /**
     * Builds and sends a digest of matches not previously notified.
     *
     * @param force include matches even if they have been sent before — for
     *              testing the wiring without waiting for new jobs to appear
     * @return what was sent, or an empty result when there was nothing to say
     * @throws NotificationException if Telegram is not configured, or the send
     *                               fails
     */
    @Transactional
    public DigestResult sendDigest(boolean force) {
        if (!isConfigured()) {
            throw new NotificationException(NotificationException.NOT_CONFIGURED,
                    "Telegram is not configured. Create a bot with @BotFather, then set "
                    + "TELEGRAM_BOT_TOKEN, TELEGRAM_CHAT_ID and TELEGRAM_ENABLED=true in "
                    + "your .env file and recreate the app container.");
        }

        List<JobScore> candidates = selectCandidates(force);
        if (candidates.isEmpty()) {
            log.info("Digest skipped: nothing new above {}", properties.minScore());
            return DigestResult.nothingToSend();
        }

        // Explanations are best-effort. A digest with plain matches is far
        // better than no digest because the explainer was unavailable.
        List<JobScore> explained = candidates;
        try {
            explained = explainer.explainAll(candidates);
        } catch (RuntimeException e) {
            log.warn("Digest going out without explanations: {}", e.getMessage());
        }

        client.sendMessage(formatter.format(explained));

        List<Long> ids = new ArrayList<>(explained.size());
        for (JobScore match : explained) {
            ids.add(match.jobId());
        }
        jobRepository.markNotified(ids, Instant.now());

        log.info("Digest sent: {} match(es), top score {}", ids.size(), explained.get(0).score());
        return new DigestResult(true, ids.size(), explained.get(0).score(), ids);
    }

    /**
     * Builds exactly the message {@link #sendDigest} would send, without
     * sending it and without marking anything notified.
     *
     * <p>Needs no bot token, which is the point: it makes the selection and the
     * formatting — where the real failure modes are, given Telegram rejects a
     * message whose HTML does not parse — checkable before any credentials
     * exist, and lets you see what you are signing up for.
     */
    @Transactional(readOnly = true)
    public String preview(boolean force) {
        List<JobScore> candidates = selectCandidates(force);
        if (candidates.isEmpty()) {
            return "(nothing new above " + properties.minScore() + ")";
        }
        List<JobScore> explained = candidates;
        try {
            explained = explainer.explainAll(candidates);
        } catch (RuntimeException e) {
            log.warn("Preview without explanations: {}", e.getMessage());
        }
        return formatter.format(explained);
    }

    /** Ranked matches above the threshold that have not been sent before. */
    private List<JobScore> selectCandidates(boolean force) {
        List<JobScore> ranked =
                scoringService.rank(RANK_DEPTH, properties.minScore(), null);

        Set<Long> alreadySent = force ? Set.of() : jobRepository.findNotifiedIds();

        // A job you have already applied to, saved, or been rejected from is
        // not a decision waiting to be made. Excluded even under force, because
        // force exists to re-test the wiring, not to re-open closed decisions.
        Set<Long> tracked = applicationService.trackedJobIds();

        List<JobScore> candidates = new ArrayList<>();
        for (JobScore match : ranked) {
            if (candidates.size() >= properties.maxJobs()) {
                break;
            }
            if (tracked.contains(match.jobId())) {
                continue;
            }
            if (!alreadySent.contains(match.jobId())) {
                candidates.add(match);
            }
        }
        return candidates;
    }
}
