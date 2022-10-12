package xyz.benanderson.nanopay;

import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import uk.oczadly.karl.jnano.model.HexData;
import uk.oczadly.karl.jnano.model.NanoAccount;
import uk.oczadly.karl.jnano.model.NanoAmount;
import uk.oczadly.karl.jnano.model.block.StateBlock;
import uk.oczadly.karl.jnano.rpc.RpcQueryNode;
import uk.oczadly.karl.jnano.util.WalletUtil;
import uk.oczadly.karl.jnano.util.wallet.LocalRpcWalletAccount;
import uk.oczadly.karl.jnano.util.wallet.WalletActionException;
import xyz.benanderson.nanopay.death.*;
import xyz.benanderson.nanopay.storage.WalletStorage;
import xyz.benanderson.nanopay.storage.WalletStorageProvider;
import xyz.benanderson.nanopay.wallet.SecureRandomUtil;
import xyz.benanderson.nanopay.wallet.Wallet;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class WalletManagerTest {

    WalletStorageProvider walletStorageProvider;
    WalletDeathHandler walletDeathHandler;
    WalletDeathLogger walletDeathLogger;
    WebSocketListener webSocketListener;
    RpcQueryNode rpcClient;
    WalletManager walletManager;
    NanoAccount storageWallet, representative;
    Clock clock;

    final static BigDecimal MORE_THAN_REQUIRED_AMOUNT = new BigDecimal("8.0");
    final static BigDecimal REQUIRED_AMOUNT = new BigDecimal("5.0");
    final static BigDecimal LESS_THAN_REQUIRED_AMOUNT = new BigDecimal("1.0");

    @BeforeEach
    void setup() {
        webSocketListener = mock(WebSocketListener.class);
        rpcClient = mock(RpcQueryNode.class);
        walletStorageProvider = new WalletStorageProvider(mock(WalletStorage.class), mock(WalletStorage.class));
        storageWallet = NanoAccount.parse("nano_18xbfx1czna9178ah7gkyg6ukrdg919ebn9xt7j6fkq31kh4qwia4r3i7674");
        representative = storageWallet;
        clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());
        walletDeathLogger = mock(DefaultWalletDeathLogger.class);
        //noinspection unchecked
        walletDeathHandler = Mockito.spy(new DefaultWalletDeathHandler(
                mock(Consumer.class), mock(Consumer.class), storageWallet, rpcClient));

        walletManager = spy(new WalletManager(
                walletStorageProvider,
                walletDeathHandler,
                walletDeathLogger,
                webSocketListener,
                rpcClient,
                representative,
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
        WalletDeathState walletDeathState = WalletDeathState.success(true);
        doNothing().when(walletDeathHandler).refundExtraBalance(any(), any());
        doNothing().when(walletDeathLogger).log(any());
        walletManager.killWallet(rpcWallet, wallet, walletDeathState);

        verify(rpcWallet, times(1)).send(sendWalletCaptor.capture(), sendAmountCaptor.capture());
        verify(walletDeathHandler, times(1)).refundExtraBalance(refundExtraBalanceRpcWalletCaptor.capture(),
                refundExtraBalanceRequiredAmountCaptor.capture());
        verify(walletDeathHandler.getPaymentSuccessListener(), times(1)).accept(wallet.address());
        verify(walletDeathHandler.getPaymentFailListener(), times(0)).accept(wallet.address());
        verify(walletStorageProvider.activeWalletStorage()).deleteWallet(activeWalletStorageDeleteCaptor.capture());
        verify(walletStorageProvider.deadWalletStorage()).saveWallet(deadWalletStorageSaveCaptor.capture());
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
        doNothing().when(walletDeathLogger).log(any());
        WalletDeathState walletDeathState = WalletDeathState.success(false);
        walletManager.killWallet(rpcWallet, wallet, walletDeathState);

        verify(rpcWallet, times(1)).send(sendWalletCaptor.capture(), sendAmountCaptor.capture());
        verify(walletDeathHandler, times(0)).refundExtraBalance(any(), any());
        verify(walletDeathHandler.getPaymentSuccessListener(), times(1)).accept(wallet.address());
        verify(walletDeathHandler.getPaymentFailListener(), times(0)).accept(wallet.address());
        verify(walletStorageProvider.activeWalletStorage()).deleteWallet(activeWalletStorageDeleteCaptor.capture());
        verify(walletStorageProvider.deadWalletStorage()).saveWallet(deadWalletStorageSaveCaptor.capture());
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
        WalletDeathState walletDeathState = WalletDeathState.failure();
        doNothing().when(walletDeathHandler).refundAllBalance(any());
        doNothing().when(walletDeathLogger).log(any());
        walletManager.killWallet(rpcWallet, wallet, walletDeathState);

        verify(walletDeathHandler, times(1)).refundAllBalance(refundAllBalanceRpcWalletCaptor.capture());
        verify(walletDeathHandler.getPaymentSuccessListener(), times(0)).accept(wallet.address());
        verify(walletDeathHandler.getPaymentFailListener(), times(1)).accept(wallet.address());
        verify(walletStorageProvider.activeWalletStorage()).deleteWallet(activeWalletStorageDeleteCaptor.capture());
        verify(walletStorageProvider.deadWalletStorage()).saveWallet(deadWalletStorageSaveCaptor.capture());
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
        WalletDeathState walletDeathState = WalletDeathState.failure();
        doNothing().when(walletDeathLogger).log(any());
        walletManager.killWallet(rpcWallet, wallet, walletDeathState);

        verify(walletDeathHandler, times(0)).refundAllBalance(rpcWallet);
        verify(walletDeathHandler.getPaymentSuccessListener(), times(0)).accept(wallet.address());
        verify(walletDeathHandler.getPaymentFailListener(), times(1)).accept(wallet.address());
        verify(walletStorageProvider.activeWalletStorage()).deleteWallet(activeWalletStorageDeleteCaptor.capture());
        verify(walletStorageProvider.deadWalletStorage()).saveWallet(deadWalletStorageSaveCaptor.capture());
        assertEquals(wallet, activeWalletStorageDeleteCaptor.getValue());
        assertEquals(wallet, deadWalletStorageSaveCaptor.getValue());
    }

    @Test
    void requestPayment() {
        ArgumentCaptor<String> webSocketFilterCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Wallet> walletStorageCaptor = ArgumentCaptor.forClass(Wallet.class);

        String address = walletManager.requestPayment(REQUIRED_AMOUNT);
        verify(webSocketListener).addWalletFilter(webSocketFilterCaptor.capture());
        verify(walletStorageProvider.activeWalletStorage()).saveWallet(walletStorageCaptor.capture());

        assertEquals(address, webSocketFilterCaptor.getValue());
        assertEquals(address, walletStorageCaptor.getValue().address());
        assertEquals(clock.instant().truncatedTo(ChronoUnit.MILLIS), walletStorageCaptor.getValue().creationTime());
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
        when(walletStorageProvider.activeWalletStorage().getAllWallets()).thenReturn(testWallets);
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
        ArgumentCaptor<WalletDeathState> killWalletDeathStateCaptor
                = ArgumentCaptor.forClass(WalletDeathState.class);

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
        assertEquals(WalletDeathState.success(false), killWalletDeathStateCaptor.getValue());
    }

    @Test
    void checkWalletReceivedExtra() throws WalletActionException {
        @SuppressWarnings("unchecked") ArgumentCaptor<LocalRpcWalletAccount<StateBlock>>
                killRpcWalletCaptor = ArgumentCaptor.forClass(LocalRpcWalletAccount.class);
        ArgumentCaptor<Wallet> killWalletCaptor = ArgumentCaptor.forClass(Wallet.class);
        ArgumentCaptor<WalletDeathState> killWalletDeathStateCaptor
                = ArgumentCaptor.forClass(WalletDeathState.class);

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
        assertEquals(WalletDeathState.success(true), killWalletDeathStateCaptor.getValue());
    }

}