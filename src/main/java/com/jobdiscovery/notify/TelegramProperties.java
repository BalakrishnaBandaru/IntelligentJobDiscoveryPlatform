package com.jobdiscovery.notify;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration for the Telegram digest, bound from {@code telegram.*}.
 *
 * @param enabled       off by default, so the stack runs with no bot at all
 * @param botToken      from BotFather. Treated exactly like the API keys — it
 *                      lives only in {@code .env}
 * @param chatId        who receives the digest. Not the same as the bot token;
 *                      get it by messaging the bot and reading
 *                      {@code /getUpdates}
 * @param minScore      listings below this are not worth a notification. 60 is
 *                      roughly the top third of a typical pool — a digest that
 *                      includes weak matches trains you to ignore it
 * @param maxJobs       hard cap per digest. Telegram messages are limited to
 *                      4096 characters, and a digest nobody finishes is a
 *                      digest nobody reads
 * @param sendOnStartup send a digest after the startup fetch, not only on the
 *                      cron. The cron assumes an always-on host; this laptop is
 *                      not one, so without this a digest would rarely arrive
 */
@ConfigurationProperties(prefix = "telegram")
public record TelegramProperties(
        @DefaultValue("false") boolean enabled,
        String botToken,
        String chatId,
        @DefaultValue("https://api.telegram.org") String baseUrl,
        @DefaultValue("60") double minScore,
        @DefaultValue("8") int maxJobs,
        @DefaultValue("true") boolean sendOnStartup) {
}
