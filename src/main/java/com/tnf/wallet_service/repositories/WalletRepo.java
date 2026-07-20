package com.tnf.wallet_service.repositories;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.tnf.wallet_service.entity.Wallet;

public interface WalletRepo extends MongoRepository<Wallet, String>{
    // Automatically implemented methods :
    // - save()
    // - findById()
    // - findAll()
    // - existsById()
    // - deleteById()
    // - count()

    List<Wallet> findByCustomerId(String customerId);
}
