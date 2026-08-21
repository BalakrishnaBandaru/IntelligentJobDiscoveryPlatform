package com.jobdiscovery.notify;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Sends a digest without ever letting that failure matter to the caller.
 *
 * <p>The fetch jobs call this. Fetching is the valuable half of the pipeline and
 * notifying is the convenience on top, so a Telegram outage, an unset token or a
 * malformed message must not turn a successful fetch into a failed one. Every
 * path here logs and returns.
 *
 * <p>It also keeps that decision in one place rather than repeated as a
 * try/catch at each call site, where one of them would eventually be written
 * without it.
 */
@Component
public class DigestNotifier {

    private static final Logger log = LoggerFactory.getLogger(DigestNotifier.class);

    private final DigestService digestService;
    private final TelegramProperties properties;

    public DigestNotifier(DigestService digestService, TelegramProperties properties) {
        this.digestService = digestService;
        this.properties = properties;
    }

    /** Sends after a fetch, if Telegram is set up at all. */
    public void sendQuietly(String context) {
        if (!digestService.isConfigured()) {
            log.debug("No digest after {}: Telegram is not configured", context);
            return;
        }
        try {
            DigestResult result = digestService.sendDigest(false);
            if (result.sent()) {
                log.info("Digest sent after {}: {} match(es)", context, result.jobCount());
            } else {
                log.info("No digest after {}: nothing new above the threshold", context);
            }
        } catch (RuntimeException e) {
            log.warn("Digest after {} failed, continuing anyway: {}", context, e.getMessage());
        }
    }

    /**
     * As {@link #sendQuietly}, but also honours
     * {@code telegram.send-on-startup} — some people want the daily cron only,
     * and would not thank a digest for every container restart.
     */
    public void sendOnStartup(String context) {
        if (!properties.sendOnStartup()) {
            log.debug("No digest after {}: telegram.send-on-startup is false", context);
            return;
        }
        sendQuietly(context);
    }
}
