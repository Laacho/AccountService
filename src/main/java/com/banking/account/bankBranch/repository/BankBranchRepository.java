package com.banking.account.bankBranch.repository;

import com.banking.account.bankBranch.model.BankBranch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface BankBranchRepository extends JpaRepository<BankBranch, UUID> {
    Optional<BankBranch> findByBranchName(String name);

    @Query(value = "SELECT b.bank_code FROM bank_branch b WHERE b.branch_name = :branchName", nativeQuery = true)
    String findBankCodeByBranchName(String branchName);
}
