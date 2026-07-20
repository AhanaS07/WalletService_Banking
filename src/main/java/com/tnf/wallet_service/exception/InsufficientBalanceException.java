package com.tnf.wallet_service.exception;

// A debit exceeds the available balance.
public class InsufficientBalanceException extends RuntimeException {

    public InsufficientBalanceException(String message) {
        super(message);
    }
}
