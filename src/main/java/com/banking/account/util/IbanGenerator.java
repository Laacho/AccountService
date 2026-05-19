package com.banking.account.util;

import org.springframework.stereotype.Component;

@Component
public class IbanGenerator {

    public String generateIban(String countryCode, String bankCode, String branchCode, String accountNumber) {
        if (countryCode == null || countryCode.length() != 2) {
            throw new IllegalArgumentException("Country code must be 2 letters");
        }
        if (bankCode == null || bankCode.length() != 4) {
            throw new IllegalArgumentException("Bank code must be 4 characters");
        }
        if (branchCode == null || branchCode.length() != 4) {
            throw new IllegalArgumentException("Branch code must be 4 characters");
        }
        if (accountNumber == null || accountNumber.length() > 16) {
            throw new IllegalArgumentException("Account number must be up to 16 characters");
        }

        String bban = bankCode + branchCode + accountNumber;
        String rearranged = bban + countryCode.toUpperCase() + "00";
        String numeric = toNumericString(rearranged);
        int mod = mod97(numeric);
        int checkDigits = 98 - mod;
        String checkDigitsStr = String.format("%02d", checkDigits);

        return countryCode.toUpperCase() + checkDigitsStr + bban;
    }

    private String toNumericString(String input) {
        StringBuilder sb = new StringBuilder(input.length() * 2);
        for (char ch : input.toCharArray()) {
            if (Character.isDigit(ch)) {
                sb.append(ch);
            } else if (Character.isLetter(ch)) {
                int value = Character.toUpperCase(ch) - 'A' + 10;
                sb.append(value);
            } else {
                throw new IllegalArgumentException("Invalid character in IBAN source: " + ch);
            }
        }
        return sb.toString();
    }

    private int mod97(String numeric) {
        int remainder = 0;
        int index = 0;
        int length = numeric.length();

        while (index < length) {
            int end = Math.min(index + 9, length);
            String part = remainder + numeric.substring(index, end);
            remainder = Integer.parseInt(part) % 97;
            index = end;
        }
        return remainder;
    }
}