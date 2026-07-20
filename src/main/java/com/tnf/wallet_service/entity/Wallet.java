package com.tnf.wallet_service.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "wallets")
public class Wallet {

    @Id
    private String walletId;
    private String customerId;

    // PAYTM or PHONEPE 
    private WalletType walletType;
    private BigDecimal balance;

    // Daily-spend tracking 
    private LocalDate dailySpendDate;
    private BigDecimal dailySpendTotal;

// Constructors
    public Wallet() {
    }

    public Wallet(String walletId, String customerId, WalletType walletType, BigDecimal balance,
            LocalDate dailySpendDate, BigDecimal dailySpendTotal) {
        this.walletId = walletId;
        this.customerId = customerId;
        this.walletType = walletType;
        this.balance = balance;
        this.dailySpendDate = dailySpendDate;
        this.dailySpendTotal = dailySpendTotal;
    }
// Getters and Setters
    public String getWalletId() {
        return walletId;
    }

    public void setWalletId(String walletId) {
        this.walletId = walletId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public WalletType getWalletType() {
        return walletType;
    }

    public void setWalletType(WalletType walletType) {
        this.walletType = walletType;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public LocalDate getDailySpendDate() {
        return dailySpendDate;
    }

    public void setDailySpendDate(LocalDate dailySpendDate) {
        this.dailySpendDate = dailySpendDate;
    }

    public BigDecimal getDailySpendTotal() {
        return dailySpendTotal;
    }

    public void setDailySpendTotal(BigDecimal dailySpendTotal) {
        this.dailySpendTotal = dailySpendTotal;
    }

    @Override
    public String toString() {
        return "Wallet [walletId=" + walletId + ", customerId=" + customerId + ", walletType=" + walletType
                + ", balance=" + balance + ", dailySpendDate=" + dailySpendDate + ", dailySpendTotal=" + dailySpendTotal
                + "]";
    }
    
}
