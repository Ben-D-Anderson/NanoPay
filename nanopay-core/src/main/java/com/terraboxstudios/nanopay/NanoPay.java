package com.terraboxstudios.nanopay;

import com.terraboxstudios.nanopay.death.*;
import com.terraboxstudios.nanopay.storage.MemoryWalletStorage;
import com.terraboxstudios.nanopay.storage.ReadOnlyWalletStorage;
import com.terraboxstudios.nanopay.storage.WalletStorage;
import com.terraboxstudios.nanopay.storage.WalletStorageProvider;
import com.terraboxstudios.nanopay.wallet.Wallet;
import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.oczadly.karl.jnano.model.NanoAccount;
import uk.oczadly.karl.jnano.model.block.StateBlock;
import uk.oczadly.karl.jnano.rpc.RpcQueryNode;
import uk.oczadly.karl.jnano.rpc.exception.RpcException;
import uk.oczadly.karl.jnano.rpc.request.node.RequestMultiAccountBalances;
import uk.oczadly.karl.jnano.rpc.response.ResponseMultiAccountBalances;
import uk.oczadly.karl.jnano.util.wallet.LocalRpcWalletAccount;
import uk.oczadly.karl.jnano.util.wallet.WalletActionException;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URL;
import java.time.Clock;
import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class NanoPay {

    public static final Logger LOGGER = LoggerFactory.getLogger(NanoPay.class);
    private final WalletManager walletManager;

    private NanoPay(NanoPay.Builder builder) {
        WebSocketListener webSocketListener = new WebSocketListener(
                URI.create(builder.webSocketAddress), builder.webSocketReconnect);

        RpcQueryNode rpcClient = new RpcQueryNode(builder.rpcAddress);
        if (builder.walletDeathLogger == null) {
            builder.walletDeathLogger = new DefaultWalletDeathLogger();
        }
        if (builder.walletDeathHandler == null) {
            builder.walletDeathHandler = new DefaultWalletDeathHandler(builder.paymentSuccessListener,
                    builder.paymentFailureListener, builder.storageWallet, rpcClient);
        }
        walletManager = new WalletManager(
                builder.walletStorageProvider,
                builder.walletDeathHandler,
                builder.walletDeathLogger,
                webSocketListener,
                rpcClient,
                builder.representativeWallet,
                builder.clock
        );
        walletManager.loadWallets();
        if (builder.walletPruneServiceEnabled)
            walletManager.startWalletPruneService(builder.walletPruneService, builder.walletPruneDelay);
        if (builder.refundServiceEnabled)
            walletManager.startWalletRefundService(builder.refundWalletService, builder.walletRefundDelay);

        webSocketListener.connectWebSocket(transaction -> {
            NanoPay.LOGGER.debug("Listened to transaction: " + transaction);
            try {
                Optional<Wallet> walletOptional = walletManager.getWallet(transaction.receiver().toAddress());
                if (walletOptional.isEmpty()) return;
                Wallet wallet = walletOptional.get();
                walletManager.checkWallet(walletManager.getLocalRpcWallet(wallet), wallet);
            } catch (WalletActionException e) {
                NanoPay.LOGGER.error("Exception occurred checking wallet (" + transaction.receiver().toAddress() + ")", e);
            }
        });
    }

    /**
     * Creates a new NANO wallet to receive funds, adds it to the internal monitoring system, and stores it
     * in the active storage of the WalletStorageProvider of this object.
     *
     * @param amount The amount of NANO to receive for the payment to be considered completed
     * @return NANO wallet address the payment should be sent to - can be used as a unique identifier for the payment
     * (as wallets are not re-used)
     * @throws IllegalArgumentException if the amount requested is zero.
     */
    public String requestPayment(BigDecimal amount) {
        if (amount.equals(BigDecimal.ZERO)) throw new IllegalArgumentException("Required amount cannot be zero");
        return walletManager.requestPayment(amount);
    }

    /**
     * Attempts to kill the active {@link Wallet} waiting for payment. Any funds in the {@link Wallet} will be acted
     * on according to the {@link WalletDeathHandler} used by {@link NanoPay}, the {@link Wallet} will then be moved
     * to dead storage.
     * @param address Address of {@link Wallet} waiting for payment (equivalent to a transaction identifier)
     * @return boolean denoting whether the {@link Wallet} could be retrieved from active storage and cancel attempted
     */
    public boolean cancelPayment(String address) {
        Optional<Wallet> walletOptional = walletManager.getWallet(address);
        if (walletOptional.isEmpty()) return false;
        Wallet wallet = walletOptional.get();
        walletManager.killWallet(walletManager.getLocalRpcWallet(wallet), wallet, WalletDeathState.failure());
        return true;
    }

    /**
     * @return A read only view of the {@link WalletStorageProvider} used by the underlying {@link WalletManager}. All
     * returned data in the {@link WalletStorage}s is immutable, with {@link WalletStorage#saveWallet(Wallet)} and
     * {@link WalletStorage#deleteWallet(Wallet)} throwing an {@link UnsupportedOperationException}.
     */
    public WalletStorageProvider getWalletStorage() {
        return new WalletStorageProvider(
                new ReadOnlyWalletStorage(walletManager.getWalletStorageProvider().activeWalletStorage()),
                new ReadOnlyWalletStorage(walletManager.getWalletStorageProvider().deadWalletStorage())
        );
    }

    public WalletDeathLogger getWalletDeathLogger() {
        return walletManager.getWalletDeathLogger();
    }

    /**
     * Attempts to get the balance of a {@link Wallet}
     * @param wallet {@link Wallet} to retrieve the balance of
     * @return {@link Optional} denoting the retrieved balance of the {@link Wallet}, may be empty.
     */
    public Optional<BigDecimal> getBalance(Wallet wallet) {
        LocalRpcWalletAccount<StateBlock> walletAccount = walletManager.getLocalRpcWallet(wallet);
        try {
            walletAccount.receiveAll();
        } catch (WalletActionException ignored) {}
        try {
            return Optional.ofNullable(walletAccount.getBalance().getAsNano());
        } catch (WalletActionException e) {
            return Optional.empty();
        }
    }

    /**
     * Gets balances of {@link Wallet}s in batch.
     * @param walletCollection collection of {@link Wallet}s to batch process
     * @return {@link HashMap} of {@link Wallet} addresses and their respective balance denoted by an {@link Optional}.
     * If the balance retrieval for a {@link Wallet} failed, then an empty optional is stored as the value in the {@link HashMap}.
     */
    public Map<String, Optional<BigDecimal>> getBalances(Collection<Wallet> walletCollection) {
        Map<String, Optional<BigDecimal>> balances = new HashMap<>();
        String[] addresses = walletCollection.stream().map(Wallet::address).toArray(String[]::new);
        RequestMultiAccountBalances requestMultiAccountBalances = new RequestMultiAccountBalances(addresses);
        try {
            ResponseMultiAccountBalances responseMultiAccountBalances = walletManager.getRpcClient()
                    .processRequest(requestMultiAccountBalances);
            responseMultiAccountBalances.getBalances().forEach(
                    (entry, balance) -> balances.put(entry.toAddress(), Optional.of(balance.getTotal().getAsNano())));
            walletCollection.stream().map(Wallet::address).forEach(
                    address -> balances.computeIfAbsent(address, k -> Optional.empty()));
            return balances;
        } catch (IOException | RpcException e) {
            return new HashMap<>();
        }
    }

    public record RepeatingDelay(int initialDelayAmount, int repeatingDelayAmount, TimeUnit delayUnit) {}

    /**
     * Builder class used to construct a NanoPay object
     */
    public static class Builder {

        private final Consumer<String> paymentSuccessListener, paymentFailureListener;
        private final NanoAccount storageWallet;
        private WalletDeathHandler walletDeathHandler;
        private WalletDeathLogger walletDeathLogger;
        private URL rpcAddress;
        private String webSocketAddress = "wss://socket.nanos.cc/";
        private NanoAccount representativeWallet
                = NanoAccount.parseAddress("nano_1natrium1o3z5519ifou7xii8crpxpk8y65qmkih8e8bpsjri651oza8imdd");
        private WalletStorageProvider walletStorageProvider;
        private ScheduledExecutorService walletPruneService, refundWalletService;
        private boolean webSocketReconnect = true, walletPruneServiceEnabled = true, refundServiceEnabled = true;
        private RepeatingDelay walletRefundDelay = new RepeatingDelay(1, 1, TimeUnit.MINUTES);
        private RepeatingDelay walletPruneDelay = new RepeatingDelay(5, 5, TimeUnit.MINUTES);
        private Clock clock = Clock.systemDefaultZone();

        /**
         * @param storageWallet Wallet address for the funds of a payment to be transferred to,
         *                     after the payment has been processed and completed.
         * @param paymentSuccessListener Called when a payment is processed and completed, the argument to the consumer
         *                              is the address of the NANO wallet that received the funds for the payment
         *                              - can be used as a unique identifier for the payment (as wallets are not re-used).
         * @param paymentFailureListener Called when a payment is failed due to expiry time being met, the argument to the consumer
         *                              is the address of the NANO wallet that the funds were meant to be sent to
         *                              - can be used as a unique identifier for the payment (as wallets are not re-used).
         */
        @SneakyThrows
        public Builder(String storageWallet, Consumer<String> paymentSuccessListener, Consumer<String> paymentFailureListener) {
            this.storageWallet = NanoAccount.parseAddress(storageWallet);
            this.paymentSuccessListener = paymentSuccessListener;
            this.paymentFailureListener = paymentFailureListener;
            this.rpcAddress = new URL("https://proxy.nanos.cc/proxy");
        }

        /**
         * Disables the wallet prune service. The wallet prune service is the service that deletes wallets out of
         * active storage when they have expired and moves them to dead storage, and deletes them from dead storage
         * when they expire.
         */
        public Builder disableWalletPruneService() {
            this.walletPruneServiceEnabled = false;
            return this;
        }

        /**
         * Disables the dead wallet refund service. The refund service will refund all payments sent to dead
         * wallets whilst they are stored in the dead wallet storage.
         */
        public Builder disableRefundService() {
            this.refundServiceEnabled = false;
            return this;
        }

        public Builder setWalletDeathHandler(WalletDeathHandler walletDeathHandler) {
            this.walletDeathHandler = walletDeathHandler;
            return this;
        }

        public Builder setWalletDeathLogger(WalletDeathLogger walletDeathLogger) {
            this.walletDeathLogger = walletDeathLogger;
            return this;
        }

        public Builder setRepresentativeWallet(String representativeWallet) {
            this.representativeWallet = NanoAccount.parse(representativeWallet);
            return this;
        }

        public Builder setWebSocketAddress(String webSocketAddress) {
            this.webSocketAddress = webSocketAddress;
            return this;
        }

        @SneakyThrows
        public Builder setRpcAddress(String rpcAddress) {
            this.rpcAddress = new URL(rpcAddress);
            return this;
        }

        public Builder setClock(Clock clock) {
            this.clock = clock;
            return this;
        }

        public Builder setRpcAddress(URL rpcAddress) {
            this.rpcAddress = rpcAddress;
            return this;
        }

        public Builder disableWebSocketReconnect() {
            this.webSocketReconnect = false;
            return this;
        }

        public Builder setWalletStorageProvider(WalletStorageProvider walletStorageProvider) {
            this.walletStorageProvider = walletStorageProvider;
            return this;
        }

        public Builder setWalletPruneService(ScheduledExecutorService walletPruneService) {
            this.walletPruneService = walletPruneService;
            return this;
        }

        public Builder setRefundWalletService(ScheduledExecutorService refundWalletService) {
            this.refundWalletService = refundWalletService;
            return this;
        }

        public Builder setWalletPruneDelay(RepeatingDelay repeatingDelay) {
            this.walletPruneDelay = repeatingDelay;
            return this;
        }

        public Builder setWalletRefundDelay(RepeatingDelay repeatingDelay) {
            this.walletRefundDelay = repeatingDelay;
            return this;
        }

        public NanoPay build() {
            if (walletStorageProvider == null) walletStorageProvider = new WalletStorageProvider(
                    new MemoryWalletStorage(Duration.ofMinutes(15)), new MemoryWalletStorage(Duration.ofMinutes(60)));
            if ((walletPruneServiceEnabled || refundServiceEnabled)
                    && (walletPruneService == null || refundWalletService == null)) {
                ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
                if (walletPruneService == null && walletPruneServiceEnabled) {
                    walletPruneService = scheduledExecutorService;
                }
                if (refundWalletService == null && refundServiceEnabled) {
                    refundWalletService = scheduledExecutorService;
                }
            }
            return new NanoPay(this);
        }

    }

}
