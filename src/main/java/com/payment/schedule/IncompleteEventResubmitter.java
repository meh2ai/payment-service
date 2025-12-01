package com.payment.schedule;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@EnableScheduling
@RequiredArgsConstructor
@Slf4j
public class IncompleteEventResubmitter {

    private final IncompleteEventPublications incompleteEvents;

    @Scheduled(fixedDelayString = "${app.events.resubmit.interval:60000}")
    public void resubmitIncompleteEvents() {
        log.info("Checking for incomplete event publications");
        incompleteEvents.resubmitIncompletePublicationsOlderThan(Duration.ofMinutes(1));
    }
}
