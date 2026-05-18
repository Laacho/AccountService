package com.banking.account.dto.request;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for POST /internal/accounts/transfer
 * Executes an atomic debit-credit between two accounts.
 * Called exclusively by Transaction-Service.
 */
public record InternalTransferRequest(

        @NotNull(message = "sourceAccountId is required")
        UUID sourceAccountId,

        @NotNull(message = "destinationAccountId is required")
        UUID destinationAccountId,

        @NotNull(message = "amount is required")
        @DecimalMin(value = "0.0001", message = "amount must be greater than zero")
        @Digits(integer = 15, fraction = 4, message = "amount must have at most 15 integer digits and 4 decimal places")
        BigDecimal amount,

        /**
         * ISO 4217 currency code. Both accounts must have the same currency;
         * this field is validated against both account records.
         */
        @NotBlank(message = "currency is required")
        @Pattern(regexp = "^[A-Z]{3}$", message = "currency must be a 3-letter ISO 4217 code")
        String currency,

        /**
         * The transaction reference from Transaction-Service (used for tracing).
         * Stored in the audit log but not persisted in Account-Service's own tables.
         */
        @NotBlank(message = "reference is required")
        @Size(max = 50)
        String reference,

        /**
         * Idempotency key forwarded from Transaction-Service.
         * Used by Account-Service for duplicate-transfer detection via the ledger unique constraint.
         */
        @NotBlank(message = "idempotencyKey is required")
        @Size(max = 64, message = "idempotencyKey must not exceed 64 characters")
        String idempotencyKey

) {

}
