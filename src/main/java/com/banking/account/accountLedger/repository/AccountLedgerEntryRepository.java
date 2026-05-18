package com.banking.account.accountLedger.repository;

import com.banking.account.accountLedger.model.AccountLedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccountLedgerEntryRepository extends JpaRepository<AccountLedgerEntry, UUID> {


    boolean existsByIdempotencyKeyAndBankAccountId(String idempotencyKey, UUID bankAccount_id);


    Optional<AccountLedgerEntry> findByIdempotencyKeyAndBankAccountId(String idempotencyKey, UUID bankAccountId);

}
