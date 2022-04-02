package com.terraboxstudios.nanopay.storage;

import com.terraboxstudios.nanopay.Wallet;

import java.time.Duration;
import java.time.temporal.TemporalAmount;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public class MemoryWalletStorageProvider implements WalletStorageProvider {

    private final List<Wallet> wallets;

    public MemoryWalletStorageProvider() {
        this.wallets = new ArrayList<>();
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

    private final Duration duration = Duration.ofMinutes(15);

    @Override
    public TemporalAmount getWalletExpirationTime() {
        return duration;
    }

}
