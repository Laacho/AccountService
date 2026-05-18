package com.banking.account.accountLedger.service;

import com.banking.account.accountLedger.model.AccountLedgerEntry;
import com.banking.account.accountLedger.repository.AccountLedgerEntryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class AccountLedgerEntryService {

    private final AccountLedgerEntryRepository accountLedgerEntryRepository;

    @Autowired
    public AccountLedgerEntryService(AccountLedgerEntryRepository accountLedgerEntryRepository) {
        this.accountLedgerEntryRepository = accountLedgerEntryRepository;
    }


    public boolean findAlreadyExistingLedger(String idempotencyKey, UUID bankAccountId){
        return accountLedgerEntryRepository.existsByIdempotencyKeyAndBankAccountId(idempotencyKey, bankAccountId);
    }

    public Optional<AccountLedgerEntry> findByIdempotencyKeyAndBanAccountId(String idempotencyKey, UUID bankAccountId){
        return accountLedgerEntryRepository.findByIdempotencyKeyAndBankAccountId(idempotencyKey, bankAccountId);
    }


    @Transactional
    public void saveAll(List<AccountLedgerEntry> entries) {
         accountLedgerEntryRepository.saveAll(entries);
    }
}
