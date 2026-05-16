package com.banking.account.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;

/**
 * Represents the live balance of an account.
 * Returned as part of {@link AccountResponse} and directly from
 * GET /internal/accounts/{id}/balance.
 */
@Builder
public record BalanceResponse(
        UUID accountId,
        BigDecimal availableAmount,
        BigDecimal blockedAmount,
        BigDecimal totalAmount,
        String currencyCode,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime lastUpdated
) {

}
