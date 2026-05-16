package com.banking.account.bankAccount.model;

import lombok.Getter;

import java.util.Arrays;

/**
 * The type of bank account.
 *
 * <ul>
 *   <li>{@link #CURRENT}  — everyday transactional account (chequing/current)</li>
 *   <li>{@link #SAVINGS}  — interest-bearing savings account</li>
 *   <li>{@link #BUSINESS} — account for legal-entity customers</li>
 * </ul>
 */
//00' (current), '01' (savings), '02' (business)
@Getter
public enum AccountType {
    CURRENT("00"),
    SAVINGS("01"),
    BUSINESS("02"),
    UNKNOWN("N/A");

    private final String code;

    AccountType(String code) {
        this.code = code;
    }

    public static AccountType getByCode(String code) {
        return Arrays.stream(AccountType.values())
                .filter(e -> e.code.equals(code))
                .findFirst()
                .orElse(UNKNOWN);
    }
}