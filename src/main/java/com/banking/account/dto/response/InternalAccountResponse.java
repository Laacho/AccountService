package com.banking.account.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

import com.banking.account.bankAccount.model.AccountStatus;
import com.banking.account.bankAccount.model.AccountType;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * Slim account detail returned by GET /internal/accounts/{id}.
 * Used by Card-Service to validate account existence and status before card issuance.
 */
public record InternalAccountResponse(
        UUID id,
        UUID userId,
        String iban,
        AccountType accountType,
        AccountStatus status,
        String currencyCode,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime openedAt
) {

}
