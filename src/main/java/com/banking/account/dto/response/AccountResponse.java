package com.banking.account.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

import com.banking.account.bankAccount.model.AccountStatus;
import com.banking.account.bankAccount.model.AccountType;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;

/**
 * Full account detail. Returned by:
 * - POST /api/v1/accounts (on creation)
 * - GET  /api/v1/accounts/{id}
 * - PATCH /api/v1/accounts/{id}/status
 */
@Builder
public record AccountResponse(
        UUID id,
        UUID userId,
        String iban,
        String accountNumber,
        AccountType accountType,
        AccountStatus status,
        BalanceResponse balance,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime openedAt,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime closedAt,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime createdAt,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime updatedAt
) {

}