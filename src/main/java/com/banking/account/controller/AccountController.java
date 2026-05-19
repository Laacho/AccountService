package com.banking.account.controller;

import com.banking.account.dto.request.AccountFilterRequest;
import com.banking.account.dto.request.CreateAccountRequest;
import com.banking.account.dto.request.GenerateStatementRequest;
import com.banking.account.dto.request.UpdateAccountStatusRequest;
import com.banking.account.dto.response.AccountResponse;
import com.banking.account.dto.response.AccountSummaryResponse;
import com.banking.account.dto.response.StatementResponse;
import com.banking.account.service.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Public REST controller for the Account-Service.
 *
 * <p>All routes under {@code /api/v1/accounts} require a valid JWT.
 * The authenticated user identity is resolved from the Spring Security
 * {@code Authentication} principal populated by {@code JwtFilter}.</p>
 *
 * <p>This controller is intentionally thin — all business logic lives in
 * {@link AccountService}.</p>
 */
@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
@Validated
@Slf4j
@Tag(name = "Accounts", description = "Bank account lifecycle management")
@SecurityRequirement(name = "bearerAuth")
public class AccountController {

    private final AccountService accountService;

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/v1/accounts
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Creates a new bank account for the authenticated user.
     *
     * <p>The userId is resolved from the JWT via the {@code JwtFilter} —
     * no custom headers required.</p>
     *
     * <p><strong>Inter-service calls:</strong>
     * <ol>
     *   <li>HTTP sync → User-Service GET /internal/users/{userId}</li>
     *   <li>Async → Kafka {@code account-events}: {@code account.created}</li>
     * </ol>
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Create a new bank account",
            description = "Opens a new bank account (CURRENT, SAVINGS, or BUSINESS) with a zero balance. " +
                    "The IBAN is generated automatically. " +
                    "Triggers an account.created Kafka event consumed by Notification-Service."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Account created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request body (validation failed)"),
            @ApiResponse(responseCode = "404", description = "User not found in User-Service"),
            @ApiResponse(responseCode = "422", description = "Unsupported currency code")
    })
    public ResponseEntity<AccountResponse> createAccount(Authentication authentication, @RequestBody @Valid CreateAccountRequest request) {

        UUID userId = extractUserId(authentication);
        log.info("Creating {} account in {} for user {}",
                request.accountType(), request.currency(), userId);

        AccountResponse response = accountService.createAccount(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/v1/accounts
    // ─────────────────────────────────────────────────────────────────────────


    /**
     * Lists all accounts owned by the authenticated user with optional filtering.
     *
     * <p><strong>Inter-service calls:</strong> None. All data is local.</p>
     */
    @GetMapping
    @Operation(
            summary = "List accounts for the authenticated user",
            description = "Returns a paginated list of accounts. Supports filtering by status, " +
                    "currency, and account type. Default page size is 20."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Accounts listed successfully")
    })
    public ResponseEntity<Page<AccountSummaryResponse>> listAccounts(
            Authentication authentication,
            AccountFilterRequest request,
            @PageableDefault(size = 20, sort = "openedAt", direction = Sort.Direction.DESC)
            Pageable pageable) {

        UUID userId = extractUserId(authentication);
        log.debug("Listing accounts for user {} — status={}, currency={}, type={}",
                userId, request.status(), request.currency(), request.accountType());

        return ResponseEntity.ok(
                accountService.listAccounts(userId, request, pageable));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/v1/accounts/{accountId}
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns full detail of a single account, including live balance.
     *
     * <p><strong>Inter-service calls:</strong> None. Balance is read from Redis
     * cache (fallback: DB) within this service.</p>
     */
    @GetMapping("/{accountId}")
    @Operation(
            summary = "Get account details",
            description = "Returns full account information including the live balance. " +
                    "Returns 403 if the account belongs to a different user."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Account found"),
            @ApiResponse(responseCode = "403", description = "Account belongs to a different user"),
            @ApiResponse(responseCode = "404", description = "Account not found")
    })
    public ResponseEntity<AccountResponse> getAccount(
            Authentication authentication,

            @Parameter(description = "Account UUID", required = true)
            @PathVariable UUID accountId) {

        UUID userId = extractUserId(authentication);
        return ResponseEntity.ok(accountService.getAccount(accountId, userId));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PATCH /api/v1/accounts/{accountId}/status
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Changes the status of an account (freeze, unfreeze, or close).
     *
     * <p>Allowed transitions: ACTIVE ↔ FROZEN, ACTIVE/FROZEN → CLOSED (zero balance only)</p>
     *
     * <p><strong>Inter-service calls:</strong> Async → Kafka {@code account-events}:
     * {@code account.frozen} / {@code account.unfrozen} / {@code account.closed}</p>
     */
    @PatchMapping("/{accountId}/status")
    @Operation(
            summary = "Update account status",
            description = "Transitions the account between ACTIVE / FROZEN / CLOSED. " +
                    "Closing an account requires the balance to be zero. " +
                    "Publishes an account event to Kafka on success."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Status updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid status value"),
            @ApiResponse(responseCode = "403", description = "Account belongs to a different user"),
            @ApiResponse(responseCode = "404", description = "Account not found"),
            @ApiResponse(responseCode = "409", description = "Invalid state transition or non-zero balance on close")
    })
    public ResponseEntity<AccountResponse> updateAccountStatus(
            Authentication authentication,

            @Parameter(description = "Account UUID", required = true)
            @PathVariable UUID accountId,

            @RequestBody @Valid UpdateAccountStatusRequest request) {

        UUID userId = extractUserId(authentication);
        log.info("Status change request for account {} by user {} → {}",
                accountId, userId, request.status());

        return ResponseEntity.ok(
                accountService.updateAccountStatus(accountId, userId, request));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/v1/accounts/{accountId}/statements
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Lists previously generated statements for an account.
     *
     * <p><strong>Inter-service calls:</strong> None.</p>
     */
    @GetMapping("/{accountId}/statements")
    @Operation(
            summary = "List statements for an account",
            description = "Returns previously generated statements. Filter by date range using " +
                    "'from' and 'to' query parameters (ISO date, yyyy-MM-dd)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Statements listed successfully"),
            @ApiResponse(responseCode = "403", description = "Account belongs to a different user"),
            @ApiResponse(responseCode = "404", description = "Account not found")
    })
    public ResponseEntity<Page<StatementResponse>> getStatements(
            Authentication authentication,

            @Parameter(description = "Account UUID", required = true)
            @PathVariable UUID accountId,

            @Parameter(description = "Filter: statements with periodStart >= this date (yyyy-MM-dd)")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,

            @Parameter(description = "Filter: statements with periodEnd <= this date (yyyy-MM-dd)")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,

            @PageableDefault(sort = "generatedAt", direction = Sort.Direction.DESC)
            Pageable pageable) {

        return ResponseEntity.ok(
                accountService.getStatements(accountId, extractUserId(authentication), from, to, pageable));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/v1/accounts/{accountId}/statements/generate
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Generates a new account statement for the given period.
     *
     * <p><strong>Inter-service calls:</strong> HTTP sync → Transaction-Service
     * {@code GET /internal/transactions/account/{accountId}?from=...&to=...}</p>
     */
    @PostMapping("/{accountId}/statements/generate")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Generate an account statement",
            description = "Generates a new statement for the specified date range. " +
                    "Calls Transaction-Service (HTTP sync) to retrieve the full transaction list, " +
                    "then computes opening/closing balances and persists the statement."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Statement generated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid date range (periodEnd before periodStart)"),
            @ApiResponse(responseCode = "403", description = "Account belongs to a different user"),
            @ApiResponse(responseCode = "404", description = "Account not found"),
            @ApiResponse(responseCode = "409", description = "Statement for this period already exists")
    })
    public ResponseEntity<StatementResponse> generateStatement(
            Authentication authentication,

            @Parameter(description = "Account UUID", required = true)
            @PathVariable UUID accountId,

            @RequestBody @Valid GenerateStatementRequest request) {

        UUID userId = extractUserId(authentication);
        log.info("Generating statement for account {} period {} to {} by user {}",
                accountId, request.periodStart(), request.periodEnd(), userId);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(accountService.generateStatement(accountId, userId, request));
    }


    //helper
    private UUID extractUserId(Authentication authentication) {
        return UUID.fromString(authentication.getPrincipal().toString());
    }
}
