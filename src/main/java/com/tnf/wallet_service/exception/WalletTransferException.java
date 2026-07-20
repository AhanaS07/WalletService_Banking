package com.tnf.wallet_service.exception;

// Thrown when a wallet-to-wallet transfer cannot be completed atomically.
// isReconciled=true  -> the debit was rolled back; no money moved (safe to retry).
// isReconciled=false -> CRITICAL: rollback also failed; wallets may be inconsistent
//                       and require manual reconciliation.
public class WalletTransferException extends RuntimeException {

    private final boolean reconciled;

    public WalletTransferException(String message, boolean reconciled, Throwable cause) {
        super(message, cause);
        this.reconciled = reconciled;
    }

    public boolean isReconciled() {
        return reconciled;
    }
}
