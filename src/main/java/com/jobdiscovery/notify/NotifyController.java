package com.jobdiscovery.notify;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Manual trigger for the Telegram digest — the same path the scheduled and
 * startup sends take, so testing the wiring exercises the real thing rather
 * than a parallel implementation.
 */
@RestController
@Tag(name = "Notifications")
public class NotifyController {

    private final DigestService digestService;

    public NotifyController(DigestService digestService) {
        this.digestService = digestService;
    }

    /**
     * @param force re-send matches that have already been notified. Without it a
     *              second call in a row correctly reports there is nothing new,
     *              which is unhelpful when you are trying to check the bot works
     */
    @PostMapping("/api/notify")
    public DigestResult sendDigest(@RequestParam(defaultValue = "false") boolean force) {
        return digestService.sendDigest(force);
    }

    /**
     * The exact message a digest would send, without sending it and without
     * marking anything notified. Works with no bot configured.
     */
    @GetMapping(value = "/api/notify/preview", produces = "text/plain;charset=UTF-8")
    public String preview(@RequestParam(defaultValue = "false") boolean force) {
        return digestService.preview(force);
    }
}
