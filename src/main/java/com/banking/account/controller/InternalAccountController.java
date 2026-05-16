package com.banking.account.controller;

import com.banking.account.dto.request.InternalTransferRequest;
import com.banking.account.dto.response.BalanceResponse;
import com.banking.account.dto.response.InternalAccountResponse;
import com.banking.account.dto.response.TransferResultResponse;
import com.banking.account.service.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Internal REST controller for service-to-service communication.
 *
 * <p><strong>Security:</strong> These endpoints are NOT routed through the API Gateway
 * and are therefore unreachable from the public internet. They are accessible only
 * within the {@code banking-internal} Docker network. The {@code X-Internal-Service}
 * header is required on every request; the {@link com.banking.account.config.SecurityConfig}
 * restricts {@code /internal/**} to requests carrying this header AND originating from
 * the internal network (enforced at the network layer by Docker).</p>
 *
 * <p><strong>Callers:</strong>
 * <ul>
 *   <li>{@code GET /internal/accounts/{id}}         — called by Card-Service</li>
 *   <li>{@code GET /internal/accounts/{id}/balance} — called by Transaction-Service</li>
 *   <li>{@code POST /internal/accounts/transfer}    — called by Transaction-Service</li>
 * </ul>
 * </p>
 */
@RestController
@RequestMapping("/internal/accounts")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Internal Accounts", description = "Service-to-service endpoints — NOT exposed via API Gateway")
public class InternalAccountController {

    private final AccountService accountService;

    // ─────────────────────────────────────────────────────────────────────────
    // GET /internal/accounts/{accountId}
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns slim account metadata for a given account ID.
     *
     * <p><strong>Caller:</strong> Card-Service (HTTP sync, Spring OpenFeign).<br>
     * Called before card issuance to verify the linked account:
     * <ul>
     *   <li>exists in Account-Service</li>
     *   <li>is in status {@code ACTIVE}</li>
     *   <li>belongs to the same userId as the card request</li>
     *   <li>currency matches the card's intended currency</li>
     * </ul>
     * </p>
     *
     * @param callerService the name of the calling service (e.g. "card-service")
     * @param accountId     the account UUID from the URL path
     * @return slim account detail or 404
     */
    @GetMapping("/{accountId}")
    @Operation(
        summary = "[INTERNAL] Get account metadata",
        description = "Returns slim account detail (no balance) for service-to-service use. " +
                      "Called by Card-Service before card issuance."
    )
    public ResponseEntity<InternalAccountResponse> getAccount(
            @Parameter(description = "Name of the calling microservice", required = true)
            @RequestHeader("X-Internal-Service") String callerService,

            @Parameter(description = "Account UUID", required = true)
            @PathVariable UUID accountId) {

        log.debug("[INTERNAL] Account lookup — caller: {}, accountId: {}",
                callerService, accountId);

        return ResponseEntity.ok(accountService.getAccountForInternalUse(accountId));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /internal/accounts/{accountId}/balance
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns the live balance of an account.
     *
     * <p><strong>Caller:</strong> Transaction-Service (HTTP sync, Spring OpenFeign).<br>
     * Called on the critical path before every transfer to validate that the source
     * account has sufficient available funds. The balance is read from Redis cache
     * (30-second TTL) and falls back to a DB read on cache miss.</p>
     *
     * <p><strong>Consistency note:</strong> This endpoint reads the balance
     * optimistically. The actual debit is performed atomically inside
     * {@link #executeTransfer(String, InternalTransferRequest)} under a pessimistic
     * write lock, so a small window exists between the balance check and the
     * transfer execution where the balance could change. Transaction-Service must
     * handle the 422 from {@code executeTransfer} as a race-condition retry.</p>
     *
     * @param callerService the name of the calling service
     * @param accountId     the account UUID from the URL path
     * @return live balance including available, blocked, total, and currency
     */
    @GetMapping("/{accountId}/balance")
    @Operation(
        summary = "[INTERNAL] Get account balance",
        description = "Returns live balance (available + blocked + total). " +
                      "Read from Redis cache (30s TTL). Called by Transaction-Service before transfers."
    )
    public ResponseEntity<BalanceResponse> getBalance(
            @Parameter(description = "Name of the calling microservice", required = true)
            @RequestHeader("X-Internal-Service") String callerService,

            @Parameter(description = "Account UUID", required = true)
            @PathVariable UUID accountId) {

        log.debug("[INTERNAL] Balance lookup — caller: {}, accountId: {}",
                callerService, accountId);

        return ResponseEntity.ok(accountService.getBalanceForInternalUse(accountId));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /internal/accounts/transfer
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Executes an atomic debit-credit transfer between two accounts.
     *
     * <p><strong>Caller:</strong> Transaction-Service (HTTP sync, Spring OpenFeign).<br>
     * This is the most critical internal endpoint in the platform. It runs
     * inside a {@code @Transactional} service method that acquires a
     * <em>pessimistic write lock</em> ({@code SELECT ... FOR UPDATE}) on both
     * Balance rows before mutating them, preventing double-spend under concurrency.</p>
     *
     * <p><strong>What this endpoint does (in order):</strong>
     * <ol>
     *   <li>Validates both account IDs exist and are not the same account.</li>
     *   <li>Verifies source account status is {@code ACTIVE} (can debit).</li>
     *   <li>Verifies destination account status is {@code ACTIVE} or {@code FROZEN}
     *       (can credit — incoming transfers to frozen accounts are still allowed).</li>
     *   <li>Verifies both accounts use the requested currency.</li>
     *   <li>Acquires pessimistic write locks on both Balance rows (ordered by
     *       account ID to prevent deadlocks).</li>
     *   <li>Calls {@code Balance.debit(amount)} on the source — throws
     *       {@code InsufficientFundsException} (422) if balance is insufficient.</li>
     *   <li>Calls {@code Balance.credit(amount)} on the destination.</li>
     *   <li>Flushes and commits the JPA transaction.</li>
     *   <li>Invalidates both balance Redis cache entries.</li>
     *   <li>Publishes {@code account.balance-updated} to Kafka for both accounts
     *       (asynchronously, after commit).</li>
     * </ol>
     * </p>
     *
     * <p><strong>Error responses:</strong>
     * <ul>
     *   <li>{@code 404} — source or destination account not found</li>
     *   <li>{@code 409} — source or destination account has an invalid status</li>
     *   <li>{@code 422} — insufficient funds in source account</li>
     *   <li>{@code 422} — currency mismatch (amount currency ≠ account currency)</li>
     * </ul>
     * </p>
     *
     * @param callerService the name of the calling service (logged for tracing)
     * @param request       transfer details — source, destination, amount, currency, reference
     * @return new balances for both accounts after the transfer, plus execution timestamp
     */
    @PostMapping("/transfer")
    @Operation(
        summary = "[INTERNAL] Execute atomic account transfer",
        description = "Atomically debits the source account and credits the destination account. " +
                      "Uses pessimistic locking on Balance rows. " +
                      "Publishes account.balance-updated events to Kafka after commit. " +
                      "Called exclusively by Transaction-Service."
    )
    public ResponseEntity<TransferResultResponse> executeTransfer(
            @Parameter(description = "Name of the calling microservice", required = true)
            @RequestHeader("X-Internal-Service") String callerService,

            @RequestBody @Valid InternalTransferRequest request) {

        log.info("[INTERNAL] Transfer — caller: {}, src: {}, dst: {}, amount: {} {}",
                callerService,
                request.sourceAccountId(),
                request.destinationAccountId(),
                request.amount(),
                request.currency());

        return ResponseEntity.ok(accountService.executeTransfer(request));
    }
}
