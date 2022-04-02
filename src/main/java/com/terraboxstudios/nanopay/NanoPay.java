package com.terraboxstudios.nanopay;

import com.terraboxstudios.nanopay.storage.MemoryWalletStorageProvider;
import com.terraboxstudios.nanopay.storage.WalletStorageProvider;
import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.oczadly.karl.jnano.model.NanoAccount;
import uk.oczadly.karl.jnano.rpc.RpcQueryNode;
import uk.oczadly.karl.jnano.util.wallet.WalletActionException;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URL;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;

public class NanoPay {

    static final Logger LOGGER = LoggerFactory.getLogger(NanoPay.class);
    private final WalletManager walletManager;

    private NanoPay(NanoPay.Builder builder) {
        WebSocketListener webSocketListener = new WebSocketListener(URI.create(builder.webSocketAddress), builder.webSocketReconnect);

        walletManager = new WalletManager(
                builder.walletStorageProvider,
                webSocketListener,
                builder.walletPruneService,
                NanoAccount.parseAddress(builder.storageWalletAddress),
                new RpcQueryNode(builder.rpcAddress),
                NanoAccount.parseAddress(builder.representativeAddress),
                builder.paymentSuccessListener,
                builder.paymentFailListener
        );

        webSocketListener.connectWebSocket(transaction -> {
            NanoPay.LOGGER.debug("Wallet " + transaction.getReceiver() + " received " + transaction.getAmount().toNanoString() + " NANO");
            try {
                walletManager.checkWallet(transaction.getReceiver().toAddress());
            } catch (WalletActionException e) {
                NanoPay.LOGGER.error("Exception occurred checking wallet (" + transaction.getReceiver().toAddress() + ")", e);
            }
        }, walletManager::isActiveWallet);
    }

    /**
     * Creates a nano wallet, stores it in the WalletStorageProvider from the builder,
     * and adds the wallet to the event loop.
     * @param amount The amount of NANO to receive
     * @return NANO address the payment should be sent to
     */
    public String newPayment(BigDecimal amount) {
        return walletManager.newPayment(amount);
    }

    public static class Builder {

        private final Consumer<String> paymentSuccessListener;
        private final String storageWalletAddress;
        private URL rpcAddress;
        private String webSocketAddress = "wss://socket.nanos.cc/";
        private String representativeAddress = "nano_1natrium1o3z5519ifou7xii8crpxpk8y65qmkih8e8bpsjri651oza8imdd";
        private WalletStorageProvider walletStorageProvider;
        private ScheduledExecutorService walletPruneService;
        private Consumer<String> paymentFailListener = walletAddress -> {};
        private boolean webSocketReconnect = false;

        @SneakyThrows
        public Builder(String storageWalletAddress, Consumer<String> paymentSuccessListener) {
            this.storageWalletAddress = storageWalletAddress;
            this.paymentSuccessListener = paymentSuccessListener;
            this.rpcAddress = new URL("https://proxy.nanos.cc/proxy");
        }

        public Builder setRepresentativeAddress(String representativeAddress) {
            this.representativeAddress = representativeAddress;
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

        public Builder setWalletPruneService(ScheduledExecutorService walletPruneService) {
            this.walletPruneService = walletPruneService;
            return this;
        }

        public NanoPay build() {
            if (walletStorageProvider == null) walletStorageProvider = new MemoryWalletStorageProvider();
            if (walletPruneService == null) walletPruneService = Executors.newSingleThreadScheduledExecutor();
            return new NanoPay(this);
        }

    }

}
