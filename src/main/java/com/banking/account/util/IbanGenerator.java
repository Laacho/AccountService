package com.banking.account.util;

import org.springframework.stereotype.Component;

@Component
public class IbanGenerator {


    public static String generateIban(String countryCode, String bankCode, String branchCode, String accountNumber) {
        // Basic IBAN structure: CC BBBB SSSS CCCC CCCC CCCC
        // CC = Country Code (2 letters)
        // BBBB = Bank Code (4 characters)
        // SSSS = Branch Code (4 characters)
        // CCCC CCCC CCCC = Account Number (up to 16 characters)

        // Validate inputs
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
        // Step 1: Move country code + "00" to the end
        String rearranged = bban + countryCode.toUpperCase() + "00";

        // Step 2: Convert letters → numbers
        String numeric = toNumericString(rearranged);

        // Step 3: Compute mod97
        int mod = mod97(numeric);

        // Step 4: Check digits = 98 - mod
        int checkDigits = 98 - mod;
        String checkDigitsStr = String.format("%02d", checkDigits);

        // Final IBAN
        return countryCode.toUpperCase() + checkDigitsStr + bban;

    }

    private static String toNumericString(String input) {
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

    private static int mod97(String numeric) {
        int remainder = 0;
        int index = 0;
        int length = numeric.length();

        while (index < length) {
            int end = Math.min(index + 9, length); // 9–10 digits per step is safe
            String part = remainder + numeric.substring(index, end);
            remainder = Integer.parseInt(part) % 97;
            index = end;
        }
        return remainder;
    }
}
