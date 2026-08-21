package com.jobdiscovery.scoring;

import com.jobdiscovery.job.JobListing;
import com.jobdiscovery.job.JobListingRepository;
import com.jobdiscovery.profile.CandidateProfile;
import com.jobdiscovery.profile.CandidateProfileService;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The deterministic rule engine: scores every stored listing against the
 * candidate profile and ranks them.
 *
 * <p><b>No LLM is involved here, by design.</b> The number is produced by code
 * that can be unit-tested, re-run identically tomorrow, and explained line by
 * line. The Phase 5 LLM layer sits downstream of this and only puts the
 * resulting {@link JobScore} into words — it never produces or adjusts the
 * number. That split is what keeps the ranking auditable.
 *
 * <p>Scoring runs in memory over the whole table on each request. That is the
 * right trade for a single-user tool holding a few hundred rows: re-tuning a
 * weight re-scores everything for free, with no stored scores to invalidate.
 */
@Service
public class JobScoringService {

    private final JobListingRepository jobRepository;
    private final CandidateProfileService profileService;
    private final ScoringProperties properties;

    /**
     * City names that mean the same place. Without these, a profile asking for
     * "Bangalore" scores a Bengaluru posting at zero — and both spellings are
     * common across the source APIs.
     */
    private static final List<Set<String>> LOCATION_ALIASES = List.of(
            Set.of("bangalore", "bengaluru"),
            Set.of("mumbai", "bombay"),
            Set.of("gurgaon", "gurugram"),
            Set.of("delhi", "new delhi", "ncr"),
            Set.of("kolkata", "calcutta"),
            Set.of("chennai", "madras"),
            Set.of("pune", "poona"),
            Set.of("hyderabad", "secunderabad"),
            Set.of("trivandrum", "thiruvananthapuram"));

    /** Ways a profile or a posting can express "not tied to an office". */
    private static final List<String> REMOTE_TERMS =
            List.of("remote", "hybrid", "work from home", "wfh", "anywhere");

    public JobScoringService(JobListingRepository jobRepository,
                             CandidateProfileService profileService,
                             ScoringProperties properties) {
        this.jobRepository = jobRepository;
        this.profileService = profileService;
        this.properties = properties;
    }

    /**
     * Scores every stored listing and returns the best matches, highest first.
     *
     * @param limit    how many to return
     * @param minScore drop anything below this score
     * @param source   optional source filter, e.g. {@code ADZUNA}
     * @throws com.jobdiscovery.profile.ProfileNotFoundException if no profile is set
     */
    @Transactional(readOnly = true)
    public List<JobScore> rank(int limit, double minScore, String source) {
        CandidateProfile profile = profileService.get();

        List<JobListing> listings = (source == null || source.isBlank())
                ? jobRepository.findAll()
                : jobRepository.findBySourceOrderByIdDesc(source.trim().toUpperCase());

        Instant now = Instant.now();
        return listings.stream()
                .map(listing -> score(listing, profile, now))
                .filter(scored -> scored.score() >= minScore)
                // Highest score first; newest first as the tie-break, so a fresh
                // posting outranks an older one that scored identically.
                .sorted(Comparator.comparingDouble(JobScore::score).reversed()
                        .thenComparing(JobScore::jobId, Comparator.reverseOrder()))
                .limit(Math.max(0, limit))
                .toList();
    }

    /**
     * Scores one listing against one profile. Pure — same inputs, same output —
     * which is what makes the engine testable without a database or a network.
     *
     * @param now reference time for the recency dimension, passed in rather than
     *            read from the clock so tests are deterministic
     */
    public JobScore score(JobListing listing, CandidateProfile profile, Instant now) {
        List<String> titleTokens = TextNormalizer.tokenize(listing.getTitle());
        List<String> textTokens = TextNormalizer.tokenizeAll(
                listing.getTitle(), listing.getDescription());

        List<String> matchedSkills = new ArrayList<>();
        List<String> missingSkills = new ArrayList<>();
        partition(profile.getSkills(), textTokens, matchedSkills, missingSkills);

        List<String> matchedKeywords = new ArrayList<>();
        partition(profile.getKeywords(), textTokens, matchedKeywords, new ArrayList<>());

        SeniorityLevel jobSeniority = SeniorityLevel.fromTitle(listing.getTitle());
        ExperienceRequirement required =
                ExperienceRequirement.parse(listing.getTitle(), listing.getDescription());

        // Both live sources return a preview rather than the posting, so an
        // unmatched skill here is usually "the text ran out", not "the job does
        // not want it". scoreSkills discounts the misses when this is true.
        boolean truncated = TextNormalizer.isTruncated(listing.getDescription());

        List<ScoreComponent> components = List.of(
                scoreSkills(matchedSkills, missingSkills, titleTokens, truncated),
                scoreSeniority(profile, jobSeniority, required),
                scoreLocation(listing, profile, textTokens),
                scoreKeywords(profile, matchedKeywords),
                scorePreferredCompany(listing, profile),
                scoreRecency(listing, now));

        return new JobScore(
                listing.getId(), listing.getTitle(), listing.getCompany(),
                listing.getLocation(), listing.getSource(), listing.getApplyUrl(),
                listing.getPostedDate(),
                total(components),
                List.copyOf(matchedSkills), List.copyOf(missingSkills),
                List.copyOf(matchedKeywords),
                jobSeniority, required, components,
                // Both filled in downstream: the explanation by Phase 5b, the
                // application status by Phase 7. Neither affects the number.
                null, null, null);
    }

    /**
     * Earned points over the weight of the dimensions that applied, as 0–100.
     *
     * <p>Dividing by the <i>applicable</i> weight rather than the total is what
     * lets a sparse profile score fairly: leaving preferred companies unset
     * removes that dimension instead of costing every job the same points.
     */
    private double total(List<ScoreComponent> components) {
        double applicableWeight = 0;
        double earned = 0;
        for (ScoreComponent component : components) {
            if (component.applicable()) {
                applicableWeight += component.weight();
                earned += component.weight() * component.value();
            }
        }
        if (applicableWeight <= 0) {
            return 0.0;
        }
        return Math.round(earned / applicableWeight * 1000.0) / 10.0;
    }

    // --- dimensions --------------------------------------------------------

    /**
     * @param truncated whether the posting text was cut short by the source, in
     *                  which case an unmatched skill is discounted — see
     *                  {@link TextNormalizer#isTruncated}
     */
    private ScoreComponent scoreSkills(List<String> matched, List<String> missing,
                                       List<String> titleTokens, boolean truncated) {
        double weight = properties.weights().skills();
        int total = matched.size() + missing.size();
        if (total == 0) {
            return ScoreComponent.notApplicable("skills", weight, "profile lists no skills");
        }

        // Dividing by the profile's whole skill list assumes the text had a fair
        // chance to mention every one of them. A 500-character preview did not,
        // so each unmatched skill counts for less when the text is truncated.
        // At the 1.0 default this is exactly the old matched/total ratio.
        double missWeight = truncated ? properties.truncatedMissWeight() : 1.0;
        double denominator = matched.size() + missing.size() * missWeight;
        double value = denominator <= 0 ? 0.0 : matched.size() / denominator;

        // A skill named in the title is a far stronger signal than one buried in
        // the description, so a title hit lifts an otherwise partial match.
        String titleSkill = null;
        for (String skill : matched) {
            if (TextNormalizer.contains(titleTokens, skill)) {
                titleSkill = skill;
                break;
            }
        }
        String detail = "matched " + matched.size() + " of " + total + " skills"
                + (matched.isEmpty() ? "" : ": " + String.join(", ", matched));
        if (titleSkill != null) {
            value += 0.15;
            detail += "; title names " + titleSkill;
        }
        if (truncated && !missing.isEmpty()) {
            detail += "; posting text is truncated, so the " + missing.size()
                    + " unmatched count as unknown rather than absent";
        }
        return ScoreComponent.of("skills", weight, value, detail);
    }

    private ScoreComponent scoreSeniority(CandidateProfile profile, SeniorityLevel jobLevel,
                                          ExperienceRequirement required) {
        double weight = properties.weights().seniority();
        Integer experience = profile.getExperienceYears();
        if (experience == null) {
            return ScoreComponent.notApplicable("seniority", weight,
                    "profile states no experience");
        }
        int years = experience;

        // An explicitly stated requirement beats anything inferred from a title.
        if (required != null) {
            if (required.isSatisfiedBy(years)) {
                return ScoreComponent.of("seniority", weight, 1.0,
                        "asks for " + required.describe() + "; candidate has " + years);
            }
            if (years < required.minYears()) {
                int shortfall = required.minYears() - years;
                return ScoreComponent.of("seniority", weight, 1.0 - 0.30 * shortfall,
                        "asks for " + required.describe() + "; candidate has " + years
                                + " — short by " + shortfall);
            }
            int excess = years - required.maxYears();
            return ScoreComponent.of("seniority", weight, 1.0 - 0.15 * excess,
                    "asks for " + required.describe() + "; candidate has " + years
                            + " — over-qualified by " + excess);
        }

        // Otherwise fall back to the level implied by the title.
        SeniorityLevel candidateLevel = SeniorityLevel.forExperienceYears(years);
        int gap = jobLevel.rank() - candidateLevel.rank();
        double value = switch (gap) {
            case 0 -> 1.00;
            // A rung above is a stretch role — still worth surfacing.
            case 1 -> 0.85;
            case 2 -> 0.60;
            // A rung below is a step down, and two below is not worth a look.
            case -1 -> 0.60;
            case -2 -> 0.30;
            default -> gap > 0 ? 0.30 : 0.0;
        };
        String direction = gap == 0 ? "matches" : (gap > 0 ? "above" : "below");
        return ScoreComponent.of("seniority", weight, value,
                "title reads " + jobLevel + ", " + direction + " the " + candidateLevel
                        + " level implied by " + years + " years");
    }

    private ScoreComponent scoreLocation(JobListing listing, CandidateProfile profile,
                                         List<String> textTokens) {
        double weight = properties.weights().location();
        List<String> preferred = profile.getPreferredLocations();
        if (preferred == null || preferred.isEmpty()) {
            return ScoreComponent.notApplicable("location", weight,
                    "profile states no preferred locations");
        }

        List<String> locationTokens = TextNormalizer.tokenize(listing.getLocation());
        if (!locationTokens.isEmpty()) {
            for (String preference : preferred) {
                for (String alias : expandAliases(preference)) {
                    if (TextNormalizer.contains(locationTokens, alias)) {
                        return ScoreComponent.of("location", weight, 1.0,
                                "'" + listing.getLocation() + "' matches preferred '"
                                        + preference + "'");
                    }
                }
            }
        }

        // Remote is a location preference the location field rarely carries — it
        // usually shows up in the title or the body instead.
        if (wantsRemote(preferred) && mentionsRemote(textTokens)) {
            return ScoreComponent.of("location", weight, 1.0,
                    "posting offers remote or hybrid work");
        }

        if (locationTokens.isEmpty()) {
            return ScoreComponent.of("location", weight, 0.3,
                    "posting states no location");
        }

        // A posting located at nothing more precise than the country tells us
        // nothing about the city, so this dimension has nothing to judge and
        // drops out — the same treatment preferred companies get when the
        // profile names none.
        //
        // It was 0.0 originally, then 0.5 (fe17ace) to stop Jooble listings
        // being buried. Half credit was still a verdict, though, and measuring
        // it showed what that verdict actually did: Jooble cannot geocode Indian
        // cities and reports "India" for every result, so ALL 24 Jooble rows
        // scored exactly 0.5 and all 62 Adzuna rows exactly 1.0. The dimension
        // had stopped measuring location and become a source flag worth a flat
        // 10 points, which put 19 of the top 20 on one source while the pool was
        // 72/28. Dropping out judges those postings on evidence that exists.
        //
        // Only fires when the location is *exactly* the country: "Hyderabad,
        // India" names a real, non-preferred city and still scores zero.
        if (locationTokens.equals(TextNormalizer.tokenize(properties.homeCountry()))) {
            return ScoreComponent.notApplicable("location", weight,
                    "'" + listing.getLocation() + "' is country-level only — no city to judge");
        }

        return ScoreComponent.of("location", weight, 0.0,
                "'" + listing.getLocation() + "' is not a preferred location");
    }

    private ScoreComponent scoreKeywords(CandidateProfile profile, List<String> matched) {
        double weight = properties.weights().keywords();
        List<String> keywords = profile.getKeywords();
        if (keywords == null || keywords.isEmpty()) {
            return ScoreComponent.notApplicable("keywords", weight,
                    "profile states no keywords");
        }
        double value = (double) matched.size() / keywords.size();
        String detail = "matched " + matched.size() + " of " + keywords.size() + " keywords"
                + (matched.isEmpty() ? "" : ": " + String.join(", ", matched));
        return ScoreComponent.of("keywords", weight, value, detail);
    }

    private ScoreComponent scorePreferredCompany(JobListing listing, CandidateProfile profile) {
        double weight = properties.weights().preferredCompany();
        List<String> preferred = profile.getPreferredCompanies();
        if (preferred == null || preferred.isEmpty()) {
            return ScoreComponent.notApplicable("preferredCompany", weight,
                    "profile states no preferred companies");
        }
        List<String> companyTokens = TextNormalizer.tokenize(listing.getCompany());
        for (String preference : preferred) {
            if (TextNormalizer.contains(companyTokens, preference)) {
                return ScoreComponent.of("preferredCompany", weight, 1.0,
                        listing.getCompany() + " is a preferred employer");
            }
        }
        return ScoreComponent.of("preferredCompany", weight, 0.0,
                "not one of the preferred employers");
    }

    private ScoreComponent scoreRecency(JobListing listing, Instant now) {
        double weight = properties.weights().recency();
        Instant posted = listing.getPostedDate();
        if (posted == null) {
            return ScoreComponent.notApplicable("recency", weight,
                    "source reported no posting date");
        }
        long days = Duration.between(posted, now).toDays();
        if (days < 0) {
            days = 0;
        }
        double value;
        if (days <= 7) {
            value = 1.0;
        } else if (days <= 14) {
            value = 0.8;
        } else if (days <= 30) {
            value = 0.6;
        } else if (days <= 60) {
            value = 0.3;
        } else {
            value = 0.0;
        }
        return ScoreComponent.of("recency", weight, value, "posted " + days + " day(s) ago");
    }

    // --- helpers -----------------------------------------------------------

    /** Splits {@code terms} by whether the posting text mentions them. */
    private void partition(List<String> terms, List<String> textTokens,
                           List<String> matched, List<String> missing) {
        if (terms == null) {
            return;
        }
        for (String term : terms) {
            if (TextNormalizer.contains(textTokens, term)) {
                matched.add(term);
            } else {
                missing.add(term);
            }
        }
    }

    /** A location plus any other spelling of the same place. */
    private List<String> expandAliases(String location) {
        String normalized = TextNormalizer.normalize(location);
        for (Set<String> group : LOCATION_ALIASES) {
            if (group.contains(normalized)) {
                return List.copyOf(group);
            }
        }
        return List.of(location);
    }

    private boolean wantsRemote(List<String> preferredLocations) {
        for (String preference : preferredLocations) {
            String normalized = TextNormalizer.normalize(preference);
            if (REMOTE_TERMS.contains(normalized)) {
                return true;
            }
        }
        return false;
    }

    private boolean mentionsRemote(List<String> textTokens) {
        for (String term : REMOTE_TERMS) {
            if (TextNormalizer.contains(textTokens, term)) {
                return true;
            }
        }
        return false;
    }
}
