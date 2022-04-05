package com.terraboxstudios.nanopay.storage;

import com.terraboxstudios.nanopay.Wallet;

import java.time.Duration;
import java.util.Collection;
import java.util.Optional;

/**
 * An object provide storage functionality for {@link Wallet}(s). All {@link WalletStorage} implementations should
 * be thread-safe (fully functional when used by multiple threads simultaneously).
 */
public interface WalletStorage {

    Collection<Wallet> getAllWallets();

    Optional<Wallet> findWalletByAddress(String address);

    void saveWallet(Wallet wallet);

    void deleteWallet(Wallet wallet);

    Duration getWalletExpirationTime();

}
