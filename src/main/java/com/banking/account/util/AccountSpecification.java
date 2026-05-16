package com.banking.account.util;


import com.banking.account.bankAccount.model.BankAccount;
import com.banking.account.dto.request.AccountFilterRequest;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.criteria.Predicate;

/**
 * class used in AccountServiceImpl.listAccounts to build the query for filtering accounts
 * based on the provided criteria in AccountFilterRequest
 */
public class AccountSpecification {

    public static Specification<BankAccount> withFilters(AccountFilterRequest filters) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filters.status() != null) {
                predicates.add(cb.equal(root.get("status"), filters.status()));
            }
            if (filters.currency() != null) {
                predicates.add(cb.equal(root.join("balance").get("currencyCode"), filters.currency()));
            }
            if (filters.accountType() != null) {
                predicates.add(cb.equal(root.get("accountType"), filters.accountType()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
