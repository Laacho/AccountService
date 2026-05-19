package com.banking.account.service;

import com.banking.account.accountLedger.model.AccountLedgerEntry;
import com.banking.account.accountLedger.model.LedgerDirection;
import com.banking.account.accountLedger.service.AccountLedgerEntryService;
import com.banking.account.accountStatement.model.AccountStatement;
import com.banking.account.accountStatement.service.AccountStatementService;
import com.banking.account.balance.model.Balance;
import com.banking.account.bankAccount.model.AccountStatus;
import com.banking.account.bankAccount.model.AccountType;
import com.banking.account.bankAccount.model.BankAccount;
import com.banking.account.bankAccount.repository.BankAccountRepository;
import com.banking.account.bankBranch.service.BankBranchService;
import com.banking.account.dto.request.*;
import com.banking.account.dto.response.*;
import com.banking.account.exception.AccountAccessDeniedException;
import com.banking.account.exception.AccountNotFoundException;
import com.banking.account.exception.CurrencyMismatchException;
import com.banking.account.exception.InvalidAccountStateException;
import com.banking.account.feing.service.UserClientService;
import com.banking.account.aspect.Logged;
import com.banking.account.util.AccountNumberGenerator;
import com.banking.account.util.AccountSpecification;
import com.banking.account.util.BankConstants;
import com.banking.account.util.IbanGenerator;
import org.joda.money.CurrencyUnit;
import org.joda.money.Money;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Logged
@Service
public class AccountServiceImpl implements AccountService {

    private final BankBranchService bankBranchService;
    private final BankAccountRepository bankAccountRepository;
    private final UserClientService userClientService;
    private final AccountStatementService accountStatementService;
    private final AccountLedgerEntryService accountLedgerEntryService;
    private final AccountNumberGenerator accountNumberGenerator;
    private final IbanGenerator ibanGenerator;

    @Autowired
    public AccountServiceImpl(BankBranchService bankBranchService,
                              BankAccountRepository bankAccountRepository,
                              UserClientService userClientService,
                              AccountStatementService accountStatementService,
                              AccountLedgerEntryService accountLedgerEntryService,
                              AccountNumberGenerator accountNumberGenerator,
                              IbanGenerator ibanGenerator) {
        this.bankBranchService = bankBranchService;
        this.bankAccountRepository = bankAccountRepository;
        this.userClientService = userClientService;
        this.accountStatementService = accountStatementService;
        this.accountLedgerEntryService = accountLedgerEntryService;
        this.accountNumberGenerator = accountNumberGenerator;
        this.ibanGenerator = ibanGenerator;
    }

    @Override
    @Transactional
    public AccountResponse createAccount(UUID userId, CreateAccountRequest request) {
        if (request.accountType() == AccountType.UNKNOWN) {
            throw new IllegalArgumentException("Account type UNKNOWN is not valid");
        }

        userClientService.validateUserExists(userId);
        String accountNumber = accountNumberGenerator.generate(request.accountType().getCode());

        String branchCode = bankBranchService.findCodeByName(request.branchName());
        String iban = ibanGenerator.generateIban(
                BankConstants.COUNTRY_CODE,
                BankConstants.BANK_CODE,
                branchCode,
                accountNumber
        );

        BankAccount bankAccount = BankAccount.builder()
                .userId(userId)
                .iban(iban)
                .accountNumber(accountNumber)
                .accountType(request.accountType())
                .status(AccountStatus.ACTIVE)
                .openedAt(LocalDateTime.now())
                .build();

        Balance balance = Balance.zeroBalance(bankAccount, request.currency());
        bankAccount.assignBalance(balance);

        // Save BankAccount only — CascadeType.ALL propagates persist to Balance automatically
        BankAccount saveBankAccount = bankAccountRepository.save(bankAccount);

        // TODO: publish account.created Kafka event

        Balance savedBalance = saveBankAccount.getBalance();
        BigDecimal totalAmount = savedBalance.getAvailableAmount().add(savedBalance.getBlockedAmount());

        BalanceResponse balanceResponse = BalanceResponse.builder()
                .accountId(saveBankAccount.getId())
                .availableAmount(savedBalance.getAvailableAmount())
                .blockedAmount(savedBalance.getBlockedAmount())
                .totalAmount(totalAmount)
                .currencyCode(savedBalance.getCurrencyCode())
                .lastUpdated(savedBalance.getLastUpdated())
                .build();

        return AccountResponse.builder()
                .id(saveBankAccount.getId())
                .userId(userId)
                .iban(iban)
                .accountNumber(accountNumber)
                .accountType(request.accountType())
                .status(saveBankAccount.getStatus())
                .balance(balanceResponse)
                .openedAt(saveBankAccount.getOpenedAt())
                .closedAt(saveBankAccount.getClosedAt())
                .createdAt(saveBankAccount.getCreatedAt())
                .updatedAt(saveBankAccount.getUpdatedAt())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AccountSummaryResponse> listAccounts(UUID userId, AccountFilterRequest request, Pageable pageable) {
        userClientService.validateUserExists(userId);

        Specification<BankAccount> spec = Specification
                .<BankAccount>where((root, query, cb) -> cb.equal(root.get("userId"), userId))
                .and(AccountSpecification.withFilters(request));

        return bankAccountRepository.findAll(spec, pageable).map(account -> {
            Balance balance = account.getBalance();

            return AccountSummaryResponse.builder()
                    .id(account.getId())
                    .iban(account.getIban())
                    .accountType(account.getAccountType())
                    .status(account.getStatus())
                    .availableAmount(balance.getAvailableAmount())
                    .currencyCode(balance.getCurrencyCode())
                    .openedAt(account.getOpenedAt())
                    .build();
        });
    }


    @Override
    @Transactional(readOnly = true)
    public AccountResponse getAccount(UUID accountId, UUID userId) {
        userClientService.validateUserExists(userId);

        BankAccount bankAccount = bankAccountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountId));

        if (!bankAccount.getUserId().equals(userId)) {
            throw new AccountAccessDeniedException("Account " + accountId + " does not belong to user " + userId);
        }

        return toAccountResponse(bankAccount, userId);
    }

    @Override
    @Transactional
    public AccountResponse updateAccountStatus(UUID accountId, UUID userId, UpdateAccountStatusRequest request) {
        userClientService.validateUserExists(userId);

        BankAccount bankAccount = bankAccountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountId));

        if (!bankAccount.getUserId().equals(userId)) {
            throw new AccountAccessDeniedException("Account " + accountId + " does not belong to user " + userId);
        }

        switch (request.status()) {
            case FROZEN -> bankAccount.freeze();
            case ACTIVE -> bankAccount.unfreeze();
            case CLOSED -> bankAccount.close();
            default -> throw new InvalidAccountStateException("Cannot transition to status: " + request.status());
        }

        bankAccountRepository.save(bankAccount);
        // TODO: publish kafka event

        return toAccountResponse(bankAccount, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<StatementResponse> getStatements(UUID accountId, UUID userId, LocalDate from, LocalDate to, Pageable pageable) {
        userClientService.validateUserExists(userId);

        BankAccount bankAccount = bankAccountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountId));

        if (!bankAccount.getUserId().equals(userId)) {
            throw new AccountAccessDeniedException("Account " + accountId + " does not belong to user " + userId);
        }
        if (to == null) {
            to = LocalDate.now();
        }
        if (from == null) {
            from = LocalDate.from(bankAccount.getOpenedAt());
        }
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("From date is after to date for account " + accountId);
        }

        Page<AccountStatement> result = accountStatementService.findByAccountIdWithFilterForDates(accountId, from, to, pageable);
        return result.map(statement ->
                StatementResponse.builder()
                        .id(statement.getId())
                        .accountId(accountId)
                        .periodStart(statement.getPeriodStart())
                        .periodEnd(statement.getPeriodEnd())
                        .openingBalance(statement.getOpeningBalance().getAmount())
                        .closingBalance(statement.getClosingBalance().getAmount())
                        .currencyCode(statement.getOpeningBalance().getCurrencyCode())
                        .transactionCount(statement.getTransactionCount())
                        .statementRef(statement.getStatementRef())
                        .generatedAt(statement.getGeneratedAt())
                        .build()
        );
    }

    @Override
    @Transactional
    public StatementResponse generateStatement(UUID accountId, UUID userId, GenerateStatementRequest request) {
        userClientService.validateUserExists(userId);

        BankAccount bankAccount = bankAccountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountId));

        if (!bankAccount.getUserId().equals(userId)) {
            throw new AccountAccessDeniedException("Account " + accountId + " does not belong to user " + userId);
        }

        if (accountStatementService.existsByAccountAndPeriod(accountId, request.periodStart(), request.periodEnd())) {
            throw new IllegalStateException("Statement already exists for account " + accountId
                    + " period " + request.periodStart() + " to " + request.periodEnd());
        }

        List<AccountStatement> result = accountStatementService.findByAccountIdWithinRange(accountId, request);

        if (result.isEmpty()) {
            throw new IllegalStateException("No statements found for account " + accountId + " in requested period");
        }

        result.sort(Comparator.comparing(AccountStatement::getPeriodStart));

        AccountStatement first = result.getFirst();
        AccountStatement last = result.getLast();
        int totalTxCount = result.stream().mapToInt(AccountStatement::getTransactionCount).sum();

        String statementRef = "STMT-" + bankAccount.getIban()
                + "-" + first.getPeriodStart()
                + "-" + last.getPeriodEnd();

        AccountStatement composite = AccountStatement.builder()
                .bankAccount(bankAccount)
                .periodStart(first.getPeriodStart())
                .periodEnd(last.getPeriodEnd())
                .openingBalance(first.getOpeningBalance())
                .closingBalance(last.getClosingBalance())
                .transactionCount(totalTxCount)
                .statementRef(statementRef)
                .build();

        AccountStatement saved = accountStatementService.save(composite);

        return StatementResponse.builder()
                .id(saved.getId())
                .accountId(accountId)
                .periodStart(saved.getPeriodStart())
                .periodEnd(saved.getPeriodEnd())
                .openingBalance(saved.getOpeningBalance().getAmount())
                .closingBalance(saved.getClosingBalance().getAmount())
                .currencyCode(saved.getOpeningBalance().getCurrencyCode())
                .transactionCount(saved.getTransactionCount())
                .statementRef(saved.getStatementRef())
                .generatedAt(saved.getGeneratedAt())
                .build();
    }

    @Override
    public InternalAccountResponse getAccountForInternalUse(UUID accountId) {

        BankAccount bankAccount = bankAccountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountId));
        Balance balance = bankAccount.getBalance();
        return InternalAccountResponse.builder()
                .id(bankAccount.getId())
                .userId(bankAccount.getUserId())
                .iban(bankAccount.getIban())
                .accountType(bankAccount.getAccountType())
                .status(bankAccount.getStatus())
                .currencyCode(balance.getCurrencyCode())
                .openedAt(bankAccount.getOpenedAt())
                .build();
    }

    @Override
    public BalanceResponse getBalanceForInternalUse(UUID accountId) {
        BankAccount bankAccount = bankAccountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountId));
        Balance balance = bankAccount.getBalance();
        return BalanceResponse.builder()
                .accountId(bankAccount.getId())
                .availableAmount(balance.getAvailableAmount())
                .blockedAmount(balance.getBlockedAmount())
                .totalAmount(balance.getAvailableAmount().add(balance.getBlockedAmount()))
                .currencyCode(balance.getCurrencyCode())
                .lastUpdated(balance.getLastUpdated())
                .build();
    }

    @Override
    @Transactional
    public TransferResultResponse executeTransfer(InternalTransferRequest request) {
        if (request.sourceAccountId().equals(request.destinationAccountId())) {
            throw new IllegalArgumentException("Self-transfer not allowed");
        }

        // Fast-path idempotency check — not race-safe alone, re-checked after lock acquisition
        if (accountLedgerEntryService.findAlreadyExistingLedger(request.idempotencyKey(), request.sourceAccountId())) {
            return rebuildCacheResponse(request);
        }

        // Lock accounts in deterministic UUID order to prevent deadlock on reverse concurrent transfers
        boolean sourceFirst = request.sourceAccountId().compareTo(request.destinationAccountId()) < 0;
        UUID firstId  = sourceFirst ? request.sourceAccountId()      : request.destinationAccountId();
        UUID secondId = sourceFirst ? request.destinationAccountId() : request.sourceAccountId();

        BankAccount first = bankAccountRepository
                .findByIdForUpdate(firstId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + firstId));
        BankAccount second = bankAccountRepository
                .findByIdForUpdate(secondId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + secondId));

        BankAccount source      = sourceFirst ? first : second;
        BankAccount destination = sourceFirst ? second : first;

        // Post-lock re-check: handles concurrent requests that both passed the fast-path check
        if (accountLedgerEntryService.findAlreadyExistingLedger(request.idempotencyKey(), request.sourceAccountId())) {
            return rebuildCacheResponse(request, source, destination);
        }

        if (!source.canDebit()) {
            throw new InvalidAccountStateException(
                    "Source account cannot debit. Current status: " + source.getStatus());
        }

        if (!destination.canCredit()) {
            throw new InvalidAccountStateException(
                    "Destination account cannot credit. Current status: " + destination.getStatus());
        }

        String currencyCode = source.getBalance().getCurrencyCode();
        if (!currencyCode.equals(destination.getBalance().getCurrencyCode())) {
            throw new CurrencyMismatchException(
                    "Cross-currency transfers not supported. Source=%s, Destination=%s"
                            .formatted(currencyCode, destination.getBalance().getCurrencyCode()));
        }
        if (!currencyCode.equals(request.currency())) {
            throw new CurrencyMismatchException(
                    "Request currency does not match account currency. Account=%s, Request=%s"
                            .formatted(currencyCode, request.currency()));
        }

        Money transferAmount = Money.of(CurrencyUnit.of(request.currency()), request.amount());

        BigDecimal sourceBalanceBefore = source.getBalance().getAvailableAmount();
        BigDecimal destBalanceBefore   = destination.getBalance().getAvailableAmount();

        LocalDateTime executedAt = LocalDateTime.now();

        source.getBalance().debit(transferAmount);
        destination.getBalance().credit(transferAmount);

        bankAccountRepository.save(source);
        bankAccountRepository.save(destination);

        AccountLedgerEntry debitEntry = AccountLedgerEntry.builder()
                .bankAccount(source)
                .direction(LedgerDirection.DEBIT)
                .amount(request.amount())
                .currencyCode(request.currency())
                .balanceBefore(sourceBalanceBefore)
                .balanceAfter(source.getBalance().getAvailableAmount())
                .counterpartyAccountId(destination.getId())
                .reference(request.reference())
                .idempotencyKey(request.idempotencyKey())
                .description("Transfer to " + destination.getIban())
                .build();

        AccountLedgerEntry creditEntry = AccountLedgerEntry.builder()
                .bankAccount(destination)
                .direction(LedgerDirection.CREDIT)
                .amount(request.amount())
                .currencyCode(request.currency())
                .balanceBefore(destBalanceBefore)
                .balanceAfter(destination.getBalance().getAvailableAmount())
                .counterpartyAccountId(source.getId())
                .reference(request.reference())
                .idempotencyKey(request.idempotencyKey())
                .description("Transfer from " + source.getIban())
                .build();

        accountLedgerEntryService.saveAll(List.of(debitEntry, creditEntry));

        return new TransferResultResponse(
                request.reference(),
                toBalanceResponse(source),
                toBalanceResponse(destination),
                executedAt
        );
    }

    private TransferResultResponse rebuildCacheResponse(InternalTransferRequest request) {
        BankAccount source = bankAccountRepository.findById(request.sourceAccountId())
                .orElseThrow(() -> new AccountNotFoundException("Source not found"));
        BankAccount destination = bankAccountRepository.findById(request.destinationAccountId())
                .orElseThrow(() -> new AccountNotFoundException("Destination not found"));
        return rebuildCacheResponse(request, source, destination);
    }

    private TransferResultResponse rebuildCacheResponse(InternalTransferRequest request, BankAccount source, BankAccount destination) {
        AccountLedgerEntry originalDebit = accountLedgerEntryService
                .findByIdempotencyKeyAndBanAccountId(request.idempotencyKey(), request.sourceAccountId())
                .orElseThrow(() -> new IllegalStateException(
                        "exists check passed but ledger entry not found — concurrency bug"));
        AccountLedgerEntry originalCredit = accountLedgerEntryService
                .findByIdempotencyKeyAndBanAccountId(request.idempotencyKey(), request.destinationAccountId())
                .orElseThrow(() -> new IllegalStateException(
                        "debit found but credit entry missing — data integrity issue"));

        return new TransferResultResponse(
                originalDebit.getReference(),
                toSnapshotBalanceResponse(source, originalDebit),
                toSnapshotBalanceResponse(destination, originalCredit),
                originalDebit.getCreatedAt()
        );
    }

    private BalanceResponse toSnapshotBalanceResponse(BankAccount account, AccountLedgerEntry entry) {
        BigDecimal available = entry.getBalanceAfter();
        BigDecimal blocked   = account.getBalance().getBlockedAmount();
        return BalanceResponse.builder()
                .accountId(account.getId())
                .availableAmount(available)
                .blockedAmount(blocked)
                .totalAmount(available.add(blocked))
                .currencyCode(entry.getCurrencyCode())
                .lastUpdated(entry.getCreatedAt())
                .build();
    }

    private BalanceResponse toBalanceResponse(BankAccount account) {
        Balance balance = account.getBalance();
        return BalanceResponse.builder()
                .accountId(account.getId())
                .availableAmount(balance.getAvailableAmount())
                .blockedAmount(balance.getBlockedAmount())
                .totalAmount(balance.getAvailableAmount().add(balance.getBlockedAmount()))
                .currencyCode(balance.getCurrencyCode())
                .lastUpdated(balance.getLastUpdated())
                .build();
    }

    private AccountResponse toAccountResponse(BankAccount account, UUID userId) {
        Balance balance = account.getBalance();
        BalanceResponse balanceResponse = BalanceResponse.builder()
                .accountId(account.getId())
                .availableAmount(balance.getAvailableAmount())
                .blockedAmount(balance.getBlockedAmount())
                .totalAmount(balance.getAvailableAmount().add(balance.getBlockedAmount()))
                .currencyCode(balance.getCurrencyCode())
                .lastUpdated(balance.getLastUpdated())
                .build();

        return AccountResponse.builder()
                .id(account.getId())
                .userId(userId)
                .iban(account.getIban())
                .accountNumber(account.getAccountNumber())
                .accountType(account.getAccountType())
                .status(account.getStatus())
                .openedAt(account.getOpenedAt())
                .closedAt(account.getClosedAt())
                .createdAt(account.getCreatedAt())
                .updatedAt(account.getUpdatedAt())
                .balance(balanceResponse)
                .build();
    }

}