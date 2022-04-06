package com.terraboxstudios.nanopay.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.terraboxstudios.nanopay.NanoPay;
import com.terraboxstudios.nanopay.wallet.Wallet;
import com.terraboxstudios.nanopay.wallet.WalletGsonAdapter;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.Semaphore;

/**
 * Operations have an O(n) time complexity, it is recommended that you wrap this class with CacheWrappedWalletStorage
 */
public class SingleFileWalletStorage implements WalletStorage {

    private final Path storageFile;
    private final Duration walletExpiryTime;
    private final Gson gson;
    private final Semaphore semaphore;

    /**
     * @param storageFile Path to the wallet storage file
     * @param walletExpiryTime amount of time a wallet can stay in this wallet storage
     * @throws IOException If an IOException was thrown when creating the wallet storage file if it
     * didn't already exist.
     * @throws IllegalArgumentException If the {@code storageFile} argument provided resolves to a folder
     * instead of a file.
     */
    public SingleFileWalletStorage(Path storageFile, Duration walletExpiryTime) throws IOException {
        this.storageFile = storageFile;
        this.walletExpiryTime = walletExpiryTime;
        this.semaphore = new Semaphore(1);

        if (!Files.exists(this.storageFile)) {
            Files.createFile(this.storageFile);
        }
        if (Files.isDirectory(this.storageFile)) {
            throw new IllegalArgumentException("Storage file path provided resolved to a folder, not a file.");
        }

        GsonBuilder builder = new GsonBuilder();
        builder.registerTypeAdapter(Wallet.class, new WalletGsonAdapter());
        this.gson = builder.create();
    }

    @Override
    public Collection<Wallet> getAllWallets() {
        Collection<Wallet> walletCollection;
        try {
            semaphore.acquire();
        } catch (InterruptedException ignored) {}
        try (BufferedReader reader = Files.newBufferedReader(storageFile)) {
            Wallet[] wallets = gson.fromJson(reader, Wallet[].class);
            walletCollection = Arrays.asList(wallets);
        } catch (IOException e) {
            NanoPay.LOGGER.error("IO Exception occurred when opening wallet storage file", e);
            walletCollection = Collections.emptySet();
        }
        semaphore.release();
        return walletCollection;
    }

    @Override
    public Optional<Wallet> findWalletByAddress(String address) {
        Optional<Wallet> walletOptional;
        try {
            semaphore.acquire();
        } catch (InterruptedException ignored) {}
        try (BufferedReader reader = Files.newBufferedReader(storageFile)) {
            Wallet[] wallets = gson.fromJson(reader, Wallet[].class);
            walletOptional = Arrays.stream(wallets).filter(wallet -> wallet.getAddress().equals(address)).findFirst();
        } catch (IOException e) {
            NanoPay.LOGGER.error("IO Exception occurred when opening wallet storage file", e);
            walletOptional = Optional.empty();
        }
        semaphore.release();
        return walletOptional;
    }

    @Override
    public void saveWallet(Wallet wallet) {
        Collection<Wallet> wallets = new HashSet<>(getAllWallets());
        wallets.add(wallet);
        save(wallets);
    }

    @Override
    public void deleteWallet(Wallet wallet) {
        Collection<Wallet> wallets = new HashSet<>(getAllWallets());
        wallets.remove(wallet);
        save(wallets);
    }

    private void save(Collection<Wallet> wallets) {
        try {
            semaphore.acquire();
        } catch (InterruptedException ignored) {}
        try {
            Files.write(storageFile, gson.toJson(wallets.toArray()).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            NanoPay.LOGGER.error("IO Exception occurred when saving the wallet storage file", e);
        }
        semaphore.release();
    }

    @Override
    public Duration getWalletExpirationTime() {
        return walletExpiryTime;
    }

}
