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
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class SingleFileWalletStorageTest {

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

    @Test
    void createStorageFile(@TempDir Path tempFolder) throws IOException {
        Path storageFile = tempFolder.resolve("wallet-file-storage.json");
        assertFalse(Files.exists(storageFile));
        new SingleFileWalletStorage(storageFile, Duration.ofMinutes(10));
        assertTrue(Files.exists(storageFile));
        assertEquals("[]", Files.readString(storageFile));
    }

    @Test
    void failGetAllWallets(@TempDir Path tempFolder) throws IOException {
        Path storageFile = tempFolder.resolve("wallet-file-storage.json");
        String json = "[]";
        Files.writeString(storageFile, json);

        SingleFileWalletStorage walletStorage = new SingleFileWalletStorage(storageFile, Duration.ofMinutes(10));
        Collection<Wallet> foundWallets = walletStorage.getAllWallets();

        Collection<Wallet> expectedWallets = Collections.emptyList();
        CustomAssertions.assertUnorderedCollectionEquals(expectedWallets, foundWallets);
    }

    @Test
    void getAllWallets(@TempDir Path tempFolder) throws IOException {
        Path storageFile = tempFolder.resolve("wallet-file-storage.json");
        String json = gson.toJson(new Wallet[] {testWalletOne, testWalletTwo});
        Files.writeString(storageFile, json);

        SingleFileWalletStorage walletStorage = new SingleFileWalletStorage(storageFile, Duration.ofMinutes(10));
        Collection<Wallet> foundWallets = walletStorage.getAllWallets();

        CustomAssertions.assertUnorderedCollectionEquals(List.of(testWalletOne, testWalletTwo), foundWallets);
    }

    @Test
    void failFindWalletByAddress(@TempDir Path tempFolder) throws IOException {
        Path storageFile = tempFolder.resolve("wallet-file-storage.json");

        String json = gson.toJson(new Wallet[] {testWalletOne, testWalletTwo});
        Files.writeString(storageFile, json);

        SingleFileWalletStorage walletStorage = new SingleFileWalletStorage(storageFile, Duration.ofMinutes(10));
        Optional<Wallet> foundWallet = walletStorage.findWalletByAddress("nano_3nafw1z91qhiuadtetwiukao999dthpahy8pdxn19ghxsh7wfcote5skm894");

        assertFalse(foundWallet.isPresent());
    }

    @Test
    void findWalletByAddress(@TempDir Path tempFolder) throws IOException {
        Path storageFile = tempFolder.resolve("wallet-file-storage.json");

        String json = gson.toJson(new Wallet[] {testWalletOne, testWalletTwo});
        Files.writeString(storageFile, json);

        SingleFileWalletStorage walletStorage = new SingleFileWalletStorage(storageFile, Duration.ofMinutes(10));
        Optional<Wallet> foundWallet = walletStorage.findWalletByAddress(testWalletTwo.address());

        assertTrue(foundWallet.isPresent());
        assertEquals(testWalletTwo, foundWallet.get());
    }

    @Test
    void saveWalletAppend(@TempDir Path tempFolder) throws IOException {
        Path storageFile = tempFolder.resolve("wallet-file-storage.json");

        String json = gson.toJson(new Wallet[] {testWalletOne});
        Files.writeString(storageFile, json);

        SingleFileWalletStorage walletStorage = new SingleFileWalletStorage(storageFile, Duration.ofMinutes(10));
        walletStorage.saveWallet(testWalletTwo);

        String foundJson = Files.readString(storageFile);

        String firstPossibleExpectedJson = gson.toJson(new Wallet[] {testWalletOne, testWalletTwo});
        String secondPossibleExpectedJson = gson.toJson(new Wallet[] {testWalletTwo, testWalletOne});

        assertTrue(foundJson.equals(firstPossibleExpectedJson) || foundJson.equals(secondPossibleExpectedJson));
    }

    @Test
    void deleteWallet(@TempDir Path tempFolder) throws IOException {
        Path storageFile = tempFolder.resolve("wallet-file-storage.json");

        String json = gson.toJson(new Wallet[] {testWalletOne, testWalletTwo});
        Files.writeString(storageFile, json);

        SingleFileWalletStorage walletStorage = new SingleFileWalletStorage(storageFile, Duration.ofMinutes(10));
        walletStorage.deleteWallet(testWalletTwo);

        String expectedJson = gson.toJson(new Wallet[] {testWalletOne});
        String foundJson = Files.readString(storageFile);

        assertEquals(expectedJson, foundJson);
    }
}