package com.banking.account.dto.request;

import com.banking.account.bankAccount.model.AccountStatus;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request body for PATCH /api/v1/accounts/{accountId}/status
 * Changes the status of an account (freeze, unfreeze, close).
 */
public record UpdateAccountStatusRequest(

        @NotNull(message = "status is required")
        AccountStatus status,

        /** Optional human-readable reason for audit log / notification message. */
        @Size(max = 255, message = "reason must not exceed 255 characters")
        String reason

) {

}
