package com.terraboxstudios.nanopay.storage;

import com.terraboxstudios.nanopay.wallet.Wallet;
import lombok.Getter;

import java.time.Duration;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
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
    @Getter
    private final CacheSearchPolicy cachePolicy;

    public CacheWrappedWalletStorage(WalletStorage backingStorage, ExecutorService backingOperationService) {
        this(backingStorage, backingOperationService, CacheSearchPolicy.CACHE_ONLY);
    }

    public CacheWrappedWalletStorage(WalletStorage backingStorage,
                                     ExecutorService backingOperationService,
                                     CacheSearchPolicy cachePolicy) {
        this.cache = new MemoryWalletStorage(backingStorage.getWalletExpirationTime());
        this.backingStorage = backingStorage;
        this.backingOperationService = backingOperationService;
        this.cachePolicy = cachePolicy;
        backingStorage.getAllWallets().forEach(cache::saveWallet);
    }

    public enum CacheSearchPolicy {
        /**
         * Only search the cache for data and never check the backing wallet storage.
         */
        CACHE_ONLY,
        /**
         * Initially search the cache for data, if data is not in the cache then fall
         * back to the backing wallet storage
         */
        BACKING_IF_MISS
    }

    @Override
    public Collection<Wallet> getAllWallets() {
        return cache.getAllWallets();
    }

    @Override
    public Optional<Wallet> findWalletByAddress(String address) {
        Optional<Wallet> walletOptional = cache.findWalletByAddress(address);
        if (walletOptional.isPresent()) return walletOptional;
        return switch (cachePolicy) {
            case CACHE_ONLY -> walletOptional;
            case BACKING_IF_MISS -> {
                try {
                    yield backingOperationService.submit(() -> backingStorage.findWalletByAddress(address)).get();
                } catch (InterruptedException | ExecutionException ignored) {}
                yield Optional.empty();
            }
        };
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
