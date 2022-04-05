package com.terraboxstudios.nanopay.storage;

import com.terraboxstudios.nanopay.Wallet;

import java.time.Duration;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

/**
 * This class is intended to work as a cache layer with storage solutions such as file storage and databases.
 * However, this class does not attempt to constantly refresh data from the backing wallet storage, it stores
 * all data in memory and simply mirrors all reads/writes to the backing wallet storage asynchronously
 * {@link ExecutorService} passed into the constructor. All 'find' or 'get' operations only access the cache.
 *
 * This class should <strong>not</strong> be used with databases or file systems which are externally modified during
 * the execution of the program - external data changes will not be reflected in the cache.
 */
public class CacheWrappedWalletStorage implements WalletStorage {

    private final WalletStorage cache;
    private final WalletStorage backingStorage;
    private final ExecutorService backingOperationService;

    public CacheWrappedWalletStorage(WalletStorage backingStorage, ExecutorService backingOperationService) {
        this.cache = new MemoryWalletStorage(backingStorage.getWalletExpirationTime());
        this.backingStorage = backingStorage;
        this.backingOperationService = backingOperationService;
        backingStorage.getAllWallets().forEach(cache::saveWallet);
    }

    @Override
    public Collection<Wallet> getAllWallets() {
        return cache.getAllWallets();
    }

    @Override
    public Optional<Wallet> findWalletByAddress(String address) {
        return cache.findWalletByAddress(address);
    }

    @Override
    public void saveWallet(Wallet wallet) {
        cache.saveWallet(wallet);
        backingOperationService.submit(() -> backingStorage.saveWallet(wallet));
    }

    @Override
    public void deleteWallet(Wallet wallet) {
        cache.deleteWallet(wallet);
        backingOperationService.submit(() -> backingStorage.deleteWallet(wallet));
    }

    @Override
    public Duration getWalletExpirationTime() {
        return backingStorage.getWalletExpirationTime();
    }

}
