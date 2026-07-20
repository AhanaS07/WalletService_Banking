package com.tnf.wallet_service.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.tnf.wallet_service.entity.Wallet;
import com.tnf.wallet_service.entity.WalletType;
import com.tnf.wallet_service.exception.InsufficientBalanceException;
import com.tnf.wallet_service.exception.InvalidAmountException;
import com.tnf.wallet_service.exception.WalletLimitExceededException;
import com.tnf.wallet_service.exception.WalletNotFoundException;
import com.tnf.wallet_service.exception.WalletTransferException;
import com.tnf.wallet_service.repositories.WalletRepo;

@Service
public class WalletService {

    private static final Logger logger = LoggerFactory.getLogger(WalletService.class);

    // Business rules
    private static final BigDecimal MAX_BALANCE = new BigDecimal("50000");
    private static final BigDecimal DAILY_LIMIT = new BigDecimal("20000");

    private final WalletRepo walletRepo;

    public WalletService(WalletRepo walletRepo) {
        this.walletRepo = walletRepo;
    }

    public Wallet createWallet(String customerId, WalletType walletType, BigDecimal openingBalance) {
        logger.info("Creating {} wallet for customer: {}", walletType, customerId);

        BigDecimal opening = openingBalance != null ? openingBalance : BigDecimal.ZERO;
        if (opening.signum() < 0 || opening.compareTo(MAX_BALANCE) > 0) {
            logger.warn("Invalid opening balance {} for customer {}", opening, customerId);
            throw new InvalidAmountException("Invalid opening balance: " + opening);
        }

        Wallet wallet = new Wallet();
        wallet.setCustomerId(customerId);
        wallet.setWalletType(walletType);
        wallet.setBalance(opening);
        wallet.setDailySpendDate(LocalDate.now());
        wallet.setDailySpendTotal(BigDecimal.ZERO);

        Wallet saved = walletRepo.save(wallet);
        logger.info("Wallet created with ID: {}", saved.getWalletId());
        return saved;
    }

    public List<Wallet> getAllWallets() {
        logger.info("Fetching all wallets");
        List<Wallet> wallets = walletRepo.findAll();
        logger.debug("Retrieved {} wallets", wallets.size());
        return wallets;
    }

    public Wallet getWalletById(String walletId) {
        logger.info("Fetching wallet by ID: {}", walletId);
        return walletRepo.findById(walletId).orElseThrow(() -> {
            logger.warn("Wallet not found with ID: {}", walletId);
            return new WalletNotFoundException("Wallet not found with id: " + walletId);
        });
    }

    public List<Wallet> getWalletsByCustomerId(String customerId) {
        logger.info("Fetching wallets for customer: {}", customerId);
        List<Wallet> wallets = walletRepo.findByCustomerId(customerId);
        logger.debug("Retrieved {} wallets for customer {}", wallets.size(), customerId);
        return wallets;
    }

    public Wallet addMoney(String walletId, BigDecimal amount) {
        logger.info("Adding {} to wallet: {}", amount, walletId);
        requirePositive(amount, "Top-up amount must be positive: " + amount);

        Wallet wallet = getWalletById(walletId);
        BigDecimal newBalance = wallet.getBalance().add(amount);
        if (newBalance.compareTo(MAX_BALANCE) > 0) {
            logger.warn("Top-up on wallet {} would exceed MAX_BALANCE {}", walletId, MAX_BALANCE);
            throw new WalletLimitExceededException(
                    "Wallet " + walletId + " would exceed MAX_BALANCE " + MAX_BALANCE);
        }

        wallet.setBalance(newBalance);
        Wallet saved = walletRepo.save(wallet);
        logger.info("Wallet {} topped up; new balance: {}", walletId, saved.getBalance());
        return saved;
    }

    public Wallet payBill(String walletId, BigDecimal amount) {
        logger.info("Paying bill of {} from wallet: {}", amount, walletId);
        Wallet wallet = getWalletById(walletId);
        debit(wallet, amount);
        Wallet saved = walletRepo.save(wallet);
        logger.info("Bill paid from wallet {}; new balance: {}", walletId, saved.getBalance());
        return saved;
    }

    // A transfer is two writes (debit source, credit target). Standalone MongoDB has no
    // multi-document transactions, so we couple the writes with a compensating rollback:
    // persist the debit first, then the credit; if the credit fails, undo the debit.
    
    public Wallet transfer(String fromWalletId, String toWalletId, BigDecimal amount) {
        logger.info("Transferring {} from wallet {} to wallet {}", amount, fromWalletId, toWalletId);
        if (fromWalletId.equals(toWalletId)) {
            logger.warn("Attempted transfer to the same wallet: {}", fromWalletId);
            throw new InvalidAmountException("Cannot transfer to the same wallet");
        }

        Wallet source = getWalletById(fromWalletId);
        Wallet target = getWalletById(toWalletId);

        // Guard the credit side before mutating so both wallets stay consistent.
        if (target.getBalance().add(amount).compareTo(MAX_BALANCE) > 0) {
            logger.warn("Transfer would push target wallet {} over MAX_BALANCE {}", toWalletId, MAX_BALANCE);
            throw new WalletLimitExceededException(
                    "Wallet " + toWalletId + " would exceed MAX_BALANCE " + MAX_BALANCE);
        }

        // Snapshot the source's pre-debit state so we can roll back if the credit fails.
        Wallet sourceSnapshot = snapshot(source);

        // Step 1: debit the source and persist. If this throws, nothing has moved -> clean failure.
        debit(source, amount);
        Wallet savedSource = walletRepo.save(source);

        // Step 2: credit the target and persist. If this throws, compensate the debit above.
        try {
            target.setBalance(target.getBalance().add(amount));
            walletRepo.save(target);
        } catch (RuntimeException creditFailure) {
            logger.error("Credit to wallet {} failed after debiting wallet {}; rolling back the debit",
                    toWalletId, fromWalletId, creditFailure);
            try {
                restore(savedSource, sourceSnapshot);
                walletRepo.save(savedSource);
            } catch (RuntimeException rollbackFailure) {
                // Both the credit and its rollback failed: the source is now debited with no
                // matching credit. This cannot be auto-healed and needs manual reconciliation.
                logger.error("CRITICAL: rollback of debit on wallet {} failed; wallet balances are "
                        + "inconsistent and require manual reconciliation", fromWalletId, rollbackFailure);
                throw new WalletTransferException(
                        "Transfer failed and the debit could not be rolled back for wallet " + fromWalletId
                                + ". Manual reconciliation required.",
                        false, rollbackFailure);
            }
            throw new WalletTransferException(
                    "Transfer failed; the debit on wallet " + fromWalletId + " was rolled back. No money moved.",
                    true, creditFailure);
        }

        logger.info("Transfer complete; source wallet {} balance: {}", fromWalletId, savedSource.getBalance());
        return savedSource;
    }

    // Shared debit logic for payBill and the source side of a transfer.
    // positive-amount check, daily rollover, daily-limit check, balance check.
    private void debit(Wallet wallet, BigDecimal amount) {
        requirePositive(amount, "Debit amount must be positive: " + amount);
        rolloverDailyCounterIfNeeded(wallet);

        BigDecimal projectedSpend = wallet.getDailySpendTotal().add(amount);
        if (projectedSpend.compareTo(DAILY_LIMIT) > 0) {
            logger.warn("Debit on wallet {} would exceed DAILY_LIMIT {}", wallet.getWalletId(), DAILY_LIMIT);
            throw new WalletLimitExceededException(
                    "Wallet " + wallet.getWalletId() + " daily spend limit (" + DAILY_LIMIT + ") would be exceeded");
        }
        if (wallet.getBalance().compareTo(amount) < 0) {
            logger.warn("Wallet {} has insufficient balance {}", wallet.getWalletId(), wallet.getBalance());
            throw new InsufficientBalanceException(
                    "Wallet " + wallet.getWalletId() + " has insufficient balance (" + wallet.getBalance() + ")");
        }

        wallet.setBalance(wallet.getBalance().subtract(amount));
        wallet.setDailySpendTotal(projectedSpend);
    }

    // Resets the daily-spend counter when the stored date is not today.
    private void rolloverDailyCounterIfNeeded(Wallet wallet) {
        LocalDate today = LocalDate.now();
        if (!today.equals(wallet.getDailySpendDate())) {
            wallet.setDailySpendDate(today);
            wallet.setDailySpendTotal(BigDecimal.ZERO);
        }
    }

    private void requirePositive(BigDecimal amount, String message) {
        if (amount == null || amount.signum() <= 0) {
            throw new InvalidAmountException(message);
        }
    }

    // Captures the mutable balance/daily-spend state a debit changes, for compensating rollback.
    private Wallet snapshot(Wallet wallet) {
        Wallet copy = new Wallet();
        copy.setBalance(wallet.getBalance());
        copy.setDailySpendDate(wallet.getDailySpendDate());
        copy.setDailySpendTotal(wallet.getDailySpendTotal());
        return copy;
    }

    // Restores the fields captured by snapshot() onto the live wallet before re-saving it.
    private void restore(Wallet wallet, Wallet snapshot) {
        wallet.setBalance(snapshot.getBalance());
        wallet.setDailySpendDate(snapshot.getDailySpendDate());
        wallet.setDailySpendTotal(snapshot.getDailySpendTotal());
    }
}
