package com.terraboxstudios.nanopay.storage;

import com.terraboxstudios.nanopay.Wallet;

import java.time.Duration;
import java.time.temporal.TemporalAmount;
import java.util.*;

public class MemoryWalletStorage implements WalletStorage {

    private final List<Wallet> wallets;
    private final Duration duration;

    public MemoryWalletStorage(Duration walletExpiryTime) {
        this.wallets = Collections.synchronizedList(new ArrayList<>());
        this.duration = walletExpiryTime;
    }

    @Override
    public Collection<Wallet> getAllWallets() {
        return wallets;
    }

    @Override
    public Optional<Wallet> getWallet(String address) {
        return wallets.stream().filter(wallet -> wallet.getAddress().equals(address)).findFirst();
    }

    @Override
    public void storeWallet(Wallet wallet) {
        wallets.add(wallet);
    }

    @Override
    public void deleteWallet(Wallet wallet) {
        wallets.remove(wallet);
    }

    @Override
    public TemporalAmount getWalletExpirationTime() {
        return duration;
    }

}
