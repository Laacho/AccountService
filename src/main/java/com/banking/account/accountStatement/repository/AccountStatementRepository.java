package com.banking.account.accountStatement.repository;

import com.banking.account.accountStatement.model.AccountStatement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface AccountStatementRepository extends JpaRepository<AccountStatement, UUID> {

    @Query("SELECT s FROM AccountStatement s WHERE s.bankAccount.id = :accountId AND s.periodStart >= :from AND s.periodEnd <= :to")
    Page<AccountStatement> findByAccountAndDateRange(@Param("accountId") UUID accountId,
                                                     @Param("from") LocalDate from,
                                                     @Param("to") LocalDate to,
                                                     Pageable pageable);

    @Query("SELECT s FROM AccountStatement s WHERE s.bankAccount.id = :accountId AND s.periodStart >= :from AND s.periodEnd <= :to")
    List<AccountStatement> findByAccountAndDateRange(@Param("accountId") UUID accountId,
                                                    @Param("from") LocalDate from,
                                                    @Param("to") LocalDate to);

    boolean existsByBankAccountIdAndPeriodStartAndPeriodEnd(UUID accountId, LocalDate periodStart, LocalDate periodEnd);

}
