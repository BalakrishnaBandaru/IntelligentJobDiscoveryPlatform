package com.jobdiscovery.scoring;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Turns messy source text into something matchable.
 *
 * <p>Source listings are inconsistent — Adzuna descriptions can carry HTML,
 * titles arrive quoted, and companies come through as {@code "Acme Corp"} one
 * day and {@code "ACME"} the next. Every comparison in the rule engine goes
 * through here first so both sides are normalised the same way.
 *
 * <p>Matching is <b>token-based, not substring-based</b>. A naive
 * {@code description.contains("java")} matches "JavaScript" and scores a
 * front-end role as a Java match; comparing token sequences cannot.
 */
public final class TextNormalizer {

    private static final Pattern HTML_TAG = Pattern.compile("<[^>]*>");
    private static final Pattern HTML_ENTITY = Pattern.compile("&(?:[a-zA-Z]+|#\\d+);");

    /**
     * Everything that is not a letter, digit, {@code +} or {@code #} becomes a
     * separator. {@code +} and {@code #} survive so "C++" and "C#" stay
     * distinguishable from "C".
     */
    private static final Pattern NON_TOKEN = Pattern.compile("[^a-z0-9+#]+");

    /**
     * Trailing whitespace and HTML entities, stripped before looking for a
     * truncation marker. Jooble ends its snippets {@code "...&nbsp;"}, so the
     * marker is not the last thing in the string.
     */
    private static final Pattern TRAILING_FILLER =
            Pattern.compile("(?:\\s|&(?:[a-zA-Z]+|#\\d+);)+$");

    private TextNormalizer() {
        // Static utility.
    }

    /**
     * True when the source cut this text short rather than returning all of it.
     *
     * <p>Both live sources return a <i>preview</i>, not the posting: Adzuna caps
     * its description at 500 characters and ends it with "…", and Jooble's field
     * is literally named {@code snippet} and ends "...&nbsp;". Every one of the
     * 57 stored rows is truncated.
     *
     * <p>This matters because it changes what a <i>missing</i> skill means. In
     * full text, a skill that never appears is good evidence the job does not
     * want it. In a 500-character preview it is mostly evidence that the text
     * ran out — the same distinction the location dimension already draws
     * between "not a preferred city" and "country-level only, so unknown".
     * {@link JobScoringService} discounts unmatched skills accordingly.
     */
    public static boolean isTruncated(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        String withoutFiller = TRAILING_FILLER.matcher(raw).replaceAll("");
        return withoutFiller.endsWith("…") || withoutFiller.endsWith("...");
    }

    /** Lower-cased, HTML-stripped, punctuation-collapsed text. Null-safe. */
    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String text = raw.toLowerCase();
        text = HTML_TAG.matcher(text).replaceAll(" ");
        text = HTML_ENTITY.matcher(text).replaceAll(" ");
        text = NON_TOKEN.matcher(text).replaceAll(" ");
        return text.trim();
    }

    /** {@link #normalize} split into tokens. Empty list for null/blank input. */
    public static List<String> tokenize(String raw) {
        String normalized = normalize(raw);
        if (normalized.isEmpty()) {
            return List.of();
        }
        return Arrays.asList(normalized.split(" "));
    }

    /** Tokenises several fields into one haystack — e.g. title + description. */
    public static List<String> tokenizeAll(String... raws) {
        List<String> tokens = new ArrayList<>();
        for (String raw : raws) {
            tokens.addAll(tokenize(raw));
        }
        return tokens;
    }

    /**
     * True when {@code haystack} contains {@code phrase}'s tokens consecutively.
     *
     * <p>A phrase match, so "spring boot" matches "… uses Spring Boot 3 …" but
     * not a document that merely mentions "spring" and "boot" far apart.
     */
    public static boolean containsPhrase(List<String> haystack, List<String> phrase) {
        if (phrase.isEmpty() || haystack.size() < phrase.size()) {
            return false;
        }
        int lastStart = haystack.size() - phrase.size();
        for (int start = 0; start <= lastStart; start++) {
            boolean allMatch = true;
            for (int offset = 0; offset < phrase.size(); offset++) {
                if (!tokensMatch(haystack.get(start + offset), phrase.get(offset))) {
                    allMatch = false;
                    break;
                }
            }
            if (allMatch) {
                return true;
            }
        }
        return false;
    }

    /**
     * True when {@code haystack} contains {@code term}, where a term may list
     * alternatives separated by {@code /} or {@code ,} — a profile skill of
     * "JPA/Hibernate" matches a posting that names either one.
     */
    public static boolean contains(List<String> haystack, String term) {
        if (term == null || term.isBlank()) {
            return false;
        }
        // Split before normalising: normalisation turns "/" into a space, which
        // would otherwise demand "jpa hibernate" appear as one adjacent phrase.
        for (String alternative : term.split("[/,]")) {
            List<String> phrase = tokenize(alternative);
            if (!phrase.isEmpty() && containsPhrase(haystack, phrase)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Token equality, tolerant of a plural {@code s} so "REST APIs" in a profile
     * matches "REST API" in a posting.
     */
    private static boolean tokensMatch(String a, String b) {
        return a.equals(b) || singular(a).equals(singular(b));
    }

    /**
     * Strips one trailing {@code s}. Guarded on length so three-letter acronyms
     * ("aws", "sqs") keep their shape, and on "ss" so "css" is left alone.
     */
    private static String singular(String token) {
        if (token.length() > 3 && token.endsWith("s") && !token.endsWith("ss")) {
            return token.substring(0, token.length() - 1);
        }
        return token;
    }
}
