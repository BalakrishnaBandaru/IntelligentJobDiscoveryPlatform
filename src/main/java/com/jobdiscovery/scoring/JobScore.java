package com.jobdiscovery.scoring;

import java.time.Instant;
import java.util.List;

/**
 * A scored job: the number, and the evidence behind it.
 *
 * <p>The evidence fields are not decoration. Phase 5's second half feeds this
 * record to an LLM so it can put the match into a sentence — and the whole point
 * of the design is that the model explains {@link #score} rather than inventing
 * one, which it can only do if the facts arrive with the number.
 *
 * @param jobId            the underlying {@code JobListing} id
 * @param score            0–100, from the deterministic rule engine only
 * @param matchedSkills    profile skills the posting names
 * @param missingSkills    profile skills it does not — the honest half
 * @param matchedKeywords  profile keywords the posting names
 * @param jobSeniority     level inferred from the title
 * @param requiredYears    experience the posting states, or {@code null}
 * @param components       per-dimension breakdown that sums to {@link #score}
 */
public record JobScore(
        Long jobId,
        String title,
        String company,
        String location,
        String source,
        String applyUrl,
        Instant postedDate,
        double score,
        List<String> matchedSkills,
        List<String> missingSkills,
        List<String> matchedKeywords,
        SeniorityLevel jobSeniority,
        ExperienceRequirement requiredYears,
        List<ScoreComponent> components) {
}
