package com.jobdiscovery.scoring;

/**
 * One dimension's contribution to a job's score, kept as data so the breakdown
 * can be shown to a human — and, in the LLM half of Phase 5, handed to the model
 * as the evidence it must explain rather than re-derive.
 *
 * <p>A dimension can be <b>not applicable</b>: if the profile lists no preferred
 * companies there is nothing to score, so rather than award 0 (which would drag
 * every job down equally and distort the ranking) the dimension drops out and
 * its weight is excluded from the divisor.
 *
 * @param name       the dimension, e.g. "skills"
 * @param applicable whether this dimension could be judged at all
 * @param weight     its configured weight
 * @param value      how well the job did, 0.0–1.0 (0 when not applicable)
 * @param points     {@code weight * value} — what this dimension actually earned
 * @param detail     a short human-readable reason, shown in the breakdown
 */
public record ScoreComponent(
        String name,
        boolean applicable,
        double weight,
        double value,
        double points,
        String detail) {

    /** An applicable dimension scoring {@code value} (clamped to 0.0–1.0). */
    public static ScoreComponent of(String name, double weight, double value, String detail) {
        double clamped = Math.max(0.0, Math.min(1.0, value));
        return new ScoreComponent(name, true, weight, clamped,
                round(weight * clamped), detail);
    }

    /** A dimension with nothing to judge; its weight leaves the calculation. */
    public static ScoreComponent notApplicable(String name, double weight, String reason) {
        return new ScoreComponent(name, false, weight, 0.0, 0.0, reason);
    }

    private static double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
