package com.banking.account.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

// ─── Currency Mismatch ────────────────────────────────────────────────────────
@ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
public class CurrencyMismatchException extends RuntimeException {

    public CurrencyMismatchException(String message) {
        super(message);
    }

}
