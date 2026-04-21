package com.worldline.mock.service;

import com.worldline.mock.entity.AuthorizationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Rule-based mock authorization engine. Amount is in CENTS (e.g. 50000 =
 * $500.00).
 *
 * Rules: 1. If amount_cents < 50000 (i.e. < $500) → APPROVED
 *   2. If amount_cents >= 50000 (i.e. >= $500) → RANDOM (50/50 APPROVED or
 * DECLINED)
 */
@Service
@Slf4j
public class AuthorizationEngine {

    /**
     * Threshold in cents: 50000 cents = $500.00
     */
    private static final long THRESHOLD_CENTS = 50000L;

    public record Decision(AuthorizationResult result, String reason) {

    }

    /**
     * Evaluate a single transaction and return a decision.
     *
     * @param amountCents transaction amount in cents
     */
    public Decision evaluate(long amountCents) {

        // Rule 1: low amount — auto-approve
        if (amountCents < THRESHOLD_CENTS) {
            return new Decision(AuthorizationResult.APPROVED,
                    "Amount " + formatCents(amountCents) + " is below threshold " + formatCents(THRESHOLD_CENTS));
        }

        // Rule 2: high amount — random decision (50/50)
        boolean approve = ThreadLocalRandom.current().nextBoolean();
        if (approve) {
            return new Decision(AuthorizationResult.APPROVED,
                    "Amount " + formatCents(amountCents) + " >= " + formatCents(THRESHOLD_CENTS) + " — randomly APPROVED");
        } else {
            return new Decision(AuthorizationResult.DECLINED,
                    "Amount " + formatCents(amountCents) + " >= " + formatCents(THRESHOLD_CENTS) + " — randomly DECLINED");
        }
    }

    private String formatCents(long cents) {
        return String.format("%.2f", cents / 100.0);
    }
}
