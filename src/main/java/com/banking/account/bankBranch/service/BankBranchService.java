package com.banking.account.bankBranch.service;

import com.banking.account.aspect.Logged;
import com.banking.account.bankBranch.repository.BankBranchRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Logged
@Service
public class BankBranchService {

    private final BankBranchRepository bankBranchRepository;

    @Autowired
    public BankBranchService(BankBranchRepository bankBranchRepository) {
        this.bankBranchRepository = bankBranchRepository;
    }

    public String findCodeByName(String code) {
       return bankBranchRepository.findBankCodeByBranchName(code);
    }
}
