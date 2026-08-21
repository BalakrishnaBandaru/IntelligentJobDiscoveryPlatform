package com.jobdiscovery.explain;

import com.jobdiscovery.scoring.JobScore;
import com.jobdiscovery.scoring.ScoreComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Puts a scored match into words, across three tiers: the configured LLM
 * provider, then {@link TemplatedExplainer}, then a clear error.
 *
 * <p><b>No tier produces or adjusts the number.</b> Each is handed the score and
 * the evidence the rule engine already derived — matched and missing skills, the
 * seniority read, the stated experience range, the per-dimension breakdown — and
 * asked only to phrase it. The model is never shown the raw posting, which is
 * precisely why it cannot form its own opinion of the fit. That split is what
 * keeps the ranking auditable: the number comes from code you can unit-test, and
 * the sentence comes from the number.
 *
 * <p>Because of that split, the LLM tier is doing genuinely easy work, which is
 * why a 3-billion-parameter model on a laptop is a reasonable provider and why
 * falling back to a template degrades the prose rather than the correctness.
 *
 * <p>LLM explanations are cached in memory, keyed by job and score. Scoring is
 * recomputed on every request, so without this a second call to the same
 * endpoint would pay for the same sentences again. Re-tuning a weight changes
 * the score, which changes the key, which expires the entry — no invalidation
 * logic to get wrong. Templated output is not cached; it is cheap enough that
 * caching it would only add a way to be stale.
 */
@Service
public class MatchExplainer {

    private static final Logger log = LoggerFactory.getLogger(MatchExplainer.class);

    /** Marks an explanation the template wrote, so the tier is never ambiguous. */
    public static final String SOURCE_TEMPLATED = "templated";

    /**
     * Written for a small local model, because that is the weakest thing that
     * will run it — and a prompt that survives llama3.2:3b is not worse for a
     * frontier model. Three details each earn their place by having been got
     * wrong first:
     *
     * <ul>
     *   <li>The second-person rule is phrased as a prohibition. "Address the
     *       candidate as you" produced "This candidate…" every single time.</li>
     *   <li>The example is a deliberately unrelated job. An apposite one was
     *       lifted into the answer almost word for word.</li>
     *   <li>Numbers are banned outright, and the prompt withholds the score,
     *       because the model kept reciting it back.</li>
     * </ul>
     */
    private static final String SYSTEM_PROMPT = """
            You write a short note telling a job-seeker why a job was ranked \
            where it was.

            FORMAT — follow exactly:
            - Write TO the person, using "you" and "your". Never write "the \
              candidate", "this candidate", or "the job-seeker".
            - Never begin the note with the word "You". Start with the job, the \
              skill, or the reason instead.
            - Exactly two or three sentences of plain prose. No bullet points, \
              no headings, no preamble, no sign-off.
            - Never mention a numeric score. It is already shown beside the job.

            CONTENT:
            - Each dimension is already rated STRONG, MODERATE or WEAK for you. \
              Report those ratings. Never re-judge them or invent your own.
            - Use only the facts given. You are NOT shown the job posting, so \
              never guess at duties, salary or culture.
            - A skill listed as not evidenced means the job board truncated the \
              text. Say it is "not mentioned". Never say the job does not \
              require it.
            - Lead with the strongest dimension, then the most useful caveat. \
              Ignore any dimension marked "does not apply".

            The example shows the VOICE only. Its details are unrelated to the \
            job you will be given — never copy them.
            Example: "This one leans on your Terraform background, which the \
            title calls out directly. It's pitched a level below where you are \
            now, and the office is in a city you didn't list, so it ranks lower \
            than the skills alone would suggest."
            """;

    private final List<LlmClient> clients;
    private final TemplatedExplainer templated;
    private final ExplanationProperties properties;
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public MatchExplainer(List<LlmClient> clients, TemplatedExplainer templated,
                          ExplanationProperties properties) {
        this.clients = clients;
        this.templated = templated;
        this.properties = properties;
    }

    /** The selected provider, or empty when none is configured. */
    private LlmClient activeClient() {
        for (LlmClient client : clients) {
            if (client.isConfigured()) {
                return client;
            }
        }
        return null;
    }

    /** True when some tier can answer — which, with the template on, is always. */
    public boolean isAvailable() {
        return activeClient() != null
                || properties.fallback() == ExplanationProperties.Fallback.TEMPLATED;
    }

    /**
     * Returns the matches with explanations attached to the top ones.
     *
     * <p>Only the first {@code explanation.max-matches} get one — for a paid
     * provider each is a separate call, and explaining a 30-point match nobody
     * will read is money spent for nothing. The rest come back untouched.
     *
     * @throws ExplanationException when no tier can answer, i.e. the provider is
     *                              unavailable and the fallback is set to
     *                              {@code error}
     */
    public List<JobScore> explainAll(List<JobScore> matches) {
        LlmClient client = activeClient();
        boolean canTemplate = properties.fallback() == ExplanationProperties.Fallback.TEMPLATED;

        if (client == null && !canTemplate) {
            throw new ExplanationException(ExplanationException.NOT_CONFIGURED,
                    "No explanation provider is configured and the templated fallback is off. "
                    + "Set EXPLANATION_PROVIDER to 'ollama' or 'claude', or set "
                    + "EXPLANATION_FALLBACK=templated.");
        }

        List<JobScore> result = new ArrayList<>(matches.size());
        int explained = 0;
        for (JobScore match : matches) {
            if (explained >= properties.maxMatches()) {
                result.add(match);
                continue;
            }
            result.add(explainOne(match, client, canTemplate));
            explained++;
        }
        return result;
    }

    /** One match, trying the LLM first and degrading to the template. */
    private JobScore explainOne(JobScore match, LlmClient client, boolean canTemplate) {
        if (client != null) {
            try {
                return match.withExplanation(fromLlm(match, client), client.name());
            } catch (ExplanationException e) {
                if (!canTemplate) {
                    throw e;
                }
                // A missing model or a stopped container is an ordinary state
                // here, not an incident — the shortlist is unaffected either way.
                log.warn("{} could not explain job {} ({}); falling back to the template",
                        client.name(), match.jobId(), e.getMessage());
            }
        }
        return match.withExplanation(templated.explain(match), SOURCE_TEMPLATED);
    }

    /** An LLM explanation, from cache when the job last scored the same. */
    private String fromLlm(JobScore match, LlmClient client) {
        String key = client.name() + ":" + match.jobId() + "@" + match.score();
        return cache.computeIfAbsent(key, ignored -> {
            log.debug("Requesting an explanation for job {} (score {}) from {}",
                    match.jobId(), match.score(), client.name());
            return client.complete(SYSTEM_PROMPT, buildPrompt(match));
        });
    }

    /**
     * Renders the evidence as text. Everything here comes off {@link JobScore};
     * the posting itself is deliberately absent.
     */
    String buildPrompt(JobScore match) {
        StringBuilder sb = new StringBuilder();
        sb.append("Job: ").append(match.title()).append('\n');
        sb.append("Company: ").append(nullSafe(match.company())).append('\n');
        sb.append("Location: ").append(nullSafe(match.location())).append('\n');
        // The score is deliberately absent. A small model that is shown a
        // number restates it, and it is already displayed beside the job.
        sb.append("Level read from the title: ").append(match.jobSeniority()).append('\n');

        if (match.requiredYears() != null) {
            sb.append("Experience the posting asks for: ")
                    .append(match.requiredYears().describe()).append('\n');
        } else {
            sb.append("Experience the posting asks for: not stated\n");
        }

        sb.append("Your skills this posting names: ")
                .append(joinOrNone(match.matchedSkills())).append('\n');
        sb.append("Your skills not evidenced in the text: ")
                .append(joinOrNone(match.missingSkills())).append('\n');
        sb.append("Your keywords it names: ")
                .append(joinOrNone(match.matchedKeywords())).append('\n');

        // Ratings, not arithmetic. Shown "21.2 of 25 points" a small model
        // reasons about the numbers and gets it backwards - llama3.2:3b called
        // a 0.85 seniority score the thing "pulling the match down". Pre-judging
        // each dimension removes arithmetic it cannot do reliably, and costs
        // nothing: the engine already knows how every dimension went.
        sb.append("\nHow it rates on each dimension:\n");
        for (ScoreComponent component : match.components()) {
            sb.append("- ").append(component.name()).append(": ");
            if (component.applicable()) {
                sb.append(rate(component.value()))
                        .append(" (").append(component.detail()).append(')');
            } else {
                sb.append("does not apply (").append(component.detail()).append(')');
            }
            sb.append('\n');
        }

        sb.append("\nWrite the note.");
        return sb.toString();
    }

    /** Turns a 0-1 dimension score into the word the prompt asks it to report. */
    private static String rate(double value) {
        if (value >= 0.8) {
            return "STRONG";
        }
        return value >= 0.5 ? "MODERATE" : "WEAK";
    }

    /** Visible for tests: how many LLM explanations are currently memoised. */
    int cacheSize() {
        return cache.size();
    }

    private static String joinOrNone(List<String> values) {
        return values == null || values.isEmpty() ? "none" : String.join(", ", values);
    }

    private static String nullSafe(String value) {
        return value == null || value.isBlank() ? "not stated" : value;
    }
}
