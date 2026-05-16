package com.banking.account.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

// ─── Invalid Account State ────────────────────────────────────────────────────
@ResponseStatus(HttpStatus.CONFLICT)
public class InvalidAccountStateException extends RuntimeException {
    public InvalidAccountStateException(String message) { super(message); }
}

