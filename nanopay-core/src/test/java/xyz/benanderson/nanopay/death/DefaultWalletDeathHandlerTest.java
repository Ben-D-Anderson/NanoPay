package xyz.benanderson.nanopay.death;

import xyz.benanderson.nanopay.wallet.SecureRandomUtil;
import xyz.benanderson.nanopay.wallet.Wallet;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uk.oczadly.karl.jnano.model.HexData;
import uk.oczadly.karl.jnano.model.NanoAccount;
import uk.oczadly.karl.jnano.model.NanoAmount;
import uk.oczadly.karl.jnano.model.block.StateBlock;
import uk.oczadly.karl.jnano.model.block.factory.BlockFactory;
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
import java.util.Optional;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DefaultWalletDeathHandlerTest {

    WalletDeathHandler walletDeathHandler;
    BlockFactory<StateBlock> blockFactory;
    RpcQueryNode rpcClient;
    NanoAccount storageWallet;
    Clock clock;

    final static BigDecimal MORE_THAN_REQUIRED_AMOUNT = new BigDecimal("8.0");
    final static BigDecimal REQUIRED_AMOUNT = new BigDecimal("5.0");
    final static BigDecimal LESS_THAN_REQUIRED_AMOUNT = new BigDecimal("1.0");

    @BeforeEach
    void setup() {
        rpcClient = mock(RpcQueryNode.class);
        //noinspection unchecked
        blockFactory = mock(BlockFactory.class);
        storageWallet = NanoAccount.parse("nano_18xbfx1czna9178ah7gkyg6ukrdg919ebn9xt7j6fkq31kh4qwia4r3i7674");
        //noinspection unchecked
        walletDeathHandler = spy(new DefaultWalletDeathHandler(
                mock(Consumer.class), mock(Consumer.class), storageWallet, rpcClient));
        clock = Clock.fixed(Instant.ofEpochMilli(1649281447828L), ZoneId.systemDefault());
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
        LocalRpcWalletAccount<StateBlock> rpcWallet = spy(new LocalRpcWalletAccount<>(
                new HexData(wallet.privateKey()), rpcClient, blockFactory));
        doReturn(null).when(rpcWallet).send(any(), any());

        walletDeathHandler.refundExtraBalance(rpcWallet, wallet.requiredAmount());
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
        LocalRpcWalletAccount<StateBlock> rpcWallet = spy(new LocalRpcWalletAccount<>(
                new HexData(wallet.privateKey()), rpcClient, blockFactory));
        doReturn(null).when(rpcWallet).send(any(), any());

        walletDeathHandler.refundExtraBalance(rpcWallet, wallet.requiredAmount());
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
        LocalRpcWalletAccount<StateBlock> rpcWallet = spy(new LocalRpcWalletAccount<>(
                new HexData(wallet.privateKey()), rpcClient, blockFactory));
        doReturn(null).when(rpcWallet).send(any(), any());

        walletDeathHandler.refundExtraBalance(rpcWallet, wallet.requiredAmount());
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
        LocalRpcWalletAccount<StateBlock> rpcWallet = spy(new LocalRpcWalletAccount<>(
                new HexData(wallet.privateKey()), rpcClient, blockFactory));
        doReturn(null).when(rpcWallet).send(any(), any());
        doReturn(NanoAmount.valueOfNano(LESS_THAN_REQUIRED_AMOUNT.add(REQUIRED_AMOUNT))).when(rpcWallet).getBalance();
        doReturn(Optional.empty()).when(rpcWallet).sendAll(any());

        walletDeathHandler.refundAllBalance(rpcWallet);
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

}
