package com.banking.account.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

// ─── Duplicate Idempotency Key ────────────────────────────────────────────────
@ResponseStatus(HttpStatus.CONFLICT)
public class DuplicateTransferException extends RuntimeException {

    public DuplicateTransferException(String message) {
        super(message);
    }

}
