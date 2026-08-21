package com.jobdiscovery.scoring;

import com.jobdiscovery.application.ApplicationStatus;
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
 * @param explanation      the Phase 5b sentence, or {@code null} when it was
 *                         not asked for. It is derived <i>from</i> the fields
 *                         above and never feeds back into {@link #score}
 * @param applicationStatus where you have got to with this job, or {@code null}
 *                         if it is not tracked. Attached after scoring, so the
 *                         engine stays pure and a tracked job's rank is
 *                         unaffected by the fact that it is tracked
 * @param explanationSource which tier wrote it — {@code "ollama"},
 *                         {@code "claude"} or {@code "templated"}. Present so a
 *                         reader is never left guessing whether a sentence came
 *                         from a model or from a rule
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
        List<ScoreComponent> components,
        String explanation,
        String explanationSource,
        ApplicationStatus applicationStatus) {

    /**
     * The same match with an explanation attached, and a note of which tier
     * produced it.
     *
     * <p>A copy rather than a setter, so the scored result stays immutable and
     * the explanation can never be mistaken for an input to the score.
     */
    public JobScore withExplanation(String explanation, String explanationSource) {
        return new JobScore(jobId, title, company, location, source, applyUrl, postedDate,
                score, matchedSkills, missingSkills, matchedKeywords,
                jobSeniority, requiredYears, components, explanation, explanationSource,
                applicationStatus);
    }

    /**
     * The same match annotated with where the application has got to.
     *
     * <p>Applied after ranking rather than during it. A job you have applied to
     * is not a better or worse match than it was yesterday, so this must not
     * touch the score — it only stops you re-reading something you have already
     * dealt with.
     */
    public JobScore withApplicationStatus(ApplicationStatus status) {
        return new JobScore(jobId, title, company, location, source, applyUrl, postedDate,
                score, matchedSkills, missingSkills, matchedKeywords,
                jobSeniority, requiredYears, components, explanation, explanationSource,
                status);
    }
}
