package com.jobdiscovery.scoring;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Years of experience a posting asks for, parsed out of its free text.
 *
 * <p>A title tells you roughly how senior a role is; the description usually
 * says it outright ("5+ years", "3-6 years of experience"). When that explicit
 * range is present it beats the title guess, so the rule engine prefers it.
 *
 * @param minYears the lower bound, always present when a requirement was found
 * @param maxYears the upper bound, or {@code null} for an open-ended "5+ years"
 */
public record ExperienceRequirement(int minYears, Integer maxYears) {

    /**
     * A number, optionally a second number after a separator, then a years unit.
     *
     * <p>Runs against {@link TextNormalizer#normalize} output, where punctuation
     * has already collapsed to spaces — so "3-6 years" arrives as "3 6 years"
     * and the separator alternatives only need to cover surviving words.
     */
    private static final Pattern YEARS = Pattern.compile(
            "(\\d{1,2})\\s*\\+?\\s*(?:to\\s*|or\\s*)?(\\d{1,2})?\\s*\\+?\\s*(?:years?|yrs?)");

    /** How far either side of a match we look for a word confirming context. */
    private static final int CONTEXT_WINDOW = 60;

    /**
     * Finds the first stated experience requirement, or {@code null} if the text
     * states none.
     *
     * <p>A bare "5 years" nearby is not enough — the text must also mention
     * experience, otherwise "founded 5 years ago" or "5 years of double-digit
     * growth" would be read as a requirement.
     */
    public static ExperienceRequirement parse(String... texts) {
        for (String text : texts) {
            String normalized = TextNormalizer.normalize(text);
            if (normalized.isEmpty()) {
                continue;
            }
            Matcher matcher = YEARS.matcher(normalized);
            while (matcher.find()) {
                if (!hasExperienceContext(normalized, matcher.start(), matcher.end())) {
                    continue;
                }
                int first = Integer.parseInt(matcher.group(1));
                Integer second = matcher.group(2) == null ? null : Integer.parseInt(matcher.group(2));

                // One number is an open-ended floor ("5 years experience" means
                // at least five). Two numbers are a closed range.
                if (second == null) {
                    return new ExperienceRequirement(first, null);
                }
                // Guard against a reversed or nonsensical pair.
                if (second < first) {
                    return new ExperienceRequirement(second, first);
                }
                return new ExperienceRequirement(first, second);
            }
        }
        return null;
    }

    /** True if "experience" or "exp" appears close to the matched years phrase. */
    private static boolean hasExperienceContext(String text, int start, int end) {
        int from = Math.max(0, start - CONTEXT_WINDOW);
        int to = Math.min(text.length(), end + CONTEXT_WINDOW);
        String window = text.substring(from, to);
        return window.contains("experience") || window.contains(" exp ");
    }

    /** True when {@code years} sits inside the stated range. */
    public boolean isSatisfiedBy(int years) {
        if (years < minYears) {
            return false;
        }
        return maxYears == null || years <= maxYears;
    }

    /** Human-readable form for the score breakdown, e.g. "5-8 years" or "5+ years". */
    public String describe() {
        if (maxYears == null) {
            return minYears + "+ years";
        }
        if (maxYears == minYears) {
            return minYears + " years";
        }
        return minYears + "-" + maxYears + " years";
    }
}
