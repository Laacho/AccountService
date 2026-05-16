package com.banking.account.service;

import com.banking.account.dto.request.*;
import com.banking.account.dto.response.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Service contract for all account operations.
 * The implementation ({@code AccountServiceImpl}) is the only class that
 * should be used by the controllers — never the repository directly.
 */
public interface AccountService {

    // ─── Public API operations ───────────────────────────────────────────────

    /**
     * Opens a new bank account for the given user.
     * Validates user existence via User-Service (HTTP sync).
     * Publishes account.created to Kafka.
     */
    AccountResponse createAccount(UUID userId, CreateAccountRequest request);

    /**
     * Returns a paginated, filterable list of accounts owned by the user.
     */
    Page<AccountSummaryResponse> listAccounts(
            UUID userId,
            AccountFilterRequest request,
            Pageable pageable);

    /**
     * Returns full account detail (including balance) for the given account.
     * Verifies the account belongs to userId — throws 403 if not.
     */
    AccountResponse getAccount(UUID accountId, UUID userId);

    /**
     * Applies a status transition (freeze / unfreeze / close) to an account.
     * Publishes the corresponding Kafka event after transition.
     */
    AccountResponse updateAccountStatus(
            UUID accountId,
            UUID userId,
            UpdateAccountStatusRequest request);

    /**
     * Returns paginated statement list for the account with optional date range filter.
     */
    Page<StatementResponse> getStatements(
            UUID accountId,
            UUID userId,
            LocalDate from,
            LocalDate to,
            Pageable pageable);

    /**
     * Generates a new statement by fetching transactions from Transaction-Service
     * (HTTP sync via Feign), then computing and persisting the statement record.
     */
    StatementResponse generateStatement(
            UUID accountId,
            UUID userId,
            GenerateStatementRequest request);

    // ─── Internal API operations ─────────────────────────────────────────────

    /**
     * Returns slim account metadata for internal service use.
     * Does NOT check userId — callers are trusted internal services.
     */
    InternalAccountResponse getAccountForInternalUse(UUID accountId);

    /**
     * Returns the live balance for an account.
     * Reads from Redis cache first; falls back to DB on miss.
     */
    BalanceResponse getBalanceForInternalUse(UUID accountId);

    /**
     * Atomically debits the source and credits the destination.
     * Uses pessimistic locking. Publishes balance-updated events after commit.
     */
    TransferResultResponse executeTransfer(InternalTransferRequest request);
}
