package com.jobdiscovery.explain;

import com.jobdiscovery.scoring.JobScore;
import com.jobdiscovery.scoring.ScoreComponent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Writes an explanation from the same evidence an LLM would get, using rules
 * only. No network, no key, no cost.
 *
 * <p>This exists because the ranking does not depend on a model being
 * reachable. Without it, an Ollama container that is not running — or an
 * Anthropic balance that has run out — turns a perfectly good shortlist into a
 * 502. With it, the pipeline degrades to prose that is plainer but never wrong.
 *
 * <p>It reads like a template, because it is one. It cannot notice that a
 * posting is a stretch role worth taking anyway, which is the sort of thing the
 * LLM tier is actually for. What it can do is state the evidence accurately and
 * never invent anything, and the response says which tier produced it.
 */
@Component
public class TemplatedExplainer {

    /** Why a dimension being the strongest is worth mentioning first. */
    private static final Map<String, String> STRENGTHS = Map.of(
            "skills", "the skills line up",
            "seniority", "the seniority fits",
            "location", "the location works",
            "keywords", "it matches the kind of role you are looking for",
            "preferredCompany", "it is an employer you named",
            "recency", "it went up recently");

    public String explain(JobScore match) {
        List<ScoreComponent> applicable = new ArrayList<>();
        for (ScoreComponent component : match.components()) {
            if (component.applicable() && component.weight() > 0) {
                applicable.add(component);
            }
        }

        List<String> sentences = new ArrayList<>();
        sentences.add(verdict(match, applicable));
        sentences.add(skills(match));
        String caveat = caveat(applicable);
        if (caveat != null) {
            sentences.add(caveat);
        }
        return String.join(" ", sentences);
    }

    /** Overall band, plus whichever dimension carried it. */
    private String verdict(JobScore match, List<ScoreComponent> applicable) {
        String band;
        if (match.score() >= 70) {
            band = "A strong match";
        } else if (match.score() >= 55) {
            band = "A decent match";
        } else if (match.score() >= 40) {
            band = "A partial match";
        } else {
            band = "A weak match";
        }

        String formatted = "%s at %.1f out of 100".formatted(band, match.score());
        if (applicable.isEmpty()) {
            return formatted + ".";
        }
        ScoreComponent strongest = applicable.stream()
                .max(Comparator.comparingDouble(ScoreComponent::value))
                .orElseThrow();
        String reason = STRENGTHS.get(strongest.name());
        return reason == null ? formatted + "." : formatted + ", mainly because " + reason + ".";
    }

    /**
     * What the posting named and what it did not — and, when the source
     * truncated the text, the fact that "did not" is not the same as "does not
     * want". Saying otherwise would be a straightforwardly false claim, since
     * every stored row is a preview.
     */
    private String skills(JobScore match) {
        List<String> matched = match.matchedSkills();
        List<String> missing = match.missingSkills();

        if (matched == null || matched.isEmpty()) {
            return "None of your listed skills appear in the text the job board returned.";
        }

        StringBuilder sb = new StringBuilder("It names ")
                .append(String.join(", ", matched))
                .append(" from your profile");

        if (missing != null && !missing.isEmpty()) {
            sb.append(", and does not mention ").append(summarise(missing));
            if (truncated(match)) {
                sb.append(" — though the posting text is cut short, so those may still be wanted");
            }
        }
        return sb.append('.').toString();
    }

    /**
     * The weakest dimension, but only when it is actually weak — and never
     * {@code skills}, which the previous sentence has already covered in more
     * detail than a caveat would. Without that exclusion a skills-limited match
     * ends up stating the same truncation point twice in a row.
     */
    private String caveat(List<ScoreComponent> applicable) {
        ScoreComponent weakest = applicable.stream()
                .filter(component -> !"skills".equals(component.name()))
                .min(Comparator.comparingDouble(ScoreComponent::value))
                .orElse(null);
        if (weakest == null || weakest.value() >= 0.6) {
            return null;
        }
        String detail = weakest.detail();
        if (detail == null || detail.isBlank()) {
            return null;
        }
        return "The main caveat is " + Character.toLowerCase(detail.charAt(0))
                + detail.substring(1) + ".";
    }

    /** Lists at most three, then counts the rest. */
    private String summarise(List<String> values) {
        if (values.size() <= 3) {
            return String.join(", ", values);
        }
        return String.join(", ", values.subList(0, 3))
                + " and " + (values.size() - 3) + " other"
                + (values.size() - 3 == 1 ? "" : "s");
    }

    /** Whether the skills dimension reported that the source cut the text off. */
    private boolean truncated(JobScore match) {
        for (ScoreComponent component : match.components()) {
            if ("skills".equals(component.name()) && component.detail() != null
                    && component.detail().contains("truncated")) {
                return true;
            }
        }
        return false;
    }
}
