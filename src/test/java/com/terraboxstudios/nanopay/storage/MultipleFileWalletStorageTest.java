package com.terraboxstudios.nanopay.storage;

import com.terraboxstudios.nanopay.wallet.Wallet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class MultipleFileWalletStorageTest {

    private void storeWallet(Path storageFolder, Wallet wallet) throws IOException {
        Path walletPath = storageFolder.resolve(wallet.getAddress());
        Files.write(walletPath, walletToJson(wallet).getBytes(StandardCharsets.UTF_8));
    }

    private String walletToJson(Wallet wallet) {
        return "{\n" +
                "  \"address\": \"" + wallet.getAddress() + "\",\n" +
                "  \"private_key\": \"" + wallet.getPrivateKey() + "\",\n" +
                "  \"creation_time\": " + wallet.getCreationTime().toEpochMilli() + ",\n" +
                "  \"required_amount\": " + wallet.getRequiredAmount().toString() + "\n" +
                "}";
    }

    @Test
    void failGetAllWallets(@TempDir Path storageFolder) throws IOException {
        MultipleFileWalletStorage walletStorage = new MultipleFileWalletStorage(storageFolder, Duration.ofMinutes(10));
        Collection<Wallet> foundWallets = walletStorage.getAllWallets();
        assertIterableEquals(Collections.emptyList(), foundWallets);
    }

    @Test
    void getAllWallets(@TempDir Path storageFolder) throws IOException {
        Wallet firstWallet = new Wallet("nano_18xbfx1czna9178ah7gkyg6ukrdg919ebn9xt7j6fkq31kh4qwia4r3i7674",
                "B18852DAB11E34B4C0BEE3C53FCABF75560791E13EC7A5D5F9B7670277DD4643",
                Instant.ofEpochMilli(1649247684032L),
                new BigDecimal("0.1")
        );
        storeWallet(storageFolder, firstWallet);
        Wallet secondWallet = new Wallet("nano_3texgo63bs89jhtj4f6fn51nmsbh899nyfxxt51k66o8umhb931dz4bf9eto",
                "6859580360BA769E3FFAF0260A65ECF0A509715CC4964454A42699D7BE571870",
                Instant.ofEpochMilli(1649281447828L),
                new BigDecimal("1.2")
        );
        storeWallet(storageFolder, secondWallet);

        MultipleFileWalletStorage walletStorage = new MultipleFileWalletStorage(storageFolder, Duration.ofMinutes(10));
        Collection<Wallet> foundWallets = walletStorage.getAllWallets();

        Collection<Wallet> expectedWallets = Arrays.asList(firstWallet, secondWallet);
        assertIterableEquals(expectedWallets, foundWallets);
    }

    @Test
    void findWalletByAddress(@TempDir Path storageFolder) throws IOException {
        Wallet firstWallet = new Wallet("nano_18xbfx1czna9178ah7gkyg6ukrdg919ebn9xt7j6fkq31kh4qwia4r3i7674",
                "B18852DAB11E34B4C0BEE3C53FCABF75560791E13EC7A5D5F9B7670277DD4643",
                Instant.ofEpochMilli(1649247684032L),
                new BigDecimal("0.1")
        );
        storeWallet(storageFolder, firstWallet);
        Wallet secondWallet = new Wallet("nano_3texgo63bs89jhtj4f6fn51nmsbh899nyfxxt51k66o8umhb931dz4bf9eto",
                "6859580360BA769E3FFAF0260A65ECF0A509715CC4964454A42699D7BE571870",
                Instant.ofEpochMilli(1649281447828L),
                new BigDecimal("1.2")
        );
        storeWallet(storageFolder, secondWallet);

        MultipleFileWalletStorage walletStorage = new MultipleFileWalletStorage(storageFolder, Duration.ofMinutes(10));
        Optional<Wallet> foundWallet = walletStorage.findWalletByAddress("nano_3texgo63bs89jhtj4f6fn51nmsbh899nyfxxt51k66o8umhb931dz4bf9eto");

        assertTrue(foundWallet.isPresent());
        assertEquals(secondWallet, foundWallet.get());
    }

    @Test
    void failFindWalletByAddress(@TempDir Path storageFolder) throws IOException {
        Wallet firstWallet = new Wallet("nano_18xbfx1czna9178ah7gkyg6ukrdg919ebn9xt7j6fkq31kh4qwia4r3i7674",
                "B18852DAB11E34B4C0BEE3C53FCABF75560791E13EC7A5D5F9B7670277DD4643",
                Instant.ofEpochMilli(1649247684032L),
                new BigDecimal("0.1")
        );
        storeWallet(storageFolder, firstWallet);
        Wallet secondWallet = new Wallet("nano_3texgo63bs89jhtj4f6fn51nmsbh899nyfxxt51k66o8umhb931dz4bf9eto",
                "6859580360BA769E3FFAF0260A65ECF0A509715CC4964454A42699D7BE571870",
                Instant.ofEpochMilli(1649281447828L),
                new BigDecimal("1.2")
        );
        storeWallet(storageFolder, secondWallet);

        MultipleFileWalletStorage walletStorage = new MultipleFileWalletStorage(storageFolder, Duration.ofMinutes(10));
        Optional<Wallet> foundWallet = walletStorage.findWalletByAddress("nano_3nafw1z91qhiuadtetwiukao999dthpahy8pdxn19ghxsh7wfcote5skm894");

        assertFalse(foundWallet.isPresent());
    }

    @Test
    void saveWallet(@TempDir Path storageFolder) throws IOException {
        Wallet wallet = new Wallet("nano_18xbfx1czna9178ah7gkyg6ukrdg919ebn9xt7j6fkq31kh4qwia4r3i7674",
                "B18852DAB11E34B4C0BEE3C53FCABF75560791E13EC7A5D5F9B7670277DD4643",
                Instant.ofEpochMilli(1649247684032L),
                new BigDecimal("0.1")
        );
        Path walletPath = storageFolder.resolve(wallet.getAddress());
        assertFalse(Files.exists(walletPath));

        MultipleFileWalletStorage walletStorage = new MultipleFileWalletStorage(storageFolder, Duration.ofMinutes(10));
        walletStorage.saveWallet(wallet);

        assertTrue(Files.exists(walletPath));
        String expectedWalletJson = walletToJson(wallet);
        String foundWalletJson = new String(Files.readAllBytes(walletPath), StandardCharsets.UTF_8);
        assertEquals(expectedWalletJson, foundWalletJson);
    }

    @Test
    void deleteWallet(@TempDir Path storageFolder) throws IOException {
        MultipleFileWalletStorage walletStorage = new MultipleFileWalletStorage(storageFolder, Duration.ofMinutes(10));

        Wallet wallet = new Wallet("nano_18xbfx1czna9178ah7gkyg6ukrdg919ebn9xt7j6fkq31kh4qwia4r3i7674",
                "B18852DAB11E34B4C0BEE3C53FCABF75560791E13EC7A5D5F9B7670277DD4643",
                Instant.ofEpochMilli(1649247684032L),
                new BigDecimal("0.1")
        );
        storeWallet(storageFolder, wallet);

        Path walletPath = storageFolder.resolve(wallet.getAddress());
        assertTrue(Files.exists(walletPath));
        walletStorage.deleteWallet(wallet);
        assertFalse(Files.exists(walletPath));
    }

}