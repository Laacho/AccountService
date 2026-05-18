package com.banking.account.util;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class AccountNumberGenerator {

    private final static SecureRandom random = new SecureRandom();

    // Total account number length = accountType(2) + body(7) + checkDigit(1) = 10
    private static final int BODY_LENGTH = 7;


    public String generate(String accountType) {
        validateAccountType(accountType);

        String body = generateRandomBody();
        String partial = accountType + body;
        int checkDigit = computeLuhnCheckDigit(partial);

        return partial+checkDigit;
    }

    public boolean isValid(String accountNumber) {
        if (accountNumber == null || !accountNumber.matches("\\d{10}")) {
            return false;
        }
        return luhnSum(accountNumber) % 10 == 0;
    }

    private int luhnSum(String fullNumber) {
        return luhnSumInternal(fullNumber, false);
    }

    private int computeLuhnCheckDigit(String partial) {
        int sum = luhnSumInternal(partial, true);
        return (10 - (sum % 10)) % 10;
    }

    private int luhnSumInternal(String number, boolean doubleFirst) {
        int sum = 0;
        boolean alternate = doubleFirst;
        for (int i = number.length() - 1; i >= 0; i--) {
            int digit = number.charAt(i) - '0';

            if (alternate) {
                digit *= 2;
                if (digit > 9) digit -= 9;
            }

            sum      += digit;
            alternate = !alternate;
        }
        return sum;
    }

    private String generateRandomBody() {
        StringBuilder sb = new StringBuilder(BODY_LENGTH);
        for (int i = 0; i < BODY_LENGTH; i++) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }

    private void validateAccountType(String accountType) {
        if (accountType == null || !accountType.matches("\\d{2}")) {
            throw new IllegalArgumentException(
                    "Account type must be exactly 2 digits: '00' (current), '01' (savings), '02' (business)"
            );
        }
    }
}
