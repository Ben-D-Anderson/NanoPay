package com.terraboxstudios.nanopay.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.terraboxstudios.nanopay.NanoPay;
import com.terraboxstudios.nanopay.wallet.Wallet;
import com.terraboxstudios.nanopay.wallet.WalletGsonAdapter;
import uk.oczadly.karl.jnano.model.NanoAccount;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * All operations have a O(1) time complexity apart from {@link MultipleFileWalletStorage#getAllWallets} which is O(n)
 */
public class MultipleFileWalletStorage implements WalletStorage {

    private final Path storageFolder;
    private final Duration walletExpiryTime;
    private final Gson gson;

    /**
     * @param storageFolder Path to the wallet storage directory
     * @param walletExpiryTime amount of time a wallet can stay in this wallet storage
     * @throws IOException If an IOException was thrown when creating the wallet storage directory if it
     * didn't already exist.
     * @throws IllegalArgumentException If the {@code storageFolder} argument provided resolves to a file
     * instead of a folder.
     */
    public MultipleFileWalletStorage(Path storageFolder, Duration walletExpiryTime) throws IOException {
        this.storageFolder = storageFolder;
        this.walletExpiryTime = walletExpiryTime;

        if (!Files.exists(this.storageFolder)) {
            Files.createDirectories(this.storageFolder);
        }
        if (!Files.isDirectory(this.storageFolder)) {
            throw new IllegalArgumentException("Storage folder path provided resolved to a file, not a folder.");
        }

        GsonBuilder builder = new GsonBuilder();
        builder.registerTypeAdapter(Wallet.class, new WalletGsonAdapter());
        builder.setPrettyPrinting();
        this.gson = builder.create();
    }

    @Override
    public Collection<Wallet> getAllWallets() {
        try (Stream<Path> directoryContents = Files.list(storageFolder)) {
            return directoryContents.filter(item -> !Files.isDirectory(item))
                    .filter(file -> NanoAccount.isValidNano(file.getFileName().toString()))
                    .map(walletPath -> {
                        try {
                            String json = new String(Files.readAllBytes(walletPath), UTF_8);
                            return gson.fromJson(json, Wallet.class);
                        } catch (IOException e) {
                            NanoPay.LOGGER.error("Could not open wallet file '" + walletPath + "'", e);
                        }
                        return null;
                    }).filter(Objects::nonNull)
                    .collect(Collectors.toSet());
        } catch (IOException e) {
            NanoPay.LOGGER.error("Could not list files in wallet storage folder", e);
        }
        return Collections.emptySet();
    }

    @Override
    public Optional<Wallet> findWalletByAddress(String address) {
        Path walletPath = storageFolder.resolve(address);
        if (!Files.exists(walletPath)) return Optional.empty();
        try {
            String json = new String(Files.readAllBytes(walletPath), StandardCharsets.UTF_8);
            return Optional.of(gson.fromJson(json, Wallet.class));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    @Override
    public void saveWallet(Wallet wallet) {
        Path walletPath = storageFolder.resolve(wallet.getAddress());
        try {
            Files.write(walletPath, gson.toJson(wallet, Wallet.class).getBytes(UTF_8));
        } catch (IOException e) {
            NanoPay.LOGGER.error("Could not save wallet file in wallet storage folder", e);
        }
    }

    @Override
    public void deleteWallet(Wallet wallet) {
        Path walletPath = storageFolder.resolve(wallet.getAddress());
        if (!Files.exists(walletPath)) {
            NanoPay.LOGGER.warn("Received request to delete wallet file that doesn't exist from wallet storage ('" + wallet.getAddress() + "')");
        } else {
            try {
                Files.deleteIfExists(walletPath);
            } catch (IOException e) {
                NanoPay.LOGGER.error("IO exception occurred deleting wallet file from wallet storage ('" + wallet.getAddress() + "')");
            }
        }
    }

    @Override
    public Duration getWalletExpirationTime() {
        return walletExpiryTime;
    }

}
