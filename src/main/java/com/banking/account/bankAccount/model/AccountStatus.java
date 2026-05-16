package com.banking.account.bankAccount.model;

import java.util.Arrays;

/**
 * Lifecycle status of a {@link BankAccount}.
 *
 * <p>Allowed state transitions:</p>
 * <pre>
 *   ACTIVE ──▶ FROZEN ──▶ ACTIVE     (freeze / unfreeze)
 *   ACTIVE ──▶ CLOSED               (close, only if balance = 0)
 *   FROZEN ──▶ CLOSED               (close, only if balance = 0)
 * </pre>
 */
public enum AccountStatus {
    ACTIVE("Active"),
    FROZEN("Frozen"),
    CLOSED("Closed"),
    UNKNOWN("Unknown");

    private final String code;
    AccountStatus(String code) {
        this.code = code;
    }

    public static AccountStatus getByCode(String code) {
        return Arrays.stream(AccountStatus.values())
                .filter(e -> e.code.equals(code))
                .findFirst()
                .orElse(UNKNOWN);
    }
}
