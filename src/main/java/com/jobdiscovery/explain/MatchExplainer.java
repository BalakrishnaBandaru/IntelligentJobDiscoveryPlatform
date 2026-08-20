package com.jobdiscovery.explain;

import com.jobdiscovery.scoring.JobScore;
import com.jobdiscovery.scoring.ScoreComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Puts a scored match into words.
 *
 * <p><b>The model never produces or adjusts the number.</b> It is handed the
 * score and the evidence the rule engine already derived — matched and missing
 * skills, the seniority read, the stated experience range, the per-dimension
 * breakdown — and asked only to phrase them. It is never shown the raw posting,
 * which is precisely why it cannot form its own opinion of the fit. That split
 * is what keeps the ranking auditable: the number comes from code you can
 * unit-test, and the sentence comes from the number.
 *
 * <p>Explanations are cached in memory, keyed by job and score. Scoring is
 * recomputed on every request, so without this a second call to the same
 * endpoint would pay for the same sentences again. Re-tuning a weight changes
 * the score, which changes the key, which expires the entry — no invalidation
 * logic to get wrong.
 */
@Service
public class MatchExplainer {

    private static final Logger log = LoggerFactory.getLogger(MatchExplainer.class);

    private static final String SYSTEM_PROMPT = """
            You explain why a job matches a candidate. You are part of a pipeline \
            where a deterministic rule engine has ALREADY scored the match.

            Rules, in order of importance:
            1. The score is given to you. Never invent, recompute, adjust, or \
               dispute it. If the evidence looks thin, that is the honest \
               picture — describe it, do not compensate for it.
            2. Use only the evidence provided. You are deliberately not shown the \
               job posting, so do not speculate about responsibilities, salary, \
               culture, or anything else that is not in the evidence.
            3. A missing skill marked "not evidenced" means the posting text was \
               truncated by the job board, so the skill may or may not be wanted. \
               Say "not mentioned", never "the job does not require it".
            4. Two or three sentences, plain prose, no bullet points, no headings, \
               no preamble. Address the candidate as "you".
            5. Lead with the single strongest reason this ranks where it does, \
               then the most useful caveat.
            """;

    private final ClaudeClient client;
    private final ExplanationProperties properties;
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public MatchExplainer(ClaudeClient client, ExplanationProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    public boolean isConfigured() {
        return client.isConfigured();
    }

    /**
     * Returns the matches with explanations attached to the top ones.
     *
     * <p>Only the first {@code explanation.max-matches} get one — each is a
     * separate API call, and explaining a 30-point match nobody will read is
     * money spent for nothing. The rest come back untouched.
     *
     * @throws ExplanationException if explanations are not configured
     */
    public List<JobScore> explainAll(List<JobScore> matches) {
        if (!isConfigured()) {
            throw new ExplanationException(ExplanationException.NOT_CONFIGURED,
                    "Match explanations are not configured. Set ANTHROPIC_API_KEY in your .env "
                    + "file and EXPLANATION_ENABLED=true, then recreate the app container.");
        }

        List<JobScore> result = new ArrayList<>(matches.size());
        int explained = 0;
        for (JobScore match : matches) {
            if (explained < properties.maxMatches()) {
                result.add(match.withExplanation(explain(match)));
                explained++;
            } else {
                result.add(match);
            }
        }
        return result;
    }

    /** One explanation, from cache when the same job scored the same before. */
    public String explain(JobScore match) {
        String key = match.jobId() + "@" + match.score();
        return cache.computeIfAbsent(key, ignored -> {
            log.debug("Requesting an explanation for job {} (score {})",
                    match.jobId(), match.score());
            return client.complete(SYSTEM_PROMPT, buildPrompt(match));
        });
    }

    /**
     * Renders the evidence as text. Everything here comes off {@link JobScore};
     * the posting itself is deliberately absent.
     */
    String buildPrompt(JobScore match) {
        StringBuilder sb = new StringBuilder();
        sb.append("Job: ").append(match.title()).append('\n');
        sb.append("Company: ").append(nullSafe(match.company())).append('\n');
        sb.append("Location: ").append(nullSafe(match.location())).append('\n');
        sb.append("Score: ").append(match.score()).append(" out of 100\n");
        sb.append("Level read from the title: ").append(match.jobSeniority()).append('\n');

        if (match.requiredYears() != null) {
            sb.append("Experience the posting asks for: ")
                    .append(match.requiredYears().describe()).append('\n');
        } else {
            sb.append("Experience the posting asks for: not stated\n");
        }

        sb.append("Your skills this posting names: ")
                .append(joinOrNone(match.matchedSkills())).append('\n');
        sb.append("Your skills not evidenced in the text: ")
                .append(joinOrNone(match.missingSkills())).append('\n');
        sb.append("Your keywords it names: ")
                .append(joinOrNone(match.matchedKeywords())).append('\n');

        sb.append("\nHow the score breaks down:\n");
        for (ScoreComponent component : match.components()) {
            sb.append("- ").append(component.name()).append(": ");
            if (component.applicable()) {
                sb.append(component.points()).append(" of ").append(component.weight())
                        .append(" points — ").append(component.detail());
            } else {
                sb.append("not applicable — ").append(component.detail());
            }
            sb.append('\n');
        }

        sb.append("\nExplain this match in two or three sentences.");
        return sb.toString();
    }

    /** Visible for tests: how many explanations are currently memoised. */
    int cacheSize() {
        return cache.size();
    }

    private static String joinOrNone(List<String> values) {
        return values == null || values.isEmpty() ? "none" : String.join(", ", values);
    }

    private static String nullSafe(String value) {
        return value == null || value.isBlank() ? "not stated" : value;
    }
}
