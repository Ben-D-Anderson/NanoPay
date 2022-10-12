package xyz.benanderson.nanopay.storage;

import xyz.benanderson.nanopay.CustomAssertions;
import xyz.benanderson.nanopay.wallet.Wallet;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class MemoryWalletStorageTest {

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

    @Test
    void failGetAllWallets() {
        MemoryWalletStorage walletStorage = new MemoryWalletStorage(Map.of(), Duration.ofMinutes(10));
        Collection<Wallet> foundWallets = walletStorage.getAllWallets();
        CustomAssertions.assertUnorderedCollectionEquals(Collections.emptyList(), foundWallets);
    }

    @Test
    void getAllWallets() {
        Map<String, Wallet> walletsMap = Map.of(testWalletOne.address(), testWalletOne, testWalletTwo.address(), testWalletTwo);
        MemoryWalletStorage walletStorage = new MemoryWalletStorage(walletsMap, Duration.ofMinutes(10));

        Collection<Wallet> expectedWallets = List.of(testWalletOne, testWalletTwo);
        CustomAssertions.assertUnorderedCollectionEquals(expectedWallets, walletStorage.getAllWallets());
    }

    @Test
    void failFindWalletByAddress() {
        Map<String, Wallet> walletsMap = Map.of(testWalletOne.address(), testWalletOne, testWalletTwo.address(), testWalletTwo);
        MemoryWalletStorage walletStorage = new MemoryWalletStorage(walletsMap, Duration.ofMinutes(10));

        assertFalse(walletStorage.findWalletByAddress("nano_3nafw1z91qhiuadtetwiukao999dthpahy8pdxn19ghxsh7wfcote5skm894")
                .isPresent());
    }

    @Test
    void findWalletByAddress() {
        Map<String, Wallet> walletsMap = Map.of(testWalletOne.address(), testWalletOne, testWalletTwo.address(), testWalletTwo);
        MemoryWalletStorage walletStorage = new MemoryWalletStorage(walletsMap, Duration.ofMinutes(10));

        Optional<Wallet> foundWallet = walletStorage.findWalletByAddress(testWalletTwo.address());
        assertTrue(foundWallet.isPresent());
        assertEquals(testWalletTwo, foundWallet.get());
    }

    @Test
    void saveWallet() {
        Map<String, Wallet> walletMap = new HashMap<>();
        walletMap.put(testWalletOne.address(), testWalletOne);
        MemoryWalletStorage walletStorage = new MemoryWalletStorage(walletMap, Duration.ofMinutes(10));

        assertFalse(walletMap.containsKey(testWalletTwo.address()));
        walletStorage.saveWallet(testWalletTwo);
        assertTrue(walletMap.containsKey(testWalletTwo.address()));
        assertEquals(testWalletTwo, walletMap.get(testWalletTwo.address()));
    }

    @Test
    void deleteWallet() {
        Map<String, Wallet> walletMap = new HashMap<>();
        walletMap.put(testWalletOne.address(), testWalletOne);
        walletMap.put(testWalletTwo.address(), testWalletTwo);
        MemoryWalletStorage walletStorage = new MemoryWalletStorage(walletMap, Duration.ofMinutes(10));

        assertTrue(walletMap.containsKey(testWalletTwo.address()));
        walletStorage.deleteWallet(testWalletTwo);
        assertFalse(walletMap.containsKey(testWalletTwo.address()));
    }

}