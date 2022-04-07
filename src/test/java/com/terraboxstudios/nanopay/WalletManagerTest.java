package com.terraboxstudios.nanopay;

import com.terraboxstudios.nanopay.storage.WalletStorage;
import com.terraboxstudios.nanopay.storage.WalletStorageProvider;
import com.terraboxstudios.nanopay.util.SecureRandomUtil;
import com.terraboxstudios.nanopay.wallet.Wallet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uk.oczadly.karl.jnano.model.HexData;
import uk.oczadly.karl.jnano.model.NanoAccount;
import uk.oczadly.karl.jnano.model.NanoAmount;
import uk.oczadly.karl.jnano.model.block.StateBlock;
import uk.oczadly.karl.jnano.rpc.RpcQueryNode;
import uk.oczadly.karl.jnano.util.WalletUtil;
import uk.oczadly.karl.jnano.util.wallet.LocalRpcWalletAccount;
import uk.oczadly.karl.jnano.util.wallet.WalletActionException;

import java.math.BigDecimal;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Random;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WalletManagerTest {

    WalletStorageProvider walletStorageProvider;
    WalletStorage activeWalletStorage, deadWalletStorage;
    WebSocketListener webSocketListener;
    RpcQueryNode rpcClient;
    WalletManager walletManager;
    Clock clock;

    /*static WebSocketListener mockWebSocketListener() {
        NanoWebSocketClient nanoWebSocketClient = mock(NanoWebSocketClient.class);
        doNothing().when(nanoWebSocketClient).setObserver(any());
        TopicRegistry topicRegistry = mock(TopicRegistry.class);
        TopicConfirmation topicConfirmation = mock(TopicConfirmation.class);
        doNothing().when(topicConfirmation).subscribe(any(TopicConfirmation.SubArgs.class));
        when(topicRegistry.topicConfirmedBlocks()).thenReturn(topicConfirmation);
        when(nanoWebSocketClient.getTopics()).thenReturn(topicRegistry);
        return new WebSocketListener(nanoWebSocketClient, false);
    }*/

    @BeforeEach
    void setup() {
        webSocketListener = mock(WebSocketListener.class);
        activeWalletStorage = mock(WalletStorage.class);
        deadWalletStorage = mock(WalletStorage.class);
        rpcClient = mock(RpcQueryNode.class);
        walletStorageProvider = new WalletStorageProvider(activeWalletStorage, deadWalletStorage);
        NanoAccount nanoAccount = NanoAccount.parse("nano_18xbfx1czna9178ah7gkyg6ukrdg919ebn9xt7j6fkq31kh4qwia4r3i7674");
        clock = Clock.fixed(Instant.ofEpochMilli(1649281447828L), ZoneId.systemDefault());

        walletManager = spy(new WalletManager(
                walletStorageProvider,
                webSocketListener,
                nanoAccount,
                rpcClient,
                nanoAccount,
                s -> {},
                s -> {},
                clock
        ));
    }

    @Test
    void removeWallet() {
        //todo add remove wallet tests for different cases
    }

    @Test
    void requestPayment() {
        ArgumentCaptor<String> webSocketFilterCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Wallet> walletStorageCaptor = ArgumentCaptor.forClass(Wallet.class);

        BigDecimal requiredAmount = new BigDecimal("0.1");
        String address = walletManager.requestPayment(requiredAmount);
        verify(webSocketListener).addWalletFilter(webSocketFilterCaptor.capture());
        verify(activeWalletStorage).saveWallet(walletStorageCaptor.capture());

        assertEquals(address, webSocketFilterCaptor.getValue());
        assertEquals(address, walletStorageCaptor.getValue().address());
        assertEquals(clock.instant(), walletStorageCaptor.getValue().creationTime());
        assertEquals(requiredAmount, walletStorageCaptor.getValue().requiredAmount());
    }

    @Test
    void loadWallets() throws NoSuchAlgorithmException, WalletActionException {
        ArgumentCaptor<String> webSocketFilterCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Wallet> walletManagerWalletCheckCaptor = ArgumentCaptor.forClass(Wallet.class);
        @SuppressWarnings("unchecked") ArgumentCaptor<LocalRpcWalletAccount<StateBlock>>
                walletManagerRpcWalletCheckCaptor = ArgumentCaptor.forClass(LocalRpcWalletAccount.class);

        Collection<Wallet> testWallets = new HashSet<>();
        for (int i = 0; i < 10; i++) {
            HexData privateKey = WalletUtil.generateRandomKey(SecureRandomUtil.getSecureRandom());
            Wallet wallet = new Wallet(
                    NanoAccount.fromPrivateKey(privateKey).toAddress(),
                    privateKey.toString(),
                    clock.instant(),
                    new BigDecimal(new Random().nextInt(10) + 1)
            );
            testWallets.add(wallet);
        }
        Collection<String> testAddresses = testWallets.stream().map(Wallet::address).collect(Collectors.toSet());
        when(activeWalletStorage.getAllWallets()).thenReturn(testWallets);
        doNothing().when(walletManager).checkWallet(any(), any());

        walletManager.loadWallets();

        verify(webSocketListener, times(10)).addWalletFilter(webSocketFilterCaptor.capture());
        verify(walletManager, times(10))
                .checkWallet(walletManagerRpcWalletCheckCaptor.capture(), walletManagerWalletCheckCaptor.capture());

        CustomAssertions.assertUnorderedCollectionEquals(testAddresses, webSocketFilterCaptor.getAllValues());
        CustomAssertions.assertUnorderedCollectionEquals(testWallets, walletManagerWalletCheckCaptor.getAllValues());
    }

    @Test
    void checkWalletNotEnough() throws NoSuchAlgorithmException, WalletActionException {
        HexData privateKey = WalletUtil.generateRandomKey(SecureRandomUtil.getSecureRandom());
        Wallet wallet = new Wallet(
                NanoAccount.fromPrivateKey(privateKey).toAddress(),
                privateKey.toString(),
                clock.instant(),
                new BigDecimal("5.0")
        );
        LocalRpcWalletAccount<StateBlock> rpcWallet = spy(walletManager.getLocalRpcWallet(wallet));
        doReturn(Collections.emptySet()).when(rpcWallet).receiveAll();
        NanoAmount nanoAmount = NanoAmount.valueOfNano(new BigDecimal("1.0"));
        doReturn(nanoAmount).when(rpcWallet).getBalance();

        walletManager.checkWallet(rpcWallet, wallet);
        verify(walletManager, times(0)).removeWallet(any(), any(), anyBoolean(), anyBoolean());
    }

    @Test
    void checkWalletExactAmount() throws NoSuchAlgorithmException, WalletActionException {
        @SuppressWarnings("unchecked") ArgumentCaptor<LocalRpcWalletAccount<StateBlock>>
                removeRpcWalletCaptor = ArgumentCaptor.forClass(LocalRpcWalletAccount.class);
        ArgumentCaptor<Wallet> removeWalletCaptor = ArgumentCaptor.forClass(Wallet.class);
        ArgumentCaptor<Boolean> removeSuccessCaptor = ArgumentCaptor.forClass(Boolean.class);
        ArgumentCaptor<Boolean> removeReceivedExtraCaptor = ArgumentCaptor.forClass(Boolean.class);

        HexData privateKey = WalletUtil.generateRandomKey(SecureRandomUtil.getSecureRandom());
        Wallet wallet = new Wallet(
                NanoAccount.fromPrivateKey(privateKey).toAddress(),
                privateKey.toString(),
                clock.instant(),
                new BigDecimal("5.0")
        );
        LocalRpcWalletAccount<StateBlock> rpcWallet = spy(walletManager.getLocalRpcWallet(wallet));
        doReturn(Collections.emptySet()).when(rpcWallet).receiveAll();
        NanoAmount nanoAmount = NanoAmount.valueOfNano(new BigDecimal("5.0"));
        doReturn(nanoAmount).when(rpcWallet).getBalance();

        walletManager.checkWallet(rpcWallet, wallet);
        verify(walletManager, times(1)).removeWallet(
                removeRpcWalletCaptor.capture(),
                removeWalletCaptor.capture(),
                removeSuccessCaptor.capture(),
                removeReceivedExtraCaptor.capture()
        );

        assertEquals(rpcWallet, removeRpcWalletCaptor.getValue());
        assertEquals(wallet, removeWalletCaptor.getValue());
        assertTrue(removeSuccessCaptor.getValue());
        assertFalse(removeReceivedExtraCaptor.getValue());
    }

    @Test
    void checkWalletExtraAmount() throws NoSuchAlgorithmException, WalletActionException {
        @SuppressWarnings("unchecked") ArgumentCaptor<LocalRpcWalletAccount<StateBlock>>
                removeRpcWalletCaptor = ArgumentCaptor.forClass(LocalRpcWalletAccount.class);
        ArgumentCaptor<Wallet> removeWalletCaptor = ArgumentCaptor.forClass(Wallet.class);
        ArgumentCaptor<Boolean> removeSuccessCaptor = ArgumentCaptor.forClass(Boolean.class);
        ArgumentCaptor<Boolean> removeReceivedExtraCaptor = ArgumentCaptor.forClass(Boolean.class);

        HexData privateKey = WalletUtil.generateRandomKey(SecureRandomUtil.getSecureRandom());
        Wallet wallet = new Wallet(
                NanoAccount.fromPrivateKey(privateKey).toAddress(),
                privateKey.toString(),
                clock.instant(),
                new BigDecimal("5.0")
        );
        LocalRpcWalletAccount<StateBlock> rpcWallet = spy(walletManager.getLocalRpcWallet(wallet));
        doReturn(Collections.emptySet()).when(rpcWallet).receiveAll();
        NanoAmount nanoAmount = NanoAmount.valueOfNano(new BigDecimal("10.0"));
        doReturn(nanoAmount).when(rpcWallet).getBalance();
        doNothing().when(walletManager).removeWallet(any(), any(), anyBoolean(), anyBoolean());

        walletManager.checkWallet(rpcWallet, wallet);
        verify(walletManager, times(1)).removeWallet(
                removeRpcWalletCaptor.capture(),
                removeWalletCaptor.capture(),
                removeSuccessCaptor.capture(),
                removeReceivedExtraCaptor.capture()
        );

        assertEquals(rpcWallet, removeRpcWalletCaptor.getValue());
        assertEquals(wallet, removeWalletCaptor.getValue());
        assertTrue(removeSuccessCaptor.getValue());
        assertTrue(removeReceivedExtraCaptor.getValue());
    }

}