package com.banking.account.dto.response;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * Returned by POST /internal/accounts/transfer after a successful atomic debit/credit.
 * Transaction-Service uses the new balance snapshots for its own TransactionHistory records.
 */
public record TransferResultResponse(
        String reference,
        BalanceResponse sourceBalance,
        BalanceResponse destinationBalance,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime executedAt
) {

}
