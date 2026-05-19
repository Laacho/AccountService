package com.banking.account.accountStatement.service;

import com.banking.account.aspect.Logged;
import com.banking.account.accountStatement.model.AccountStatement;
import com.banking.account.accountStatement.repository.AccountStatementRepository;
import com.banking.account.dto.request.GenerateStatementRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Logged
@Service
public class AccountStatementService {

    private final AccountStatementRepository accountStatementRepository;

    public AccountStatementService(AccountStatementRepository accountStatementRepository) {
        this.accountStatementRepository = accountStatementRepository;
    }


    public  Page<AccountStatement> findByAccountIdWithFilterForDates(UUID id, LocalDate startDate, LocalDate endDate, Pageable pageable) {
       return accountStatementRepository.findByAccountAndDateRange(id, startDate, endDate, pageable);
    }

    public List<AccountStatement> findByAccountIdWithinRange(UUID accountId, GenerateStatementRequest request) {
        return accountStatementRepository.findByAccountAndDateRange(accountId, request.periodStart(), request.periodEnd());
    }

    public boolean existsByAccountAndPeriod(UUID accountId, LocalDate periodStart, LocalDate periodEnd) {
        return accountStatementRepository.existsByBankAccountIdAndPeriodStartAndPeriodEnd(accountId, periodStart, periodEnd);
    }

    public AccountStatement save(AccountStatement statement) {
        return accountStatementRepository.save(statement);
    }
}
