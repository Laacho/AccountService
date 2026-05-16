package com.banking.account.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;

/**
 * Returned by:
 * - GET  /api/v1/accounts/{id}/statements (in a page)
 * - POST /api/v1/accounts/{id}/statements/generate
 */
@Builder
public record StatementResponse(
        UUID id,
        UUID accountId,
        @JsonFormat(pattern = "yyyy-MM-dd")
        LocalDate periodStart,
        @JsonFormat(pattern = "yyyy-MM-dd")
        LocalDate periodEnd,
        BigDecimal openingBalance,
        BigDecimal closingBalance,
        String currencyCode,
        Integer transactionCount,
        String statementRef,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime generatedAt
) {

}
