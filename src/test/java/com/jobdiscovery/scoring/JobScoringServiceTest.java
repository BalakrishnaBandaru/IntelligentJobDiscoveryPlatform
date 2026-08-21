package com.jobdiscovery.scoring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jobdiscovery.job.JobListing;
import com.jobdiscovery.profile.CandidateProfile;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests the rule engine's pure scoring function. No Spring context and no
 * database: {@code score()} takes its inputs as arguments and its clock as a
 * parameter, which is the whole point of keeping the scoring deterministic.
 */
class JobScoringServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-19T00:00:00Z");

    // The repository and profile service are only used by rank(); score() is
    // pure, so they are not needed here.
    private final JobScoringService service = serviceWith(0.5);

    /** A service whose only difference is how hard truncated misses count. */
    private static JobScoringService serviceWith(double truncatedMissWeight) {
        return new JobScoringService(null, null, new ScoringProperties(
                new ScoringProperties.Weights(35, 25, 20, 10, 5, 5), "India",
                truncatedMissWeight));
    }

    // --- fixtures ----------------------------------------------------------

    private JobListing job(String title, String company, String location,
                           String description, Instant posted) {
        JobListing listing = new JobListing(title, company, location, description,
                "https://example.com/apply", "ADZUNA", posted, NOW);
        listing.setId(1L);
        return listing;
    }

    private CandidateProfile profile(List<String> skills, int years, List<String> locations,
                                     List<String> companies, List<String> keywords) {
        CandidateProfile profile = new CandidateProfile();
        profile.setSkills(skills);
        profile.setExperienceYears(years);
        profile.setPreferredLocations(locations);
        profile.setPreferredCompanies(companies);
        profile.setKeywords(keywords);
        return profile;
    }

    /** The candidate this project was built for: 10 years, Java, Bangalore. */
    private CandidateProfile seniorJavaCandidate() {
        return profile(List.of("Java", "Spring Boot"), 10, List.of("Bangalore"),
                List.of(), List.of("backend"));
    }

    private ScoreComponent component(JobScore scored, String name) {
        return scored.components().stream()
                .filter(c -> c.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no component named " + name));
    }

    // --- tests -------------------------------------------------------------

    @Test
    @DisplayName("a job matching on every applicable dimension scores 100")
    void perfectMatchScoresFull() {
        JobScore scored = service.score(
                job("Senior Java Developer", "Acme", "Bangalore",
                        "Spring Boot backend role, 8+ years of experience required", NOW),
                seniorJavaCandidate(), NOW);

        assertEquals(100.0, scored.score(), 0.01);
    }

    @Test
    @DisplayName("the score never leaves 0-100")
    void scoreStaysInRange() {
        JobScore worst = service.score(
                job("Junior PHP Intern", "Acme", "Warsaw", "1-2 years of experience",
                        NOW.minus(400, ChronoUnit.DAYS)),
                seniorJavaCandidate(), NOW);

        assertTrue(worst.score() >= 0.0, "score was " + worst.score());
        assertTrue(worst.score() <= 100.0, "score was " + worst.score());
    }

    @Test
    @DisplayName("a stated requirement well below the candidate is penalised as over-qualified")
    void penalisesOverQualification() {
        // The known issue this dimension was built for: the daily fetch searches
        // "java developer" and returns a lot of 2-4 year roles.
        CandidateProfile candidate = seniorJavaCandidate();
        Instant posted = NOW.minus(1, ChronoUnit.DAYS);

        JobScore juniorRole = service.score(
                job("Java Developer", "Acme", "Bangalore",
                        "Spring Boot work, 2-4 years of experience", posted),
                candidate, NOW);
        JobScore seniorRole = service.score(
                job("Java Developer", "Acme", "Bangalore",
                        "Spring Boot work, 8+ years of experience", posted),
                candidate, NOW);

        assertTrue(component(juniorRole, "seniority").value()
                        < component(seniorRole, "seniority").value(),
                "over-qualified role should score lower on seniority");
        assertTrue(juniorRole.score() < seniorRole.score(),
                "over-qualified role should rank below the well-matched one");
    }

    @Test
    @DisplayName("an explicit requirement beats the level implied by the title")
    void statedRequirementBeatsTitle() {
        // Title says "Senior", body says 2-3 years. The body is the real signal.
        JobScore scored = service.score(
                job("Senior Java Developer", "Acme", "Bangalore",
                        "Spring Boot, 2-3 years of experience", NOW),
                seniorJavaCandidate(), NOW);

        ScoreComponent seniority = component(scored, "seniority");
        assertTrue(seniority.value() < 1.0,
                "should not score a perfect seniority fit; was " + seniority.value());
        assertTrue(seniority.detail().contains("2-3 years"), seniority.detail());
    }

    @Test
    @DisplayName("Bangalore and Bengaluru are the same place")
    void matchesCityAliases() {
        JobScore scored = service.score(
                job("Java Developer", "Acme", "Bengaluru, Karnataka", "Spring Boot", NOW),
                seniorJavaCandidate(), NOW);

        assertEquals(1.0, component(scored, "location").value(), 0.01);
    }

    @Test
    @DisplayName("a remote preference matches a remote posting in another city")
    void matchesRemote() {
        CandidateProfile candidate = profile(List.of("Java"), 10, List.of("Remote"),
                List.of(), List.of());
        JobScore scored = service.score(
                job("Java Developer", "Acme", "Berlin", "This is a fully remote position", NOW),
                candidate, NOW);

        assertEquals(1.0, component(scored, "location").value(), 0.01);
    }

    @Test
    @DisplayName("a JavaScript posting is not a Java skill match")
    void javaScriptIsNotJava() {
        JobScore scored = service.score(
                job("Senior JavaScript Developer", "Acme", "Bangalore",
                        "React and JavaScript, 8+ years of experience", NOW),
                seniorJavaCandidate(), NOW);

        assertTrue(scored.matchedSkills().isEmpty(),
                "matched: " + scored.matchedSkills());
        assertEquals(List.of("Java", "Spring Boot"), scored.missingSkills());
    }

    @Test
    @DisplayName("a dimension with nothing to judge drops out instead of scoring zero")
    void inapplicableDimensionsAreExcluded() {
        // The candidate names no preferred companies, so that dimension cannot
        // be judged. Awarding it 0 would cap every job at 95.
        JobScore scored = service.score(
                job("Senior Java Developer", "Acme", "Bangalore",
                        "Spring Boot backend, 8+ years of experience", NOW),
                seniorJavaCandidate(), NOW);

        ScoreComponent preferredCompany = component(scored, "preferredCompany");
        assertFalse(preferredCompany.applicable());
        assertEquals(0.0, preferredCompany.points(), 0.01);
        assertEquals(100.0, scored.score(), 0.01);
    }

    @Test
    @DisplayName("a missing posting date drops recency rather than scoring it zero")
    void missingPostedDateIsNotApplicable() {
        JobScore scored = service.score(
                job("Senior Java Developer", "Acme", "Bangalore",
                        "Spring Boot backend, 8+ years of experience", null),
                seniorJavaCandidate(), NOW);

        assertFalse(component(scored, "recency").applicable());
        assertEquals(100.0, scored.score(), 0.01);
    }

    @Test
    @DisplayName("a preferred employer earns the company bonus")
    void preferredCompanyBonus() {
        CandidateProfile candidate = profile(List.of("Java"), 10, List.of("Bangalore"),
                List.of("Razorpay"), List.of());

        JobScore atPreferred = service.score(
                job("Java Developer", "Razorpay", "Bangalore", "8+ years of experience", NOW),
                candidate, NOW);
        JobScore elsewhere = service.score(
                job("Java Developer", "Acme Corp", "Bangalore", "8+ years of experience", NOW),
                candidate, NOW);

        assertEquals(1.0, component(atPreferred, "preferredCompany").value(), 0.01);
        assertEquals(0.0, component(elsewhere, "preferredCompany").value(), 0.01);
        assertTrue(atPreferred.score() > elsewhere.score());
    }

    @Test
    @DisplayName("older postings score lower on recency")
    void recencyDecays() {
        CandidateProfile candidate = seniorJavaCandidate();
        String title = "Senior Java Developer";
        String body = "Spring Boot, 8+ years of experience";

        JobScore fresh = service.score(
                job(title, "Acme", "Bangalore", body, NOW.minus(2, ChronoUnit.DAYS)),
                candidate, NOW);
        JobScore stale = service.score(
                job(title, "Acme", "Bangalore", body, NOW.minus(90, ChronoUnit.DAYS)),
                candidate, NOW);

        assertTrue(component(fresh, "recency").value() > component(stale, "recency").value());
        assertTrue(fresh.score() > stale.score());
    }

    @Test
    @DisplayName("the breakdown reports matched and missing skills honestly")
    void reportsSkillEvidence() {
        JobScore scored = service.score(
                job("Java Developer", "Acme", "Bangalore",
                        "Java and Kafka, 8+ years of experience", NOW),
                seniorJavaCandidate(), NOW);

        assertEquals(List.of("Java"), scored.matchedSkills());
        assertEquals(List.of("Spring Boot"), scored.missingSkills());
    }

    @Test
    @DisplayName("a country-only location has nothing to judge, so it drops out")
    void countryOnlyLocationIsNotApplicable() {
        // Jooble cannot geocode Indian cities and reports "India" for every
        // result. Scoring that zero buried every Jooble listing by 20 points;
        // half credit was still a verdict, and in practice ALL Jooble rows got
        // 0.5 and ALL Adzuna rows 1.0, turning the dimension into a source flag.
        JobScore scored = service.score(
                job("Java Developer", "Mastercard", "India", "8+ years of experience", NOW),
                seniorJavaCandidate(), NOW);

        assertFalse(component(scored, "location").applicable(),
                "there is no city to judge, so the weight must leave the divisor");
        assertEquals(0.0, component(scored, "location").points(), 0.01);
    }

    @Test
    @DisplayName("an unlocatable but well-matched job beats a local but poorly-matched one")
    void meritOutranksAnUnknownLocation() {
        // The point of dropping the dimension rather than half-crediting it. A
        // confirmed Bangalore job that also fits well still wins - that is
        // correct. What must NOT happen is a flat location bonus deciding the
        // ranking on its own, which is what a blanket 0.5-vs-1.0 split did: all
        // 24 Jooble rows scored 0.5 and all 62 Adzuna rows 1.0, so 19 of the top
        // 20 came from one source while the pool was 72/28.
        JobScore unlocatableButStrong = service.score(
                job("Senior Java Developer", "Acme", "India",
                        "Spring Boot backend, 8+ years of experience", NOW),
                seniorJavaCandidate(), NOW);
        JobScore localButWeak = service.score(
                job("PHP Developer", "Acme", "Bangalore",
                        "Drupal work, 1-2 years of experience", NOW),
                seniorJavaCandidate(), NOW);

        assertTrue(unlocatableButStrong.score() > localButWeak.score(),
                "unlocatable-but-strong %.1f should beat local-but-weak %.1f"
                        .formatted(unlocatableButStrong.score(), localButWeak.score()));
    }

    @Test
    @DisplayName("a country-only location still beats a named wrong city")
    void countryOnlyBeatsWrongCity() {
        JobScore country = service.score(
                job("Java Developer", "Acme", "India", "8+ years of experience", NOW),
                seniorJavaCandidate(), NOW);
        JobScore wrongCity = service.score(
                job("Java Developer", "Acme", "Warsaw", "8+ years of experience", NOW),
                seniorJavaCandidate(), NOW);

        assertTrue(country.score() > wrongCity.score(),
                "unknown %.1f should beat known-wrong %.1f"
                        .formatted(country.score(), wrongCity.score()));
    }

    @Test
    @DisplayName("a named non-preferred city still scores zero on location")
    void namedWrongCityScoresZero() {
        // The partial credit above must not leak into postings that name a real
        // city the candidate did not ask for.
        JobScore scored = service.score(
                job("Java Developer", "Acme", "Hyderabad, India", "8+ years of experience", NOW),
                seniorJavaCandidate(), NOW);

        assertEquals(0.0, component(scored, "location").value(), 0.01);
    }

    @Test
    @DisplayName("an exact country preference still scores a full match")
    void countryPreferenceIsFullMatch() {
        CandidateProfile candidate = profile(List.of("Java"), 10, List.of("India"),
                List.of(), List.of());
        JobScore scored = service.score(
                job("Java Developer", "Acme", "India", "8+ years of experience", NOW),
                candidate, NOW);

        assertEquals(1.0, component(scored, "location").value(), 0.01);
    }

    // --- truncated postings ------------------------------------------------

    /** Four skills, of which only Java appears in the text under test. */
    private CandidateProfile fourSkillCandidate() {
        return profile(List.of("Java", "Kafka", "Docker", "PostgreSQL"), 10,
                List.of("Bangalore"), List.of(), List.of());
    }

    @Test
    @DisplayName("a skill missing from a truncated posting counts as unknown, not absent")
    void truncatedMissesAreDiscounted() {
        // Same text twice; only the truncation marker differs. Both mention Java
        // and none of the other three skills.
        String complete = "We need a Java developer. 8+ years of experience.";
        String truncated = "We need a Java developer. 8+ years of experience and…";

        double completeValue = component(service.score(
                job("Developer", "Acme", "Bangalore", complete, NOW),
                fourSkillCandidate(), NOW), "skills").value();
        double truncatedValue = component(service.score(
                job("Developer", "Acme", "Bangalore", truncated, NOW),
                fourSkillCandidate(), NOW), "skills").value();

        // Full text: 1 of 4 = 0.25. Truncated at the 0.5 default the three
        // unmatched misses count as 1.5, so 1 / (1 + 1.5) = 0.4.
        assertEquals(0.25, completeValue, 0.01);
        assertEquals(0.40, truncatedValue, 0.01);
        assertTrue(truncatedValue > completeValue,
                "a truncated posting must not be punished for text it never showed");
    }

    @Test
    @DisplayName("truncation never invents a match: zero skills still scores zero")
    void truncationDoesNotRescueAZeroMatch() {
        JobScore scored = service.score(
                job("PHP Developer", "Acme", "Bangalore",
                        "We need a PHP developer with 10 years of experience and…", NOW),
                fourSkillCandidate(), NOW);

        assertEquals(0.0, component(scored, "skills").value(), 0.01);
    }

    @Test
    @DisplayName("setting truncated-miss-weight to 1.0 restores the old ratio")
    void weightOfOneRestoresPreviousBehaviour() {
        JobScore scored = serviceWith(1.0).score(
                job("Developer", "Acme", "Bangalore",
                        "We need a Java developer. 8+ years of experience and…", NOW),
                fourSkillCandidate(), NOW);

        assertEquals(0.25, component(scored, "skills").value(), 0.01);
    }

    @Test
    @DisplayName("a truncated posting says so in its breakdown")
    void truncationIsExplainedInTheBreakdown() {
        JobScore scored = service.score(
                job("Developer", "Acme", "Bangalore",
                        "We need a Java developer. 8+ years of experience and…", NOW),
                fourSkillCandidate(), NOW);

        assertTrue(component(scored, "skills").detail().contains("truncated"),
                "the breakdown must explain why the misses were discounted");
    }

    @Test
    @DisplayName("a truncated posting matching every skill is not pushed past a full match")
    void truncationCannotExceedAFullMatch() {
        CandidateProfile candidate = profile(List.of("Java"), 10, List.of("Bangalore"),
                List.of(), List.of());
        JobScore scored = service.score(
                job("Developer", "Acme", "Bangalore",
                        "We need a Java developer. 8+ years of experience and…", NOW),
                candidate, NOW);

        assertEquals(1.0, component(scored, "skills").value(), 0.01);
    }
}
