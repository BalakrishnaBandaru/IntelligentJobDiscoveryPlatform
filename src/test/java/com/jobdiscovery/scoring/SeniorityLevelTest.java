package com.jobdiscovery.scoring;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SeniorityLevelTest {

    @Test
    @DisplayName("reads the obvious markers out of a title")
    void readsMarkers() {
        assertEquals(SeniorityLevel.SENIOR, SeniorityLevel.fromTitle("Senior Java Developer"));
        assertEquals(SeniorityLevel.SENIOR, SeniorityLevel.fromTitle("Sr. Backend Engineer"));
        assertEquals(SeniorityLevel.JUNIOR, SeniorityLevel.fromTitle("Junior Java Developer"));
        assertEquals(SeniorityLevel.JUNIOR, SeniorityLevel.fromTitle("Fresher Java Developer"));
        assertEquals(SeniorityLevel.INTERN, SeniorityLevel.fromTitle("Java Developer Trainee"));
        assertEquals(SeniorityLevel.LEAD, SeniorityLevel.fromTitle("Engineering Manager, Payments"));
        assertEquals(SeniorityLevel.LEAD, SeniorityLevel.fromTitle("Tech Lead - Java"));
        assertEquals(SeniorityLevel.PRINCIPAL, SeniorityLevel.fromTitle("Principal Engineer"));
        assertEquals(SeniorityLevel.PRINCIPAL, SeniorityLevel.fromTitle("Software Architect"));
    }

    @Test
    @DisplayName("the most senior marker in a title wins")
    void mostSeniorMarkerWins() {
        // "Senior Staff Engineer" is a staff role, not a senior one - checking
        // markers in the wrong order would under-rate it by two levels.
        assertEquals(SeniorityLevel.PRINCIPAL, SeniorityLevel.fromTitle("Senior Staff Engineer"));
    }

    @Test
    @DisplayName("an unmarked title is mid-level, not junior")
    void unmarkedIsMid() {
        // Treating a bare "Java Developer" as junior would wrongly penalise the
        // single most common title in the whole feed.
        assertEquals(SeniorityLevel.MID, SeniorityLevel.fromTitle("Java Developer"));
        assertEquals(SeniorityLevel.MID, SeniorityLevel.fromTitle(null));
    }

    @Test
    @DisplayName("markers match whole tokens, not substrings")
    void matchesWholeTokens() {
        // "Leadership" contains "lead"; a substring check would call this a lead role.
        assertEquals(SeniorityLevel.MID, SeniorityLevel.fromTitle("Java Developer, Leadership Track"));
    }

    @Test
    @DisplayName("years of experience map onto the same ladder")
    void mapsYears() {
        assertEquals(SeniorityLevel.INTERN, SeniorityLevel.forExperienceYears(0));
        assertEquals(SeniorityLevel.JUNIOR, SeniorityLevel.forExperienceYears(2));
        assertEquals(SeniorityLevel.MID, SeniorityLevel.forExperienceYears(4));
        assertEquals(SeniorityLevel.SENIOR, SeniorityLevel.forExperienceYears(10));
        assertEquals(SeniorityLevel.LEAD, SeniorityLevel.forExperienceYears(12));
        assertEquals(SeniorityLevel.PRINCIPAL, SeniorityLevel.forExperienceYears(20));
    }
}
