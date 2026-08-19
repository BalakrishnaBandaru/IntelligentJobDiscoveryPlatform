package com.jobdiscovery.scoring;

import java.util.List;

/**
 * The seniority a posting is pitched at, inferred from its title.
 *
 * <p>This exists because of a concrete problem: the daily fetch searches
 * "java developer", which surfaces a lot of 2–5 year roles that a candidate
 * with ten years' experience should not see near the top of a shortlist.
 * Ranking without a seniority signal buries the good matches.
 *
 * <p>The {@link #rank} is an ordinal used to measure the distance between what
 * a posting wants and what the candidate has — see
 * {@link JobScoringService#scoreSeniority}.
 */
public enum SeniorityLevel {

    INTERN(0),
    JUNIOR(1),
    MID(2),
    SENIOR(3),
    LEAD(4),
    PRINCIPAL(5);

    private final int rank;

    SeniorityLevel(int rank) {
        this.rank = rank;
    }

    public int rank() {
        return rank;
    }

    // Marker tokens, checked most-senior first so "Senior Staff Engineer"
    // resolves to PRINCIPAL rather than stopping at SENIOR.
    private static final List<String> PRINCIPAL_MARKERS =
            List.of("principal", "staff", "architect", "distinguished", "fellow",
                    "head", "director", "vp", "cto");
    private static final List<String> LEAD_MARKERS =
            List.of("lead", "leader", "manager");
    private static final List<String> SENIOR_MARKERS =
            List.of("senior", "sr", "snr");
    private static final List<String> INTERN_MARKERS =
            List.of("intern", "internship", "trainee", "apprentice");
    private static final List<String> JUNIOR_MARKERS =
            List.of("junior", "jr", "graduate", "grad", "entry", "fresher", "associate");

    /**
     * Infers the level from a job title, defaulting to {@link #MID} when the
     * title carries no marker — an unqualified "Java Developer" is mid-level by
     * convention, and treating it as junior would wrongly penalise it.
     */
    public static SeniorityLevel fromTitle(String title) {
        List<String> tokens = TextNormalizer.tokenize(title);
        if (containsAny(tokens, PRINCIPAL_MARKERS)) {
            return PRINCIPAL;
        }
        if (containsAny(tokens, LEAD_MARKERS)) {
            return LEAD;
        }
        if (containsAny(tokens, SENIOR_MARKERS)) {
            return SENIOR;
        }
        if (containsAny(tokens, INTERN_MARKERS)) {
            return INTERN;
        }
        if (containsAny(tokens, JUNIOR_MARKERS)) {
            return JUNIOR;
        }
        return MID;
    }

    /** The level a candidate with this many years of experience sits at. */
    public static SeniorityLevel forExperienceYears(int years) {
        if (years < 1) {
            return INTERN;
        }
        if (years < 3) {
            return JUNIOR;
        }
        if (years < 6) {
            return MID;
        }
        if (years < 11) {
            return SENIOR;
        }
        if (years < 15) {
            return LEAD;
        }
        return PRINCIPAL;
    }

    private static boolean containsAny(List<String> tokens, List<String> markers) {
        for (String marker : markers) {
            if (tokens.contains(marker)) {
                return true;
            }
        }
        return false;
    }
}
