package com.terraboxstudios.nanopay.storage;

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

class SingleFileWalletStorageTest {

    @Test
    void createStorageFile(@TempDir Path tempFolder) throws IOException {
        Path storageFile = tempFolder.resolve("wallet-file-storage.json");
        assertFalse(Files.exists(storageFile));
        new SingleFileWalletStorage(storageFile, Duration.ofMinutes(10));
        assertTrue(Files.exists(storageFile));
        assertEquals("[]", Files.readString(storageFile));
    }

    @SuppressWarnings("SuspiciousMethodCalls")
    private <T> void assertUnorderedCollectionEquals(Collection<? super T> a, Collection<? super T> b) {
        assertEquals(a.size(), b.size());
        assertTrue(a.containsAll(b));
        assertTrue(b.containsAll(a));
    }

    @Test
    void failGetAllWallets(@TempDir Path tempFolder) throws IOException {
        Path storageFile = tempFolder.resolve("wallet-file-storage.json");
        String json = "[]";
        Files.writeString(storageFile, json);

        SingleFileWalletStorage walletStorage = new SingleFileWalletStorage(storageFile, Duration.ofMinutes(10));
        Collection<Wallet> foundWallets = walletStorage.getAllWallets();

        Collection<Wallet> expectedWallets = Collections.emptyList();
        assertUnorderedCollectionEquals(expectedWallets, foundWallets);
    }

    @Test
    void getAllWallets(@TempDir Path tempFolder) throws IOException {
        Path storageFile = tempFolder.resolve("wallet-file-storage.json");
        String json = """
                [
                  {
                    "address": "nano_18xbfx1czna9178ah7gkyg6ukrdg919ebn9xt7j6fkq31kh4qwia4r3i7674",
                    "private_key": "B18852DAB11E34B4C0BEE3C53FCABF75560791E13EC7A5D5F9B7670277DD4643",
                    "creation_time": 1649247684032,
                    "required_amount": 0.1
                  },
                  {
                    "address": "nano_3texgo63bs89jhtj4f6fn51nmsbh899nyfxxt51k66o8umhb931dz4bf9eto",
                    "private_key": "6859580360BA769E3FFAF0260A65ECF0A509715CC4964454A42699D7BE571870",
                    "creation_time": 1649281447828,
                    "required_amount": 1.2
                  }
                ]""";
        Files.writeString(storageFile, json);

        SingleFileWalletStorage walletStorage = new SingleFileWalletStorage(storageFile, Duration.ofMinutes(10));
        Collection<Wallet> foundWallets = walletStorage.getAllWallets();

        Collection<Wallet> expectedWallets = Arrays.asList(
                new Wallet("nano_18xbfx1czna9178ah7gkyg6ukrdg919ebn9xt7j6fkq31kh4qwia4r3i7674",
                        "B18852DAB11E34B4C0BEE3C53FCABF75560791E13EC7A5D5F9B7670277DD4643",
                        Instant.ofEpochMilli(1649247684032L),
                        new BigDecimal("0.1")
                ),
                new Wallet("nano_3texgo63bs89jhtj4f6fn51nmsbh899nyfxxt51k66o8umhb931dz4bf9eto",
                        "6859580360BA769E3FFAF0260A65ECF0A509715CC4964454A42699D7BE571870",
                        Instant.ofEpochMilli(1649281447828L),
                        new BigDecimal("1.2")
                )
        );

        assertUnorderedCollectionEquals(expectedWallets, foundWallets);
    }

    @Test
    void findWalletByAddress(@TempDir Path tempFolder) throws IOException {
        Path storageFile = tempFolder.resolve("wallet-file-storage.json");

        String json = """
                [
                  {
                    "address": "nano_18xbfx1czna9178ah7gkyg6ukrdg919ebn9xt7j6fkq31kh4qwia4r3i7674",
                    "private_key": "B18852DAB11E34B4C0BEE3C53FCABF75560791E13EC7A5D5F9B7670277DD4643",
                    "creation_time": 1649247684032,
                    "required_amount": 0.1
                  },
                  {
                    "address": "nano_3texgo63bs89jhtj4f6fn51nmsbh899nyfxxt51k66o8umhb931dz4bf9eto",
                    "private_key": "6859580360BA769E3FFAF0260A65ECF0A509715CC4964454A42699D7BE571870",
                    "creation_time": 1649281447828,
                    "required_amount": 1.2
                  }
                ]""";
        Files.writeString(storageFile, json);

        SingleFileWalletStorage walletStorage = new SingleFileWalletStorage(storageFile, Duration.ofMinutes(10));
        Optional<Wallet> foundWallet = walletStorage.findWalletByAddress("nano_3texgo63bs89jhtj4f6fn51nmsbh899nyfxxt51k66o8umhb931dz4bf9eto");

        Wallet expectedWallet = new Wallet("nano_3texgo63bs89jhtj4f6fn51nmsbh899nyfxxt51k66o8umhb931dz4bf9eto",
                "6859580360BA769E3FFAF0260A65ECF0A509715CC4964454A42699D7BE571870",
                Instant.ofEpochMilli(1649281447828L),
                new BigDecimal("1.2")
        );

        assertTrue(foundWallet.isPresent());
        assertEquals(expectedWallet, foundWallet.get());
    }

    @Test
    void failFindWalletByAddress(@TempDir Path tempFolder) throws IOException {
        Path storageFile = tempFolder.resolve("wallet-file-storage.json");

        String json = """
                [
                  {
                    "address": "nano_18xbfx1czna9178ah7gkyg6ukrdg919ebn9xt7j6fkq31kh4qwia4r3i7674",
                    "private_key": "B18852DAB11E34B4C0BEE3C53FCABF75560791E13EC7A5D5F9B7670277DD4643",
                    "creation_time": 1649247684032,
                    "required_amount": 0.1
                  },
                  {
                    "address": "nano_3texgo63bs89jhtj4f6fn51nmsbh899nyfxxt51k66o8umhb931dz4bf9eto",
                    "private_key": "6859580360BA769E3FFAF0260A65ECF0A509715CC4964454A42699D7BE571870",
                    "creation_time": 1649281447828,
                    "required_amount": 1.2
                  }
                ]""";
        Files.writeString(storageFile, json);

        SingleFileWalletStorage walletStorage = new SingleFileWalletStorage(storageFile, Duration.ofMinutes(10));
        Optional<Wallet> foundWallet = walletStorage.findWalletByAddress("nano_3nafw1z91qhiuadtetwiukao999dthpahy8pdxn19ghxsh7wfcote5skm894");

        assertFalse(foundWallet.isPresent());
    }

    @Test
    void saveWalletAppend(@TempDir Path tempFolder) throws IOException {
        Path storageFile = tempFolder.resolve("wallet-file-storage.json");

        String json = """
                [
                  {
                    "address": "nano_18xbfx1czna9178ah7gkyg6ukrdg919ebn9xt7j6fkq31kh4qwia4r3i7674",
                    "private_key": "B18852DAB11E34B4C0BEE3C53FCABF75560791E13EC7A5D5F9B7670277DD4643",
                    "creation_time": 1649247684032,
                    "required_amount": 0.1
                  }
                ]""";
        Files.writeString(storageFile, json);

        SingleFileWalletStorage walletStorage = new SingleFileWalletStorage(storageFile, Duration.ofMinutes(10));
        Wallet walletToAdd = new Wallet("nano_3nafw1z91qhiuadtetwiukao999dthpahy8pdxn19ghxsh7wfcote5skm894",
                "5A7D46F332615E6194BF843831531C3BC0EA8B26A15C3C98C15DAD51FCB721A2",
                Instant.ofEpochMilli(1649283452942L),
                new BigDecimal("0.05")
        );
        walletStorage.saveWallet(walletToAdd);

        String foundJson = Files.readString(storageFile);

        String expectedJson = """
                [
                  {
                    "address": "nano_18xbfx1czna9178ah7gkyg6ukrdg919ebn9xt7j6fkq31kh4qwia4r3i7674",
                    "private_key": "B18852DAB11E34B4C0BEE3C53FCABF75560791E13EC7A5D5F9B7670277DD4643",
                    "creation_time": 1649247684032,
                    "required_amount": 0.1
                  },
                  {
                    "address": "nano_3nafw1z91qhiuadtetwiukao999dthpahy8pdxn19ghxsh7wfcote5skm894",
                    "private_key": "5A7D46F332615E6194BF843831531C3BC0EA8B26A15C3C98C15DAD51FCB721A2",
                    "creation_time": 1649283452942,
                    "required_amount": 0.05
                  }
                ]""";

        assertEquals(expectedJson, foundJson);
    }

    @Test
    void deleteWallet(@TempDir Path tempFolder) throws IOException {
        Path storageFile = tempFolder.resolve("wallet-file-storage.json");

        Wallet walletToDelete = new Wallet("nano_3nafw1z91qhiuadtetwiukao999dthpahy8pdxn19ghxsh7wfcote5skm894",
                "5A7D46F332615E6194BF843831531C3BC0EA8B26A15C3C98C15DAD51FCB721A2",
                Instant.ofEpochMilli(1649283452942L),
                new BigDecimal("0.05")
        );
        String json = """
                [
                  {
                    "address": "nano_18xbfx1czna9178ah7gkyg6ukrdg919ebn9xt7j6fkq31kh4qwia4r3i7674",
                    "private_key": "B18852DAB11E34B4C0BEE3C53FCABF75560791E13EC7A5D5F9B7670277DD4643",
                    "creation_time": 1649247684032,
                    "required_amount": 0.1
                  },
                  {
                    "address": "nano_3nafw1z91qhiuadtetwiukao999dthpahy8pdxn19ghxsh7wfcote5skm894",
                    "private_key": "5A7D46F332615E6194BF843831531C3BC0EA8B26A15C3C98C15DAD51FCB721A2",
                    "creation_time": 1649283452942,
                    "required_amount": 0.05
                  }
                ]""";

        Files.writeString(storageFile, json);

        SingleFileWalletStorage walletStorage = new SingleFileWalletStorage(storageFile, Duration.ofMinutes(10));
        walletStorage.deleteWallet(walletToDelete);

        String expectedJson = """
                [
                  {
                    "address": "nano_18xbfx1czna9178ah7gkyg6ukrdg919ebn9xt7j6fkq31kh4qwia4r3i7674",
                    "private_key": "B18852DAB11E34B4C0BEE3C53FCABF75560791E13EC7A5D5F9B7670277DD4643",
                    "creation_time": 1649247684032,
                    "required_amount": 0.1
                  }
                ]""";

        String foundJson = Files.readString(storageFile);

        assertEquals(expectedJson, foundJson);
    }
}