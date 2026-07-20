package com.tnf.wallet_service.exception;

// Thrown when a wallet lookup by id (or the source/target of a transfer) does not exist.
public class WalletNotFoundException extends RuntimeException {

    public WalletNotFoundException(String message) {
        super(message);
    }
}
