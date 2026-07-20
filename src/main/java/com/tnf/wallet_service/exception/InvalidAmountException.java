package com.tnf.wallet_service.exception;

// A non-positive top-up/debit amount or an out-of-range opening balance.
public class InvalidAmountException extends RuntimeException {

    public InvalidAmountException(String message) {
        super(message);
    }
}
