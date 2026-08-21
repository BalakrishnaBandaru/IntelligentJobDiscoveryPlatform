package com.jobdiscovery.notify;

import com.jobdiscovery.scoring.JobScore;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Turns ranked matches into one Telegram message.
 *
 * <p>Two constraints shape this. Telegram's HTML parse mode rejects a message
 * whose entities do not parse, so every piece of job text — titles come through
 * with ampersands and angle brackets, and descriptions can contain raw HTML —
 * must be escaped. And a message is capped at 4096 characters, which is a hard
 * API limit rather than a style preference: exceed it and the send fails
 * outright, so the digest truncates itself and says how many it left out.
 */
@Component
public class DigestFormatter {

    /**
     * Telegram's documented limit. The digest is built to a lower ceiling so a
     * long title can never push a finished message over.
     */
    static final int TELEGRAM_MAX_CHARS = 4096;

    private static final int SAFETY_MARGIN = 200;

    /** Only these three characters need escaping in Telegram's HTML mode. */
    static String escape(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    /**
     * Builds the digest body.
     *
     * @param matches already filtered and ranked; the caller decides what
     *                qualifies
     */
    public String format(List<JobScore> matches) {
        if (matches.isEmpty()) {
            // Should not be sent at all, but a formatter that can produce an
            // empty message is a formatter that will eventually send one.
            return "No new matches above the threshold.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("<b>").append(matches.size())
                .append(matches.size() == 1 ? " new match" : " new matches")
                .append("</b>\n");

        int included = 0;
        for (JobScore match : matches) {
            String entry = formatOne(match);
            if (sb.length() + entry.length() > TELEGRAM_MAX_CHARS - SAFETY_MARGIN) {
                break;
            }
            sb.append(entry);
            included++;
        }

        int omitted = matches.size() - included;
        if (omitted > 0) {
            sb.append("\n<i>+").append(omitted)
                    .append(" more not shown — see /api/matches</i>");
        }
        return sb.toString();
    }

    private String formatOne(JobScore match) {
        StringBuilder sb = new StringBuilder("\n");

        sb.append("<b>").append(String.format("%.1f", match.score())).append("</b> · ");
        // The title is the link, so the message stays scannable rather than
        // carrying a wall of raw URLs.
        if (match.applyUrl() != null && !match.applyUrl().isBlank()) {
            sb.append("<a href=\"").append(escape(match.applyUrl())).append("\">")
                    .append(escape(match.title())).append("</a>");
        } else {
            sb.append(escape(match.title()));
        }
        sb.append('\n');

        String company = match.company() == null || match.company().isBlank()
                ? "unknown company" : match.company();
        String location = match.location() == null || match.location().isBlank()
                ? "location not stated" : match.location();
        sb.append(escape(company)).append(" · ").append(escape(location)).append('\n');

        if (match.explanation() != null && !match.explanation().isBlank()) {
            sb.append("<i>").append(escape(match.explanation())).append("</i>\n");
        } else if (!match.matchedSkills().isEmpty()) {
            sb.append("<i>Matches ").append(escape(String.join(", ", match.matchedSkills())))
                    .append("</i>\n");
        }
        return sb.toString();
    }
}
