package com.terraboxstudios.nanopay.storage;

import com.terraboxstudios.nanopay.Wallet;

import java.time.Duration;
import java.util.*;

public class MemoryWalletStorage implements WalletStorage {

    private final Set<Wallet> wallets;
    private final Duration duration;

    public MemoryWalletStorage(Duration walletExpiryTime) {
        this.wallets = Collections.synchronizedSet(new HashSet<>());
        this.duration = walletExpiryTime;
    }

    @Override
    public Collection<Wallet> getAllWallets() {
        return Collections.unmodifiableSet(wallets);
    }

    @Override
    public Optional<Wallet> findWalletByAddress(String address) {
        return wallets.stream().filter(wallet -> wallet.getAddress().equals(address)).findFirst();
    }

    @Override
    public void saveWallet(Wallet wallet) {
        wallets.add(wallet);
    }

    @Override
    public void deleteWallet(Wallet wallet) {
        wallets.remove(wallet);
    }

    @Override
    public Duration getWalletExpirationTime() {
        return duration;
    }

}
