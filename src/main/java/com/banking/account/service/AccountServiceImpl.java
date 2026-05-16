package com.banking.account.service;

import com.banking.account.accountStatement.model.AccountStatement;
import com.banking.account.accountStatement.service.AccountStatementService;
import com.banking.account.balance.model.Balance;
import com.banking.account.bankAccount.model.AccountStatus;
import com.banking.account.bankAccount.model.BankAccount;
import com.banking.account.bankAccount.repository.BankAccountRepository;
import com.banking.account.bankBranch.service.BankBranchService;
import com.banking.account.dto.request.*;
import com.banking.account.dto.response.*;
import com.banking.account.exception.AccountAccessDeniedException;
import com.banking.account.exception.AccountNotFoundException;
import com.banking.account.exception.InvalidAccountStateException;
import com.banking.account.feing.service.UserClientService;
import com.banking.account.util.AccountNumberGenerator;
import com.banking.account.util.AccountSpecification;
import com.banking.account.util.BankConstants;
import com.banking.account.util.IbanGenerator;
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

@Service
public class AccountServiceImpl implements AccountService {

    private final BankBranchService bankBranchService;
    private final BankAccountRepository bankAccountRepository;
    private final UserClientService userClientService;
    private final AccountStatementService accountStatementService;

    @Autowired
    public AccountServiceImpl(BankBranchService bankBranchService,
                              BankAccountRepository bankAccountRepository, UserClientService userClientService, AccountStatementService accountStatementService) {
        this.bankBranchService = bankBranchService;
        this.bankAccountRepository = bankAccountRepository;
        this.userClientService = userClientService;
        this.accountStatementService = accountStatementService;
    }

    @Override
    @Transactional
    public AccountResponse createAccount(UUID userId, CreateAccountRequest request) {
        userClientService.validateUserExists(userId);
        AccountNumberGenerator accountGen = new AccountNumberGenerator();
        String accountNumber = accountGen.generate(request.accountType().getCode());

        String branchCode = bankBranchService.findCodeByName(request.branchName());
        String iban = IbanGenerator.generateIban(
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
        return null;
    }

    @Override
    public BalanceResponse getBalanceForInternalUse(UUID accountId) {
        return null;
    }

    @Override
    public TransferResultResponse executeTransfer(InternalTransferRequest request) {
        return null;
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
