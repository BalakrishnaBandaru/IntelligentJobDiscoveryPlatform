package com.jobdiscovery.scoring;

import java.util.List;
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
public class MatchController {

    private final JobScoringService scoringService;

    public MatchController(JobScoringService scoringService) {
        this.scoringService = scoringService;
    }

    /**
     * @param limit    how many matches to return
     * @param minScore drop anything scoring below this (0–100)
     * @param source   optional filter, e.g. {@code ADZUNA} or {@code JOOBLE}
     */
    @GetMapping
    public List<JobScore> matches(
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") double minScore,
            @RequestParam(required = false) String source) {
        return scoringService.rank(limit, minScore, source);
    }
}
