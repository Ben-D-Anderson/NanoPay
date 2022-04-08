package com.terraboxstudios.nanopay;

import com.terraboxstudios.nanopay.storage.WalletStorageProvider;
import com.terraboxstudios.nanopay.util.SecureRandomUtil;
import com.terraboxstudios.nanopay.wallet.Wallet;
import lombok.SneakyThrows;
import org.jetbrains.annotations.NotNull;
import uk.oczadly.karl.jnano.model.HexData;
import uk.oczadly.karl.jnano.model.NanoAccount;
import uk.oczadly.karl.jnano.model.NanoAmount;
import uk.oczadly.karl.jnano.model.block.BlockType;
import uk.oczadly.karl.jnano.model.block.StateBlock;
import uk.oczadly.karl.jnano.model.block.factory.StateBlockFactory;
import uk.oczadly.karl.jnano.rpc.RpcQueryNode;
import uk.oczadly.karl.jnano.rpc.exception.RpcException;
import uk.oczadly.karl.jnano.rpc.request.node.RequestAccountHistory;
import uk.oczadly.karl.jnano.rpc.response.ResponseAccountHistory;
import uk.oczadly.karl.jnano.util.WalletUtil;
import uk.oczadly.karl.jnano.util.wallet.LocalRpcWalletAccount;
import uk.oczadly.karl.jnano.util.wallet.WalletActionException;
import uk.oczadly.karl.jnano.util.workgen.NodeWorkGenerator;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

final class WalletManager {

    private final WalletStorageProvider walletStorageProvider;
    private final RpcQueryNode rpcClient;
    private final StateBlockFactory blockFactory;
    private final NanoAccount nanoStorageWallet;
    private final Consumer<? super String> paymentSuccessListener, paymentFailListener;
    private final WebSocketListener webSocketListener;
    private final Clock clock;

    WalletManager(@NotNull WalletStorageProvider walletStorageProvider,
                  @NotNull WebSocketListener webSocketListener,
                  @NotNull NanoAccount nanoStorageWallet,
                  @NotNull RpcQueryNode rpcClient,
                  @NotNull NanoAccount nanoRepresentative,
                  @NotNull Consumer<? super String> paymentSuccessListener,
                  @NotNull Consumer<? super String> paymentFailListener,
                  @NotNull Clock clock) {
        this.walletStorageProvider = walletStorageProvider;
        this.webSocketListener = webSocketListener;
        this.rpcClient = rpcClient;
        this.nanoStorageWallet = nanoStorageWallet;
        this.blockFactory = new StateBlockFactory(nanoRepresentative, new NodeWorkGenerator(this.rpcClient));
        this.paymentSuccessListener = paymentSuccessListener;
        this.paymentFailListener = paymentFailListener;
        this.clock = clock;
    }

    @SneakyThrows
    String requestPayment(BigDecimal requiredAmount) {
        LocalRpcWalletAccount<StateBlock> walletAccount =
                new LocalRpcWalletAccount<>(WalletUtil.generateRandomKey(SecureRandomUtil.getSecureRandom()),
                        rpcClient,
                        blockFactory);
        addWallet(walletAccount, requiredAmount);
        return walletAccount.getAccount().toAddress();
    }

    void startPruningService(ScheduledExecutorService walletPruningService, Duration walletPruningDelay) {
        walletPruningService.scheduleWithFixedDelay(this::pruneWallets, walletPruningDelay.getSeconds(), (walletPruningDelay.getSeconds() * 1_000_000_000) + walletPruningDelay.getNano(), TimeUnit.NANOSECONDS);
    }

    void startRefundService(ScheduledExecutorService refundDeadWalletService, Duration refundWalletDelay) {
        refundDeadWalletService.scheduleWithFixedDelay(this::refundDeadWallets, refundWalletDelay.getSeconds(), (refundWalletDelay.getSeconds() * 1_000_000_000) + refundWalletDelay.getNano(), TimeUnit.NANOSECONDS);
    }

    void loadWallets() {
        this.walletStorageProvider.activeWalletStorage().getAllWallets().forEach(wallet -> {
            this.webSocketListener.addWalletFilter(wallet.address());
            try {
                checkWallet(getLocalRpcWallet(wallet), wallet);
            } catch (WalletActionException e) {
                NanoPay.LOGGER.error("Failed to check up on receiving wallet (" + wallet + ").", e);
            }
        });
    }

    private void addWallet(LocalRpcWalletAccount<StateBlock> walletAccount, BigDecimal requiredAmount) {
        Wallet wallet = new Wallet(walletAccount.getAccount().toAddress(),
                walletAccount.getPrivateKey().toHexString(),
                Instant.now(clock),
                requiredAmount);
        this.walletStorageProvider.activeWalletStorage().saveWallet(wallet);
        this.webSocketListener.addWalletFilter(wallet.address());
    }

    record WalletDeathState(boolean success, boolean receivedExtra) {
        static WalletDeathState failure() {
            return new WalletDeathState(false, false);
        }
        static WalletDeathState success(boolean receivedExtra) {
            return new WalletDeathState(true, receivedExtra);
        }
    }

    void killWallet(LocalRpcWalletAccount<StateBlock> walletAccount, Wallet wallet, WalletDeathState walletDeathState) {
        if (walletDeathState.success()) {
            NanoPay.LOGGER.debug("Wallet " + wallet.address() + " received enough NANO to complete payment");
            try {
                walletAccount.send(this.nanoStorageWallet, NanoAmount.valueOfNano(wallet.requiredAmount()));
            } catch (WalletActionException e) {
                NanoPay.LOGGER.error("Couldn't send funds from receiving wallet (" + wallet.address() + ")" +
                        " to storage wallet (" + this.nanoStorageWallet + ").", e);
            }
            if (walletDeathState.receivedExtra()) {
                refundExtraBalance(walletAccount, wallet.requiredAmount());
            }
            this.paymentSuccessListener.accept(wallet.address());
        } else {
            try {
                if (walletAccount.getBalance().compareTo(NanoAmount.ZERO) > 0) {
                    refundAllBalance(walletAccount);
                }
            } catch (WalletActionException e) {
                NanoPay.LOGGER.error("Couldn't get balance of receiving wallet (" + wallet + ").", e);
            }
            this.paymentFailListener.accept(wallet.address());
        }
        this.walletStorageProvider.deadWalletStorage().saveWallet(wallet);
        this.walletStorageProvider.activeWalletStorage().deleteWallet(wallet);
    }

    void refundExtraBalance(LocalRpcWalletAccount<StateBlock> walletAccount, BigDecimal requiredAmount) {
        try {
            RequestAccountHistory requestAccountHistory = new RequestAccountHistory(walletAccount.getAccount().toAddress());
            ResponseAccountHistory responseAccountHistory = rpcClient.processRequest(requestAccountHistory);
            List<ResponseAccountHistory.BlockInfo> receivedPayments = responseAccountHistory.getHistory()
                    .stream()
                    .filter(blockInfo -> blockInfo.getType() == BlockType.RECEIVE)
                    .sorted(Comparator.comparing(ResponseAccountHistory.BlockInfo::getTimestamp))
                    .toList();
            BigDecimal totalNano = BigDecimal.ZERO;
            boolean surpassedRequired = false;
            for (ResponseAccountHistory.BlockInfo payment : receivedPayments) {
                totalNano = totalNano.add(payment.getAmount().getAsNano());
                if (!surpassedRequired && totalNano.compareTo(requiredAmount) > 0) {
                    surpassedRequired = true;
                    BigDecimal totalExtra = totalNano.subtract(requiredAmount);
                    try {
                        walletAccount.send(payment.getAccount(), NanoAmount.valueOfNano(totalExtra));
                    } catch (WalletActionException e) {
                        NanoPay.LOGGER.error("Couldn't refund NANO to sender of NANO to wallet ("
                                + walletAccount.getAccount().toAddress() + ").", e);
                    }
                } else if (surpassedRequired) {
                    try {
                        walletAccount.send(payment.getAccount(), payment.getAmount());
                    } catch (WalletActionException e) {
                        NanoPay.LOGGER.error("Couldn't refund NANO to sender of NANO to wallet ("
                                + walletAccount.getAccount().toAddress() + ").", e);
                    }
                }
            }
        } catch (IOException | RpcException e) {
            e.printStackTrace();
        }
    }

    void refundAllBalance(LocalRpcWalletAccount<StateBlock> walletAccount) {
        try {
            RequestAccountHistory requestAccountHistory = new RequestAccountHistory(walletAccount.getAccount().toAddress());
            ResponseAccountHistory responseAccountHistory = rpcClient.processRequest(requestAccountHistory);
            responseAccountHistory.getHistory()
                    .stream()
                    .filter(blockInfo -> blockInfo.getType() == BlockType.RECEIVE)
                    .sorted(Comparator.comparing(ResponseAccountHistory.BlockInfo::getTimestamp).reversed())
                    .forEach(blockInfo -> {
                        try {
                            if (walletAccount.getBalance().compareTo(blockInfo.getAmount()) < 0) {
                                return;
                            }
                        } catch (WalletActionException e) {
                            return;
                        }
                        try {
                            walletAccount.send(blockInfo.getAccount(), blockInfo.getAmount());
                        } catch (WalletActionException e) {
                            NanoPay.LOGGER.error("Couldn't refund NANO to sender of NANO to wallet ("
                                    + walletAccount.getAccount().toAddress() + ").", e);
                        }
                    });
        } catch (RpcException | IOException e) {
            NanoPay.LOGGER.error("Couldn't refund NANO to senders of NANO to wallet ("
                    + walletAccount.getAccount().toAddress() + ").", e);
            try {
                walletAccount.sendAll(nanoStorageWallet);
            } catch (WalletActionException ex) {
                NanoPay.LOGGER.error("Couldn't send unwanted NANO from receiving wallet ("
                        + walletAccount.getAccount().toAddress() + ")" + " to storage wallet ("
                        + this.nanoStorageWallet + ") after failing to refund.", e);
            }
        }
    }

    private void refundDeadWallets() {
        this.walletStorageProvider.deadWalletStorage().getAllWallets()
                .forEach(wallet -> {
                    LocalRpcWalletAccount<StateBlock> walletAccount = new LocalRpcWalletAccount<>(
                            new HexData(wallet.privateKey()),
                            rpcClient,
                            blockFactory
                    );
                    try {
                        walletAccount.receiveAll();
                    } catch (WalletActionException ignored) {}
                    refundAllBalance(walletAccount);
                });
    }

    private void pruneWallets() {
        Instant currentTime = Instant.now(clock);
        this.walletStorageProvider.activeWalletStorage().getAllWallets()
                .stream()
                .filter(wallet -> wallet.creationTime().isBefore(
                        currentTime.minus(this.walletStorageProvider.activeWalletStorage().getWalletExpirationTime())))
                .forEach(wallet -> killWallet(new LocalRpcWalletAccount<>(
                        new HexData(wallet.privateKey()), rpcClient, blockFactory), wallet, WalletDeathState.failure()
                ));
        this.walletStorageProvider.deadWalletStorage().getAllWallets()
                .stream()
                .filter(wallet -> wallet.creationTime().isBefore(
                        currentTime.minus(this.walletStorageProvider.activeWalletStorage().getWalletExpirationTime())))
                .forEach(this.walletStorageProvider.deadWalletStorage()::deleteWallet);
    }

    Optional<Wallet> getWallet(String address) {
        return this.walletStorageProvider.activeWalletStorage().findWalletByAddress(address);
    }

    LocalRpcWalletAccount<StateBlock> getLocalRpcWallet(Wallet wallet) {
        return new LocalRpcWalletAccount<>(new HexData(wallet.privateKey()), rpcClient, blockFactory);
    }

    void checkWallet(LocalRpcWalletAccount<StateBlock> walletAccount, Wallet wallet) throws WalletActionException {
        walletAccount.receiveAll();
        BigDecimal extraToRefund = walletAccount.getBalance().getAsNano().subtract(wallet.requiredAmount());
        int comparisonResult = extraToRefund.compareTo(BigDecimal.ZERO);
        if (comparisonResult >= 0) {
            killWallet(walletAccount, wallet, WalletDeathState.success(comparisonResult > 0));
        }
    }

}
