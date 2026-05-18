package com.banking.account.bankAccount.repository;

import com.banking.account.bankAccount.model.BankAccount;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface BankAccountRepository extends JpaRepository<BankAccount, UUID>, JpaSpecificationExecutor<BankAccount> {

    /**
     * Loads an account with a database-level write lock — translates to
     * {@code SELECT ... FOR UPDATE} on MySQL/PostgreSQL. The row is locked
     * for any other transaction until this transaction commits or rolls back.
     *
     * <p><strong>Must be called inside a {@link org.springframework.transaction.annotation.Transactional}
     * method.</strong> Outside a transaction, the lock is released immediately
     * after the query, which defeats the purpose.</p>
     *
     * <p>Used by {@code executeTransfer} to prevent two concurrent transfers
     * from reading the same balance, both passing the sufficient-funds check,
     * and both debiting — the classic double-spend race condition.</p>
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM BankAccount a WHERE a.id = :id")
    Optional<BankAccount> findByIdForUpdate(UUID id);


}
