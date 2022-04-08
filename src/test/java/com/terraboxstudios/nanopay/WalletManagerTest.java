package com.terraboxstudios.nanopay;

import com.terraboxstudios.nanopay.storage.WalletStorage;
import com.terraboxstudios.nanopay.storage.WalletStorageProvider;
import com.terraboxstudios.nanopay.util.SecureRandomUtil;
import com.terraboxstudios.nanopay.wallet.Wallet;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uk.oczadly.karl.jnano.model.HexData;
import uk.oczadly.karl.jnano.model.NanoAccount;
import uk.oczadly.karl.jnano.model.NanoAmount;
import uk.oczadly.karl.jnano.model.block.StateBlock;
import uk.oczadly.karl.jnano.rpc.JsonResponseDeserializer;
import uk.oczadly.karl.jnano.rpc.RpcQueryNode;
import uk.oczadly.karl.jnano.rpc.exception.RpcException;
import uk.oczadly.karl.jnano.rpc.request.node.RequestAccountHistory;
import uk.oczadly.karl.jnano.rpc.response.ResponseAccountHistory;
import uk.oczadly.karl.jnano.util.WalletUtil;
import uk.oczadly.karl.jnano.util.wallet.LocalRpcWalletAccount;
import uk.oczadly.karl.jnano.util.wallet.WalletActionException;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class WalletManagerTest {

    WalletStorageProvider walletStorageProvider;
    WalletStorage activeWalletStorage, deadWalletStorage;
    WebSocketListener webSocketListener;
    RpcQueryNode rpcClient;
    WalletManager walletManager;
    NanoAccount storageWallet, representative;
    Consumer<String> paymentSuccessListener;
    Consumer<String> paymentFailListener;
    Clock clock;

    final static BigDecimal MORE_THAN_REQUIRED_AMOUNT = new BigDecimal("8.0");
    final static BigDecimal REQUIRED_AMOUNT = new BigDecimal("5.0");
    final static BigDecimal LESS_THAN_REQUIRED_AMOUNT = new BigDecimal("1.0");

    @BeforeEach
    void setup() {
        webSocketListener = mock(WebSocketListener.class);
        activeWalletStorage = mock(WalletStorage.class);
        deadWalletStorage = mock(WalletStorage.class);
        rpcClient = mock(RpcQueryNode.class);
        //noinspection unchecked
        paymentSuccessListener = mock(Consumer.class);
        //noinspection unchecked
        paymentFailListener = mock(Consumer.class);
        walletStorageProvider = new WalletStorageProvider(activeWalletStorage, deadWalletStorage);
        storageWallet = NanoAccount.parse("nano_18xbfx1czna9178ah7gkyg6ukrdg919ebn9xt7j6fkq31kh4qwia4r3i7674");
        representative = storageWallet;
        clock = Clock.fixed(Instant.ofEpochMilli(1649281447828L), ZoneId.systemDefault());

        walletManager = spy(new WalletManager(
                walletStorageProvider,
                webSocketListener,
                storageWallet,
                rpcClient,
                representative,
                paymentSuccessListener,
                paymentFailListener,
                clock
        ));
    }

    @SneakyThrows
    private Wallet generateTestWallet() {
        HexData privateKey = WalletUtil.generateRandomKey(SecureRandomUtil.getSecureRandom());
        return new Wallet(
                NanoAccount.fromPrivateKey(privateKey).toAddress(),
                privateKey.toString(),
                clock.instant(),
                REQUIRED_AMOUNT
        );
    }

    @Test
    void refundExtraBalanceOverflowingPaymentAndExtraPayment() throws RpcException, IOException, WalletActionException {
        //create account history json where the older payment is the more than the required amount (take as `y`),
        //and the newer payment is less than the required amount (take as `x`),
        //this should cause a refund of `y - REQUIRED_AMOUNT` to the older payer, and `x` to the newer payer
        String responseAccountHistoryJson = """
                {
                  "history": [
                    {
                      "type": "receive",
                      "account": "nano_3texgo63bs89jhtj4f6fn51nmsbh899nyfxxt51k66o8umhb931dz4bf9eto",
                      "amount": "%d",
                      "local_timestamp": "1649277683",
                      "height": "73",
                      "hash": "1F6A944D9C2B8D84816388E846A850C09A2C1714C488BBA4B67D8726EE11A617",
                      "confirmed": "true"
                    },
                    {
                      "type": "receive",
                      "account": "nano_3kaq71n6i4ndbkjiwjoj9747s74wtf586hu1fobzu7h6wkz86731eug3j3ac",
                      "amount": "%d",
                      "local_timestamp": "1649277656",
                      "height": "71",
                      "hash": "1F6A944D9C2B8D84816388E846A850C09A2C1714C488BBA4B67D8726EE11A617",
                      "confirmed": "true"
                    }
                  ]
                }""".formatted(NanoAmount.valueOfNano(LESS_THAN_REQUIRED_AMOUNT).getAsRaw(),
                                    NanoAmount.valueOfNano(MORE_THAN_REQUIRED_AMOUNT).getAsRaw());
        ResponseAccountHistory responseAccountHistory
                = new JsonResponseDeserializer().deserialize(responseAccountHistoryJson, ResponseAccountHistory.class);
        doReturn(responseAccountHistory).when(rpcClient).processRequest(any(RequestAccountHistory.class));

        ArgumentCaptor<NanoAccount> sendWalletCaptor = ArgumentCaptor.forClass(NanoAccount.class);
        ArgumentCaptor<NanoAmount> sendAmountCaptor = ArgumentCaptor.forClass(NanoAmount.class);
        Wallet wallet = generateTestWallet();
        LocalRpcWalletAccount<StateBlock> rpcWallet = spy(walletManager.getLocalRpcWallet(wallet));
        doReturn(null).when(rpcWallet).send(any(), any());

        walletManager.refundExtraBalance(rpcWallet, wallet.requiredAmount());
        verify(rpcWallet, times(2)).send(sendWalletCaptor.capture(), sendAmountCaptor.capture());
        assertEquals("nano_3kaq71n6i4ndbkjiwjoj9747s74wtf586hu1fobzu7h6wkz86731eug3j3ac",
                sendWalletCaptor.getAllValues().get(0).toAddress());
        assertEquals(NanoAmount.valueOfNano(MORE_THAN_REQUIRED_AMOUNT.subtract(REQUIRED_AMOUNT)),
                sendAmountCaptor.getAllValues().get(0));
        assertEquals("nano_3texgo63bs89jhtj4f6fn51nmsbh899nyfxxt51k66o8umhb931dz4bf9eto",
                sendWalletCaptor.getAllValues().get(1).toAddress());
        assertEquals(NanoAmount.valueOfNano(LESS_THAN_REQUIRED_AMOUNT),
                sendAmountCaptor.getAllValues().get(1));
    }

    @Test
    void refundExtraBalanceExtraPayment() throws RpcException, IOException, WalletActionException {
        //create account history json where the older payment is the required amount (take as `y`),
        //and the newer payment is less than the required amount (take as `x`),
        //this should cause a refund `x` to the newer payer
        String responseAccountHistoryJson = """
                {
                  "history": [
                    {
                      "type": "receive",
                      "account": "nano_3texgo63bs89jhtj4f6fn51nmsbh899nyfxxt51k66o8umhb931dz4bf9eto",
                      "amount": "%d",
                      "local_timestamp": "1649277683",
                      "height": "73",
                      "hash": "1F6A944D9C2B8D84816388E846A850C09A2C1714C488BBA4B67D8726EE11A617",
                      "confirmed": "true"
                    },
                    {
                      "type": "receive",
                      "account": "nano_3kaq71n6i4ndbkjiwjoj9747s74wtf586hu1fobzu7h6wkz86731eug3j3ac",
                      "amount": "%d",
                      "local_timestamp": "1649277656",
                      "height": "71",
                      "hash": "1F6A944D9C2B8D84816388E846A850C09A2C1714C488BBA4B67D8726EE11A617",
                      "confirmed": "true"
                    }
                  ]
                }""".formatted(NanoAmount.valueOfNano(LESS_THAN_REQUIRED_AMOUNT).getAsRaw(),
                                    NanoAmount.valueOfNano(REQUIRED_AMOUNT).getAsRaw());
        ResponseAccountHistory responseAccountHistory
                = new JsonResponseDeserializer().deserialize(responseAccountHistoryJson, ResponseAccountHistory.class);
        doReturn(responseAccountHistory).when(rpcClient).processRequest(any(RequestAccountHistory.class));

        ArgumentCaptor<NanoAccount> sendWalletCaptor = ArgumentCaptor.forClass(NanoAccount.class);
        ArgumentCaptor<NanoAmount> sendAmountCaptor = ArgumentCaptor.forClass(NanoAmount.class);
        Wallet wallet = generateTestWallet();
        LocalRpcWalletAccount<StateBlock> rpcWallet = spy(walletManager.getLocalRpcWallet(wallet));
        doReturn(null).when(rpcWallet).send(any(), any());

        walletManager.refundExtraBalance(rpcWallet, wallet.requiredAmount());
        verify(rpcWallet, times(1)).send(sendWalletCaptor.capture(), sendAmountCaptor.capture());
        assertEquals("nano_3texgo63bs89jhtj4f6fn51nmsbh899nyfxxt51k66o8umhb931dz4bf9eto",
                sendWalletCaptor.getValue().toAddress());
        assertEquals(NanoAmount.valueOfNano(LESS_THAN_REQUIRED_AMOUNT), sendAmountCaptor.getValue());
    }

    @Test
    void refundExtraBalanceOverflowingPayment() throws RpcException, IOException, WalletActionException {
        //create account history json where the older payment is less than the required amount (take as `y`),
        //and the newer payment is the required amount (take as `x`),
        //this should cause a refund of `y` to the newer payer
        String responseAccountHistoryJson = """
                {
                  "history": [
                    {
                      "type": "receive",
                      "account": "nano_3texgo63bs89jhtj4f6fn51nmsbh899nyfxxt51k66o8umhb931dz4bf9eto",
                      "amount": "%d",
                      "local_timestamp": "1649277683",
                      "height": "73",
                      "hash": "1F6A944D9C2B8D84816388E846A850C09A2C1714C488BBA4B67D8726EE11A617",
                      "confirmed": "true"
                    },
                    {
                      "type": "receive",
                      "account": "nano_3kaq71n6i4ndbkjiwjoj9747s74wtf586hu1fobzu7h6wkz86731eug3j3ac",
                      "amount": "%d",
                      "local_timestamp": "1649277656",
                      "height": "71",
                      "hash": "1F6A944D9C2B8D84816388E846A850C09A2C1714C488BBA4B67D8726EE11A617",
                      "confirmed": "true"
                    }
                  ]
                }""".formatted(NanoAmount.valueOfNano(REQUIRED_AMOUNT).getAsRaw(),
                                    NanoAmount.valueOfNano(LESS_THAN_REQUIRED_AMOUNT).getAsRaw());
        ResponseAccountHistory responseAccountHistory
                = new JsonResponseDeserializer().deserialize(responseAccountHistoryJson, ResponseAccountHistory.class);
        doReturn(responseAccountHistory).when(rpcClient).processRequest(any(RequestAccountHistory.class));

        ArgumentCaptor<NanoAccount> sendWalletCaptor = ArgumentCaptor.forClass(NanoAccount.class);
        ArgumentCaptor<NanoAmount> sendAmountCaptor = ArgumentCaptor.forClass(NanoAmount.class);
        Wallet wallet = generateTestWallet();
        LocalRpcWalletAccount<StateBlock> rpcWallet = spy(walletManager.getLocalRpcWallet(wallet));
        doReturn(null).when(rpcWallet).send(any(), any());

        walletManager.refundExtraBalance(rpcWallet, wallet.requiredAmount());
        verify(rpcWallet, times(1)).send(sendWalletCaptor.capture(), sendAmountCaptor.capture());
        assertEquals("nano_3texgo63bs89jhtj4f6fn51nmsbh899nyfxxt51k66o8umhb931dz4bf9eto",
                sendWalletCaptor.getValue().toAddress());
        assertEquals(NanoAmount.valueOfNano(LESS_THAN_REQUIRED_AMOUNT), sendAmountCaptor.getValue());
    }

    @Test
    void refundAllBalance() throws RpcException, IOException, WalletActionException {
        String responseAccountHistoryJson = """
                {
                  "history": [
                    {
                      "type": "receive",
                      "account": "nano_3texgo63bs89jhtj4f6fn51nmsbh899nyfxxt51k66o8umhb931dz4bf9eto",
                      "amount": "%d",
                      "local_timestamp": "1649277683",
                      "height": "73",
                      "hash": "1F6A944D9C2B8D84816388E846A850C09A2C1714C488BBA4B67D8726EE11A617",
                      "confirmed": "true"
                    },
                    {
                      "type": "receive",
                      "account": "nano_3kaq71n6i4ndbkjiwjoj9747s74wtf586hu1fobzu7h6wkz86731eug3j3ac",
                      "amount": "%d",
                      "local_timestamp": "1649277656",
                      "height": "71",
                      "hash": "1F6A944D9C2B8D84816388E846A850C09A2C1714C488BBA4B67D8726EE11A617",
                      "confirmed": "true"
                    }
                  ]
                }""".formatted(NanoAmount.valueOfNano(LESS_THAN_REQUIRED_AMOUNT).getAsRaw(),
                                    NanoAmount.valueOfNano(REQUIRED_AMOUNT).getAsRaw());
        ResponseAccountHistory responseAccountHistory
                = new JsonResponseDeserializer().deserialize(responseAccountHistoryJson, ResponseAccountHistory.class);
        doReturn(responseAccountHistory).when(rpcClient).processRequest(any(RequestAccountHistory.class));

        ArgumentCaptor<NanoAccount> sendWalletCaptor = ArgumentCaptor.forClass(NanoAccount.class);
        ArgumentCaptor<NanoAmount> sendAmountCaptor = ArgumentCaptor.forClass(NanoAmount.class);
        Wallet wallet = generateTestWallet();
        LocalRpcWalletAccount<StateBlock> rpcWallet = spy(walletManager.getLocalRpcWallet(wallet));
        doReturn(null).when(rpcWallet).send(any(), any());
        doReturn(NanoAmount.valueOfNano(LESS_THAN_REQUIRED_AMOUNT.add(REQUIRED_AMOUNT))).when(rpcWallet).getBalance();
        doReturn(Optional.empty()).when(rpcWallet).sendAll(any());

        walletManager.refundAllBalance(rpcWallet);
        verify(rpcWallet, times(2)).send(sendWalletCaptor.capture(), sendAmountCaptor.capture());
        assertEquals("nano_3texgo63bs89jhtj4f6fn51nmsbh899nyfxxt51k66o8umhb931dz4bf9eto",
                sendWalletCaptor.getAllValues().get(0).toAddress());
        assertEquals(NanoAmount.valueOfNano(LESS_THAN_REQUIRED_AMOUNT),
                sendAmountCaptor.getAllValues().get(0));
        assertEquals("nano_3kaq71n6i4ndbkjiwjoj9747s74wtf586hu1fobzu7h6wkz86731eug3j3ac",
                sendWalletCaptor.getAllValues().get(1).toAddress());
        assertEquals(NanoAmount.valueOfNano(REQUIRED_AMOUNT),
                sendAmountCaptor.getAllValues().get(1));
    }

    @Test
    void killWalletReceivedExtra() throws WalletActionException {
        ArgumentCaptor<NanoAccount> sendWalletCaptor = ArgumentCaptor.forClass(NanoAccount.class);
        ArgumentCaptor<NanoAmount> sendAmountCaptor = ArgumentCaptor.forClass(NanoAmount.class);
        ArgumentCaptor<Wallet> activeWalletStorageDeleteCaptor = ArgumentCaptor.forClass(Wallet.class);
        ArgumentCaptor<Wallet> deadWalletStorageSaveCaptor = ArgumentCaptor.forClass(Wallet.class);
        ArgumentCaptor<BigDecimal> refundExtraBalanceRequiredAmountCaptor = ArgumentCaptor.forClass(BigDecimal.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<LocalRpcWalletAccount<StateBlock>> refundExtraBalanceRpcWalletCaptor
                = ArgumentCaptor.forClass(LocalRpcWalletAccount.class);

        Wallet wallet = generateTestWallet();
        LocalRpcWalletAccount<StateBlock> rpcWallet = spy(walletManager.getLocalRpcWallet(wallet));
        doReturn(null).when(rpcWallet).send(any(), any());
        doReturn(NanoAmount.valueOfNano(MORE_THAN_REQUIRED_AMOUNT)).when(rpcWallet).getBalance();
        WalletManager.WalletDeathState walletDeathState = WalletManager.WalletDeathState.success(true);
        doNothing().when(walletManager).refundExtraBalance(any(), any());
        walletManager.killWallet(rpcWallet, wallet, walletDeathState);

        verify(rpcWallet, times(1)).send(sendWalletCaptor.capture(), sendAmountCaptor.capture());
        verify(walletManager, times(1)).refundExtraBalance(refundExtraBalanceRpcWalletCaptor.capture(),
                refundExtraBalanceRequiredAmountCaptor.capture());
        verify(paymentSuccessListener, times(1)).accept(wallet.address());
        verify(paymentFailListener, times(0)).accept(wallet.address());
        verify(activeWalletStorage).deleteWallet(activeWalletStorageDeleteCaptor.capture());
        verify(deadWalletStorage).saveWallet(deadWalletStorageSaveCaptor.capture());
        assertEquals(storageWallet, sendWalletCaptor.getValue());
        assertEquals(NanoAmount.valueOfNano(wallet.requiredAmount()), sendAmountCaptor.getValue());
        assertEquals(wallet, activeWalletStorageDeleteCaptor.getValue());
        assertEquals(wallet, deadWalletStorageSaveCaptor.getValue());
        assertEquals(rpcWallet, refundExtraBalanceRpcWalletCaptor.getValue());
        assertEquals(wallet.requiredAmount(), refundExtraBalanceRequiredAmountCaptor.getValue());
    }

    @Test
    void killWalletReceivedExact() throws WalletActionException {
        ArgumentCaptor<NanoAccount> sendWalletCaptor = ArgumentCaptor.forClass(NanoAccount.class);
        ArgumentCaptor<NanoAmount> sendAmountCaptor = ArgumentCaptor.forClass(NanoAmount.class);
        ArgumentCaptor<Wallet> activeWalletStorageDeleteCaptor = ArgumentCaptor.forClass(Wallet.class);
        ArgumentCaptor<Wallet> deadWalletStorageSaveCaptor = ArgumentCaptor.forClass(Wallet.class);

        Wallet wallet = generateTestWallet();
        LocalRpcWalletAccount<StateBlock> rpcWallet = spy(walletManager.getLocalRpcWallet(wallet));
        doReturn(null).when(rpcWallet).send(any(), any());
        doReturn(NanoAmount.valueOfNano(REQUIRED_AMOUNT)).when(rpcWallet).getBalance();
        WalletManager.WalletDeathState walletDeathState = WalletManager.WalletDeathState.success(false);
        walletManager.killWallet(rpcWallet, wallet, walletDeathState);

        verify(rpcWallet, times(1)).send(sendWalletCaptor.capture(), sendAmountCaptor.capture());
        verify(walletManager, times(0)).refundExtraBalance(any(), any());
        verify(paymentSuccessListener, times(1)).accept(wallet.address());
        verify(paymentFailListener, times(0)).accept(wallet.address());
        verify(activeWalletStorage).deleteWallet(activeWalletStorageDeleteCaptor.capture());
        verify(deadWalletStorage).saveWallet(deadWalletStorageSaveCaptor.capture());
        assertEquals(storageWallet, sendWalletCaptor.getValue());
        assertEquals(NanoAmount.valueOfNano(wallet.requiredAmount()), sendAmountCaptor.getValue());
        assertEquals(wallet, activeWalletStorageDeleteCaptor.getValue());
        assertEquals(wallet, deadWalletStorageSaveCaptor.getValue());
    }

    @Test
    void killWalletReceivedNotEnough() throws WalletActionException {
        ArgumentCaptor<Wallet> activeWalletStorageDeleteCaptor = ArgumentCaptor.forClass(Wallet.class);
        ArgumentCaptor<Wallet> deadWalletStorageSaveCaptor = ArgumentCaptor.forClass(Wallet.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<LocalRpcWalletAccount<StateBlock>> refundAllBalanceRpcWalletCaptor
                = ArgumentCaptor.forClass(LocalRpcWalletAccount.class);

        Wallet wallet = generateTestWallet();
        LocalRpcWalletAccount<StateBlock> rpcWallet = spy(walletManager.getLocalRpcWallet(wallet));
        doReturn(NanoAmount.valueOfNano(LESS_THAN_REQUIRED_AMOUNT)).when(rpcWallet).getBalance();
        WalletManager.WalletDeathState walletDeathState = WalletManager.WalletDeathState.failure();
        doNothing().when(walletManager).refundAllBalance(any());
        walletManager.killWallet(rpcWallet, wallet, walletDeathState);

        verify(walletManager, times(1))
                .refundAllBalance(refundAllBalanceRpcWalletCaptor.capture());
        verify(paymentSuccessListener, times(0)).accept(wallet.address());
        verify(paymentFailListener, times(1)).accept(wallet.address());
        verify(activeWalletStorage).deleteWallet(activeWalletStorageDeleteCaptor.capture());
        verify(deadWalletStorage).saveWallet(deadWalletStorageSaveCaptor.capture());
        assertEquals(wallet, activeWalletStorageDeleteCaptor.getValue());
        assertEquals(wallet, deadWalletStorageSaveCaptor.getValue());
        assertEquals(rpcWallet, refundAllBalanceRpcWalletCaptor.getValue());
    }

    @Test
    void killWalletReceivedZero() throws WalletActionException {
        ArgumentCaptor<Wallet> activeWalletStorageDeleteCaptor = ArgumentCaptor.forClass(Wallet.class);
        ArgumentCaptor<Wallet> deadWalletStorageSaveCaptor = ArgumentCaptor.forClass(Wallet.class);

        Wallet wallet = generateTestWallet();
        LocalRpcWalletAccount<StateBlock> rpcWallet = spy(walletManager.getLocalRpcWallet(wallet));
        doReturn(NanoAmount.ZERO).when(rpcWallet).getBalance();
        WalletManager.WalletDeathState walletDeathState = WalletManager.WalletDeathState.failure();
        walletManager.killWallet(rpcWallet, wallet, walletDeathState);

        verify(walletManager, times(0)).refundAllBalance(rpcWallet);
        verify(paymentSuccessListener, times(0)).accept(wallet.address());
        verify(paymentFailListener, times(1)).accept(wallet.address());
        verify(activeWalletStorage).deleteWallet(activeWalletStorageDeleteCaptor.capture());
        verify(deadWalletStorage).saveWallet(deadWalletStorageSaveCaptor.capture());
        assertEquals(wallet, activeWalletStorageDeleteCaptor.getValue());
        assertEquals(wallet, deadWalletStorageSaveCaptor.getValue());
    }

    @Test
    void requestPayment() {
        ArgumentCaptor<String> webSocketFilterCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Wallet> walletStorageCaptor = ArgumentCaptor.forClass(Wallet.class);

        String address = walletManager.requestPayment(REQUIRED_AMOUNT);
        verify(webSocketListener).addWalletFilter(webSocketFilterCaptor.capture());
        verify(activeWalletStorage).saveWallet(walletStorageCaptor.capture());

        assertEquals(address, webSocketFilterCaptor.getValue());
        assertEquals(address, walletStorageCaptor.getValue().address());
        assertEquals(clock.instant(), walletStorageCaptor.getValue().creationTime());
        assertEquals(REQUIRED_AMOUNT, walletStorageCaptor.getValue().requiredAmount());
    }

    @Test
    void loadWallets() throws WalletActionException {
        ArgumentCaptor<String> webSocketFilterCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Wallet> walletManagerWalletCheckCaptor = ArgumentCaptor.forClass(Wallet.class);
        @SuppressWarnings("unchecked") ArgumentCaptor<LocalRpcWalletAccount<StateBlock>>
                walletManagerRpcWalletCheckCaptor = ArgumentCaptor.forClass(LocalRpcWalletAccount.class);

        Collection<Wallet> testWallets = new HashSet<>();
        for (int i = 0; i < 10; i++) {
            testWallets.add(generateTestWallet());
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
    void checkWalletReceivedNotEnough() throws WalletActionException {
        Wallet wallet = generateTestWallet();
        LocalRpcWalletAccount<StateBlock> rpcWallet = spy(walletManager.getLocalRpcWallet(wallet));
        doReturn(Collections.emptySet()).when(rpcWallet).receiveAll();
        NanoAmount nanoAmount = NanoAmount.valueOfNano(LESS_THAN_REQUIRED_AMOUNT);
        doReturn(nanoAmount).when(rpcWallet).getBalance();

        walletManager.checkWallet(rpcWallet, wallet);
        verify(walletManager, times(0)).killWallet(any(), any(), any());
    }

    @Test
    void checkWalletReceivedExact() throws WalletActionException {
        @SuppressWarnings("unchecked") ArgumentCaptor<LocalRpcWalletAccount<StateBlock>>
                killRpcWalletCaptor = ArgumentCaptor.forClass(LocalRpcWalletAccount.class);
        ArgumentCaptor<Wallet> killWalletCaptor = ArgumentCaptor.forClass(Wallet.class);
        ArgumentCaptor<WalletManager.WalletDeathState> killWalletDeathStateCaptor
                = ArgumentCaptor.forClass(WalletManager.WalletDeathState.class);

        Wallet wallet = generateTestWallet();
        LocalRpcWalletAccount<StateBlock> rpcWallet = spy(walletManager.getLocalRpcWallet(wallet));
        doReturn(Collections.emptySet()).when(rpcWallet).receiveAll();
        NanoAmount nanoAmount = NanoAmount.valueOfNano(REQUIRED_AMOUNT);
        doReturn(nanoAmount).when(rpcWallet).getBalance();

        walletManager.checkWallet(rpcWallet, wallet);
        verify(walletManager, times(1)).killWallet(
                killRpcWalletCaptor.capture(),
                killWalletCaptor.capture(),
                killWalletDeathStateCaptor.capture()
        );

        assertEquals(rpcWallet, killRpcWalletCaptor.getValue());
        assertEquals(wallet, killWalletCaptor.getValue());
        assertEquals(WalletManager.WalletDeathState.success(false), killWalletDeathStateCaptor.getValue());
    }

    @Test
    void checkWalletReceivedExtra() throws WalletActionException {
        @SuppressWarnings("unchecked") ArgumentCaptor<LocalRpcWalletAccount<StateBlock>>
                killRpcWalletCaptor = ArgumentCaptor.forClass(LocalRpcWalletAccount.class);
        ArgumentCaptor<Wallet> killWalletCaptor = ArgumentCaptor.forClass(Wallet.class);
        ArgumentCaptor<WalletManager.WalletDeathState> killWalletDeathStateCaptor
                = ArgumentCaptor.forClass(WalletManager.WalletDeathState.class);

        Wallet wallet = generateTestWallet();
        LocalRpcWalletAccount<StateBlock> rpcWallet = spy(walletManager.getLocalRpcWallet(wallet));
        doReturn(Collections.emptySet()).when(rpcWallet).receiveAll();
        NanoAmount nanoAmount = NanoAmount.valueOfNano(MORE_THAN_REQUIRED_AMOUNT);
        doReturn(nanoAmount).when(rpcWallet).getBalance();
        doNothing().when(walletManager).killWallet(any(), any(), any());

        walletManager.checkWallet(rpcWallet, wallet);
        verify(walletManager, times(1)).killWallet(
                killRpcWalletCaptor.capture(),
                killWalletCaptor.capture(),
                killWalletDeathStateCaptor.capture()
        );

        assertEquals(rpcWallet, killRpcWalletCaptor.getValue());
        assertEquals(wallet, killWalletCaptor.getValue());
        assertEquals(WalletManager.WalletDeathState.success(true), killWalletDeathStateCaptor.getValue());
    }

}