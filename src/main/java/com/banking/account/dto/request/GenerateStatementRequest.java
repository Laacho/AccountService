package com.banking.account.dto.request;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;

/**
 * Request body for POST /api/v1/accounts/{accountId}/statements/generate
 * Triggers on-demand statement generation for the given period.
 */
public record GenerateStatementRequest(

        @NotNull(message = "periodStart is required")
        LocalDate periodStart,

        @NotNull(message = "periodEnd is required")
        LocalDate periodEnd

) {

    /**
     * Compact canonical constructor — validates period logic after field assignment.
     */
    public GenerateStatementRequest {
        if (periodStart != null && periodEnd != null && periodEnd.isBefore(periodStart)) {
            throw new IllegalArgumentException("Period end must not be before periodStart");
        }
        if (periodEnd != null && periodEnd.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Period end must not be in the future");
        }
    }

}
