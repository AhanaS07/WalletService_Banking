package com.tnf.wallet_service.exception;

// A top-up would exceed MAX_BALANCE or a debit would exceed the DAILY_LIMIT.
public class WalletLimitExceededException extends RuntimeException {

    public WalletLimitExceededException(String message) {
        super(message);
    }
}
