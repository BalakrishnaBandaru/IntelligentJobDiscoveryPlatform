package com.jobdiscovery.application;

/**
 * Where an application has got to.
 *
 * <p>Ordered as the process runs, so {@link #ordinal()} gives a sensible funnel
 * order without a separate sort key. {@link #REJECTED} and {@link #WITHDRAWN}
 * sit at the end as terminal states rather than stages.
 */
public enum ApplicationStatus {

    /** Shortlisted but not yet applied to. The "come back to this" pile. */
    SAVED(false),

    APPLIED(false),
    SCREENING(false),
    INTERVIEW(false),
    OFFER(false),

    /** They said no. */
    REJECTED(true),

    /** You said no, or stopped pursuing it. */
    WITHDRAWN(true);

    private final boolean terminal;

    ApplicationStatus(boolean terminal) {
        this.terminal = terminal;
    }

    /** True when nothing further is expected to happen. */
    public boolean isTerminal() {
        return terminal;
    }

    /**
     * Whether reaching this status means an application was actually submitted.
     * {@code SAVED} has not been; everything else has, including the two
     * terminal states — a rejection implies you applied.
     */
    public boolean isSubmitted() {
        return this != SAVED;
    }
}
