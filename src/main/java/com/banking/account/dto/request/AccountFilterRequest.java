package com.banking.account.dto.request;

import com.banking.account.bankAccount.model.AccountStatus;
import com.banking.account.bankAccount.model.AccountType;

public record AccountFilterRequest(
        AccountStatus status,
        String currency,
        AccountType accountType
) {
}
