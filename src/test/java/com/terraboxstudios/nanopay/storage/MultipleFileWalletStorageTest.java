package com.terraboxstudios.nanopay.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.terraboxstudios.nanopay.CustomAssertions;
import com.terraboxstudios.nanopay.wallet.Wallet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class MultipleFileWalletStorageTest {

    private final Wallet testWalletOne = new Wallet("nano_18xbfx1czna9178ah7gkyg6ukrdg919ebn9xt7j6fkq31kh4qwia4r3i7674",
            "B18852DAB11E34B4C0BEE3C53FCABF75560791E13EC7A5D5F9B7670277DD4643",
            Instant.ofEpochMilli(1649247684032L),
            new BigDecimal("0.1")
    );
    private final Wallet testWalletTwo = new Wallet("nano_3texgo63bs89jhtj4f6fn51nmsbh899nyfxxt51k66o8umhb931dz4bf9eto",
            "6859580360BA769E3FFAF0260A65ECF0A509715CC4964454A42699D7BE571870",
            Instant.ofEpochMilli(1649281447828L),
            new BigDecimal("1.2")
    );
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private void storeWallet(Path storageFolder, Wallet wallet) throws IOException {
        Path walletPath = storageFolder.resolve(wallet.address());
        Files.writeString(walletPath, gson.toJson(wallet));
    }

    @Test
    void failGetAllWallets(@TempDir Path storageFolder) throws IOException {
        MultipleFileWalletStorage walletStorage = new MultipleFileWalletStorage(storageFolder, Duration.ofMinutes(10));
        Collection<Wallet> foundWallets = walletStorage.getAllWallets();
        CustomAssertions.assertUnorderedCollectionEquals(Collections.emptyList(), foundWallets);
    }

    @Test
    void getAllWallets(@TempDir Path storageFolder) throws IOException {
        storeWallet(storageFolder, testWalletOne);
        storeWallet(storageFolder, testWalletTwo);

        MultipleFileWalletStorage walletStorage = new MultipleFileWalletStorage(storageFolder, Duration.ofMinutes(10));
        Collection<Wallet> foundWallets = walletStorage.getAllWallets();

        Collection<Wallet> expectedWallets = Arrays.asList(testWalletOne, testWalletTwo);
        CustomAssertions.assertUnorderedCollectionEquals(expectedWallets, foundWallets);
    }

    @Test
    void failFindWalletByAddress(@TempDir Path storageFolder) throws IOException {
        storeWallet(storageFolder, testWalletOne);
        storeWallet(storageFolder, testWalletTwo);

        MultipleFileWalletStorage walletStorage = new MultipleFileWalletStorage(storageFolder, Duration.ofMinutes(10));
        Optional<Wallet> foundWallet = walletStorage.findWalletByAddress("nano_3nafw1z91qhiuadtetwiukao999dthpahy8pdxn19ghxsh7wfcote5skm894");

        assertFalse(foundWallet.isPresent());
    }

    @Test
    void findWalletByAddress(@TempDir Path storageFolder) throws IOException {
        storeWallet(storageFolder, testWalletOne);
        storeWallet(storageFolder, testWalletTwo);

        MultipleFileWalletStorage walletStorage = new MultipleFileWalletStorage(storageFolder, Duration.ofMinutes(10));
        Optional<Wallet> foundWallet = walletStorage.findWalletByAddress(testWalletTwo.address());

        assertTrue(foundWallet.isPresent());
        assertEquals(testWalletTwo, foundWallet.get());
    }

    @Test
    void saveWallet(@TempDir Path storageFolder) throws IOException {
        Path walletPath = storageFolder.resolve(testWalletOne.address());
        assertFalse(Files.exists(walletPath));

        MultipleFileWalletStorage walletStorage = new MultipleFileWalletStorage(storageFolder, Duration.ofMinutes(10));
        walletStorage.saveWallet(testWalletOne);

        assertTrue(Files.exists(walletPath));
        String expectedWalletJson = gson.toJson(testWalletOne);
        String foundWalletJson = Files.readString(walletPath);
        assertEquals(expectedWalletJson, foundWalletJson);
    }

    @Test
    void deleteWallet(@TempDir Path storageFolder) throws IOException {
        MultipleFileWalletStorage walletStorage = new MultipleFileWalletStorage(storageFolder, Duration.ofMinutes(10));

        storeWallet(storageFolder, testWalletOne);

        Path walletPath = storageFolder.resolve(testWalletOne.address());
        assertTrue(Files.exists(walletPath));
        walletStorage.deleteWallet(testWalletOne);
        assertFalse(Files.exists(walletPath));
    }

}