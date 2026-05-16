package com.banking.account.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

// ─── Access Denied (account belongs to a different user) ─────────────────────
@ResponseStatus(HttpStatus.FORBIDDEN)
public class AccountAccessDeniedException extends RuntimeException {

    public AccountAccessDeniedException(String message) {
        super(message);
    }

}
