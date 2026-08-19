package com.jobdiscovery.scoring;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Tunable weights for the deterministic rule engine, bound from {@code scoring.*}.
 *
 * <p>Weights are relative, not percentages — the final score divides earned
 * points by the weight of the dimensions that actually applied, so a profile
 * that sets no preferred companies is not silently marked down for it. They are
 * expressed out of 100 anyway, because that makes the intent obvious at a
 * glance.
 *
 * <p>Kept configurable so ranking can be re-tuned by editing YAML and
 * restarting, without touching the engine.
 */
@ConfigurationProperties(prefix = "scoring")
public record ScoringProperties(@DefaultValue Weights weights) {

    /**
     * @param skills           overlap between profile skills and the posting
     * @param seniority        how well the posting's level fits the candidate's
     *                         years — the heaviest signal after skills, because
     *                         a "java developer" search returns a wide spread of
     *                         seniorities
     * @param location         posting location against preferred locations
     * @param keywords         profile keywords beyond the hard skills list
     * @param preferredCompany bonus when the employer is one the candidate named
     * @param recency          how recently the posting went up
     */
    public record Weights(
            @DefaultValue("35") double skills,
            @DefaultValue("25") double seniority,
            @DefaultValue("20") double location,
            @DefaultValue("10") double keywords,
            @DefaultValue("5") double preferredCompany,
            @DefaultValue("5") double recency) {
    }
}
