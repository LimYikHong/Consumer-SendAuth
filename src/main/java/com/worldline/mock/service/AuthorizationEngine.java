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
 *   2. If amount_cents >= 50000 (i.e. >= $500) → 80-90% APPROVED, rest DECLINED
 */
@Service
@Slf4j
public class AuthorizationEngine {

    /**
     * Threshold in cents: 50000 cents = $500.00
     */
    private static final long THRESHOLD_CENTS = 50000L;

    /**
     * Approval rate for high-amount transactions: 80% to 90% (randomized)
     */
    private static final double MIN_APPROVAL_RATE = 0.80;
    private static final double MAX_APPROVAL_RATE = 0.90;

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

        // Rule 2: high amount — 80%-90% approval rate
        double approvalRate = MIN_APPROVAL_RATE + ThreadLocalRandom.current().nextDouble() * (MAX_APPROVAL_RATE - MIN_APPROVAL_RATE);
        boolean approve = ThreadLocalRandom.current().nextDouble() < approvalRate;
        if (approve) {
            return new Decision(AuthorizationResult.APPROVED,
                    "APPROVED");
        } else {
            return new Decision(AuthorizationResult.DECLINED,
                    "DECLINED - insufficient amount");
        }
    }

    private String formatCents(long cents) {
        return String.format("%.2f", cents / 100.0);
    }
}
