package xyz.benanderson.nanopay.storage;

import xyz.benanderson.nanopay.wallet.Wallet;

import java.time.Duration;
import java.util.*;

/**
 * All operations have a O(1) time complexity as this class internally uses a HashMap (with the key being the wallet address).
 */
public class MemoryWalletStorage implements WalletStorage {

    private final Map<String, Wallet> wallets;
    private final Duration duration;

    public MemoryWalletStorage(Duration walletExpiryTime) {
        this(new HashMap<>(), walletExpiryTime);
    }

    MemoryWalletStorage(Map<String, Wallet> walletsMap, Duration walletExpiryTime) {
        this.wallets = Collections.synchronizedMap(walletsMap);
        this.duration = walletExpiryTime;
    }

    @Override
    public Collection<Wallet> getAllWallets() {
        return wallets.values();
    }

    @Override
    public Optional<Wallet> findWalletByAddress(String address) {
        return Optional.ofNullable(wallets.get(address));
    }

    @Override
    public void saveWallet(Wallet wallet) {
        wallets.put(wallet.address(), wallet);
    }

    @Override
    public void deleteWallet(Wallet wallet) {
        wallets.remove(wallet.address());
    }

    @Override
    public Duration getWalletExpirationTime() {
        return duration;
    }

}
