package com.jobdiscovery.scoring;

import io.swagger.v3.oas.annotations.tags.Tag;
import com.jobdiscovery.application.ApplicationService;
import com.jobdiscovery.application.ApplicationStatus;
import com.jobdiscovery.explain.MatchExplainer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The ranked shortlist — stored listings scored against the candidate profile.
 *
 * <p>Returns {@code 404 profile_not_found} until a profile is set, since there
 * is nothing to score against.
 */
@RestController
@RequestMapping("/api/matches")
@Tag(name = "Matches")
public class MatchController {

    private final JobScoringService scoringService;
    private final MatchExplainer explainer;
    private final ApplicationService applicationService;

    public MatchController(JobScoringService scoringService, MatchExplainer explainer,
                           ApplicationService applicationService) {
        this.scoringService = scoringService;
        this.explainer = explainer;
        this.applicationService = applicationService;
    }

    /**
     * @param limit    how many matches to return
     * @param minScore drop anything scoring below this (0–100)
     * @param source   optional filter, e.g. {@code ADZUNA} or {@code JOOBLE}
     * @param explain  attach an LLM-written explanation to the top matches.
     *                 Off by default because it costs money per call — and
     *                 because the ranking itself must never depend on it
     */
    @GetMapping
    public List<JobScore> matches(
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") double minScore,
            @RequestParam(required = false) String source,
            @RequestParam(defaultValue = "false") boolean explain) {
        List<JobScore> ranked = scoringService.rank(limit, minScore, source);
        if (explain) {
            ranked = explainer.explainAll(ranked);
        }
        return annotateWithApplications(ranked);
    }

    /**
     * Marks any match you have already acted on. Applied after ranking, never
     * during it — tracking a job does not make it a better or worse match, it
     * just means you no longer need to decide about it.
     */
    private List<JobScore> annotateWithApplications(List<JobScore> matches) {
        Map<Long, ApplicationStatus> byJob = applicationService.statusByJobId();
        if (byJob.isEmpty()) {
            return matches;
        }
        List<JobScore> annotated = new ArrayList<>(matches.size());
        for (JobScore match : matches) {
            ApplicationStatus status = byJob.get(match.jobId());
            annotated.add(status == null ? match : match.withApplicationStatus(status));
        }
        return annotated;
    }
}
