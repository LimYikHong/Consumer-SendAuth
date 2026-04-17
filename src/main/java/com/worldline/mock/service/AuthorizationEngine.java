package com.worldline.mock.service;

import com.worldline.mock.entity.AccountStatus;
import com.worldline.mock.entity.AuthorizationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Rule-based mock authorization engine.
 *
 * Rules: 1. If account status is BLOCKED → DECLINED 2. If amount < 500           → APPROVED
 *   3. If amount >= 500 → RANDOM (50/50 APPROVED or DECLINED)
 */
@Service
@Slf4j
public class AuthorizationEngine {

    private static final BigDecimal THRESHOLD = new BigDecimal("500");

    public record Decision(AuthorizationResult result, String reason) {
    }

    /**
     * Evaluate a single transaction and return a decision.
     */
    public Decision evaluate(BigDecimal amount, AccountStatus accountStatus) {

        // Rule 1: blocked account
        if (accountStatus == AccountStatus.BLOCKED) {
            return new Decision(AuthorizationResult.DECLINED, "Account status is BLOCKED");
        }

        // Rule 2: low amount — auto-approve
        if (amount.compareTo(THRESHOLD) < 0) {
            return new Decision(AuthorizationResult.APPROVED,
                    "Amount " + amount + " is below threshold " + THRESHOLD);
        }

        // Rule 3: high amount — random decision
        boolean approve = ThreadLocalRandom.current().nextBoolean();
        if (approve) {
            return new Decision(AuthorizationResult.APPROVED,
                    "Amount " + amount + " >= " + THRESHOLD + " — randomly APPROVED");
        } else {
            return new Decision(AuthorizationResult.DECLINED,
                    "Amount " + amount + " >= " + THRESHOLD + " — randomly DECLINED");
        }
    }
}
