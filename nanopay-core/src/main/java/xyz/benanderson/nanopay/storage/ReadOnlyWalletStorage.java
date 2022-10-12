package xyz.benanderson.nanopay.storage;

import xyz.benanderson.nanopay.wallet.Wallet;
import lombok.AllArgsConstructor;

import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;

@SuppressWarnings("ClassCanBeRecord")
@AllArgsConstructor
public class ReadOnlyWalletStorage implements WalletStorage {

    private final WalletStorage walletStorage;

    @Override
    public Collection<Wallet> getAllWallets() {
        return Collections.unmodifiableCollection(walletStorage.getAllWallets());
    }

    @Override
    public Optional<Wallet> findWalletByAddress(String address) {
        return walletStorage.findWalletByAddress(address);
    }

    @Override
    public void saveWallet(Wallet wallet) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void deleteWallet(Wallet wallet) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Duration getWalletExpirationTime() {
        return walletStorage.getWalletExpirationTime();
    }

}
