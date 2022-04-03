package com.terraboxstudios.nanopay.storage;

import com.terraboxstudios.nanopay.Wallet;

import java.time.temporal.TemporalAmount;
import java.util.Collection;
import java.util.Optional;

public interface WalletStorage {

    Collection<Wallet> getAllWallets();

    Optional<Wallet> getWallet(String address);

    void storeWallet(Wallet wallet);

    void deleteWallet(Wallet wallet);

    TemporalAmount getWalletExpirationTime();

}
