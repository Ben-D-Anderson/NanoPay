package com.terraboxstudios.nanopay;

import com.terraboxstudios.nanopay.storage.MemoryWalletStorage;
import com.terraboxstudios.nanopay.storage.WalletStorageProvider;
import com.terraboxstudios.nanopay.wallet.Wallet;
import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.oczadly.karl.jnano.model.NanoAccount;
import uk.oczadly.karl.jnano.rpc.RpcQueryNode;
import uk.oczadly.karl.jnano.util.wallet.WalletActionException;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URL;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;

public final class NanoPay {

    public static final Logger LOGGER = LoggerFactory.getLogger(NanoPay.class);
    private final WalletManager walletManager;

    private NanoPay(NanoPay.Builder builder) {
        WebSocketListener webSocketListener = new WebSocketListener(URI.create(builder.webSocketAddress), builder.webSocketReconnect);

        walletManager = new WalletManager(
                builder.walletStorageProvider,
                webSocketListener,
                builder.storageWallet,
                new RpcQueryNode(builder.rpcAddress),
                builder.representativeWallet,
                builder.paymentSuccessListener,
                builder.paymentFailListener,
                builder.clock
        );
        walletManager.loadWallets();
        if (builder.walletPruningServiceEnabled)
            walletManager.startPruningService(builder.walletPruningService, builder.walletPruneDelay);
        if (builder.refundServiceEnabled)
            walletManager.startRefundService(builder.refundWalletService, builder.refundWalletDelay);

        webSocketListener.connectWebSocket(transaction -> {
            NanoPay.LOGGER.debug("Listened to transaction. " + transaction);
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
     * @return NANO wallet address the payment should be sent to - can be used as a unique identifier for the payment (as wallets are not re-used)
     * @throws IllegalArgumentException if the amount requested is zero.
     */
    public String requestPayment(BigDecimal amount) {
        if (amount.equals(BigDecimal.ZERO)) throw new IllegalArgumentException("Required amount cannot be zero");
        return walletManager.requestPayment(amount);
    }

    /**
     * Builder class used to construct a NanoPay object
     */
    public static class Builder {

        private final Consumer<String> paymentSuccessListener;
        private final NanoAccount storageWallet;
        private URL rpcAddress;
        private String webSocketAddress = "wss://socket.nanos.cc/";
        private NanoAccount representativeWallet = NanoAccount.parseAddress("nano_1natrium1o3z5519ifou7xii8crpxpk8y65qmkih8e8bpsjri651oza8imdd");
        private WalletStorageProvider walletStorageProvider;
        private ScheduledExecutorService walletPruningService, refundWalletService;
        private Consumer<String> paymentFailListener = walletAddress -> {};
        private boolean webSocketReconnect = false, walletPruningServiceEnabled = true, refundServiceEnabled = true;
        private Duration walletPruneDelay = Duration.ofMinutes(1), refundWalletDelay = Duration.ofMinutes(5);
        private Clock clock = Clock.systemDefaultZone();

        /**
         * @param storageWallet Wallet address for the funds of a payment to be transferred to, after the payment has been processed and completed.
         * @param paymentSuccessListener Called when a payment is processed and completed, the argument to the consumer
         *                              is the address of the NANO wallet that received the funds for the payment - can be used
         *                              as a unique identifier for the payment (as wallets are not re-used).
         */
        @SneakyThrows
        public Builder(String storageWallet, Consumer<String> paymentSuccessListener) {
            this.storageWallet = NanoAccount.parseAddress(storageWallet);
            this.paymentSuccessListener = paymentSuccessListener;
            this.rpcAddress = new URL("https://proxy.nanos.cc/proxy");
        }

        /**
         * Disables the wallet pruning service. The wallet pruning service is the service that deletes wallets out of
         * active storage when they have expired and moves them to dead storage, and deletes them from dead storage
         * when they expire.
         */
        public Builder disableWalletPruningService() {
            this.walletPruningServiceEnabled = false;
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

        public Builder onPaymentFail(Consumer<String> paymentFailListener) {
            this.paymentFailListener = paymentFailListener;
            return this;
        }

        public Builder enableWebSocketReconnect() {
            this.webSocketReconnect = true;
            return this;
        }

        public Builder setWalletStorageProvider(WalletStorageProvider walletStorageProvider) {
            this.walletStorageProvider = walletStorageProvider;
            return this;
        }

        public Builder setWalletPruningService(ScheduledExecutorService walletPruningService) {
            this.walletPruningService = walletPruningService;
            return this;
        }

        public Builder setRefundWalletService(ScheduledExecutorService refundWalletService) {
            this.refundWalletService = refundWalletService;
            return this;
        }

        public Builder setWalletPruningDelay(Duration walletPruneDelay) {
            this.walletPruneDelay = walletPruneDelay;
            return this;
        }

        public Builder setRefundWalletDelay(Duration refundWalletDelay) {
            this.refundWalletDelay = refundWalletDelay;
            return this;
        }

        public NanoPay build() {
            if (walletStorageProvider == null) walletStorageProvider = new WalletStorageProvider(new MemoryWalletStorage(Duration.ofMinutes(15)), new MemoryWalletStorage(Duration.ofMinutes(60)));
            if ((walletPruningServiceEnabled || refundServiceEnabled) && (walletPruningService == null || refundWalletService == null)) {
                ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
                if (walletPruningService == null && walletPruningServiceEnabled) {
                    walletPruningService = scheduledExecutorService;
                }
                if (refundWalletService == null && refundServiceEnabled) {
                    refundWalletService = scheduledExecutorService;
                }
            }
            return new NanoPay(this);
        }

    }

}
