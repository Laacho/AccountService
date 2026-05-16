package com.banking.account.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import com.banking.account.bankAccount.model.AccountStatus;
import com.banking.account.bankAccount.model.AccountType;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;

/**
 * Compact account view for paginated lists.
 * Returned by GET /api/v1/accounts.
 */
@Builder
public record AccountSummaryResponse(
        UUID id,
        String iban,
        AccountType accountType,
        AccountStatus status,
        BigDecimal availableAmount,
        String currencyCode,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime openedAt
) {

}
