package com.banking.account.dto.request;

import com.banking.account.bankAccount.model.AccountType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

// PUBLIC API REQUESTS

/**
 * Request body for POST /api/v1/accounts
 * Creates a new bank account for the authenticated user.
 */
public record CreateAccountRequest(

    @NotNull(message = "accountType is required")
    AccountType accountType,

    /**
     * ISO 4217 currency code. Validated against Joda CurrencyUnit.
     * Examples: "BGN", "EUR", "USD", "GBP"
     */
    @NotBlank(message = "currency is required")
    @Pattern(regexp = "^[A-Z]{3}$", message = "currency must be a 3-letter ISO 4217 code")
    String currency,

    @NotNull(message = "Branch name is required")
    String branchName

) {}

