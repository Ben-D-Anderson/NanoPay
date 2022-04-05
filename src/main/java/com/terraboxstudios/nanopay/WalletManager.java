package com.terraboxstudios.nanopay;

import com.terraboxstudios.nanopay.storage.WalletStorageProvider;
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
import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public final class WalletManager {

    private final WalletStorageProvider walletStorageProvider;
    private final RpcQueryNode rpcClient;
    private final StateBlockFactory blockFactory;
    private final NanoAccount nanoStorageWallet;
    private final Consumer<String> paymentCompletionListener, walletExpireListener;
    private final WebSocketListener webSocketListener;
    private final Clock clock;

    WalletManager(@NotNull WalletStorageProvider walletStorageProvider,
                  @NotNull WebSocketListener webSocketListener,
                  @NotNull NanoAccount nanoStorageWallet,
                  @NotNull RpcQueryNode rpcClient,
                  @NotNull NanoAccount nanoRepresentative,
                  @NotNull Consumer<String> paymentCompletionListener,
                  @NotNull Consumer<String> walletExpireListener,
                  @NotNull Clock clock) {
        this.walletStorageProvider = walletStorageProvider;
        this.webSocketListener = webSocketListener;
        this.rpcClient = rpcClient;
        this.nanoStorageWallet = nanoStorageWallet;
        this.blockFactory = new StateBlockFactory(nanoRepresentative, new NodeWorkGenerator(this.rpcClient));
        this.paymentCompletionListener = paymentCompletionListener;
        this.walletExpireListener = walletExpireListener;
        this.clock = clock;
    }

    @SneakyThrows
    String requestPayment(BigDecimal requiredNano) {
        LocalRpcWalletAccount<StateBlock> walletAccount = new LocalRpcWalletAccount<>(WalletUtil.generateRandomKey(), rpcClient, blockFactory);
        addWallet(walletAccount, requiredNano);
        return walletAccount.getAccount().toAddress();
    }

    void startPruningService(ScheduledExecutorService walletPruningService, Duration walletPruningDelay) {
        walletPruningService.scheduleWithFixedDelay(this::pruneWallets, walletPruningDelay.getSeconds(), (walletPruningDelay.getSeconds() * 1_000_000_000) + walletPruningDelay.getNano(), TimeUnit.NANOSECONDS);
    }

    void startRefundService(ScheduledExecutorService refundDeadWalletService, Duration refundWalletDelay) {
        refundDeadWalletService.scheduleWithFixedDelay(this::refundDeadWallets, refundWalletDelay.getSeconds(), (refundWalletDelay.getSeconds() * 1_000_000_000) + refundWalletDelay.getNano(), TimeUnit.NANOSECONDS);
    }

    void loadWallets() {
        this.walletStorageProvider.getActiveWalletStorage().getAllWallets().forEach(wallet -> {
            this.webSocketListener.addWalletFilter(wallet.getAddress());
            try {
                checkWallet(wallet.getAddress());
            } catch (WalletActionException e) {
                NanoPay.LOGGER.error("Failed to check up on receiving wallet (" + wallet + ").", e);
            }
        });
    }

    private void addWallet(LocalRpcWalletAccount<StateBlock> walletAccount, BigDecimal requiredNano) {
        Wallet wallet = new Wallet(walletAccount.getAccount().toAddress(), walletAccount.getPrivateKey().toHexString(), Instant.now(clock), requiredNano);
        this.walletStorageProvider.getActiveWalletStorage().saveWallet(wallet);
        this.webSocketListener.addWalletFilter(wallet.getAddress());
    }

    private void removeWallet(LocalRpcWalletAccount<StateBlock> walletAccount, Wallet wallet, boolean paymentSuccess, boolean receivedMoreThanRequested) {
        if (paymentSuccess) {
            NanoPay.LOGGER.debug("Wallet " + wallet.getAddress() + " received enough NANO to complete payment");
            try {
                walletAccount.send(this.nanoStorageWallet, NanoAmount.valueOfNano(wallet.getRequiredNano()));
            } catch (WalletActionException e) {
                NanoPay.LOGGER.error("Couldn't send funds from receiving wallet (" + wallet.getAddress() + ") to storage wallet (" + this.nanoStorageWallet + ").", e);
            }
            if (receivedMoreThanRequested) {
                refundExtraBalance(walletAccount, wallet.getRequiredNano());
            }
            this.paymentCompletionListener.accept(wallet.getAddress());
        } else {
            try {
                if (walletAccount.getBalance().compareTo(NanoAmount.ZERO) > 0) {
                    refundAllBalance(wallet, walletAccount);
                }
            } catch (WalletActionException e) {
                NanoPay.LOGGER.error("Couldn't get balance of receiving wallet (" + wallet + ").", e);
            }
            walletExpireListener.accept(wallet.getAddress());
        }
        this.walletStorageProvider.getActiveWalletStorage().deleteWallet(wallet);
        this.walletStorageProvider.getDeadWalletStorage().saveWallet(wallet);
    }

    private void refundExtraBalance(LocalRpcWalletAccount<StateBlock> walletAccount, BigDecimal requiredNano) {
        try {
            RequestAccountHistory requestAccountHistory = new RequestAccountHistory(walletAccount.getAccount().toAddress());
            ResponseAccountHistory responseAccountHistory = rpcClient.processRequest(requestAccountHistory);
            List<ResponseAccountHistory.BlockInfo> receivedPayments = responseAccountHistory.getHistory()
                    .stream()
                    .filter(blockInfo -> blockInfo.getType() == BlockType.RECEIVE)
                    .sorted(Comparator.comparing(ResponseAccountHistory.BlockInfo::getTimestamp))
                    .collect(Collectors.toList());
            BigDecimal totalNano = BigDecimal.ZERO;
            boolean surpassedRequired = false;
            for (ResponseAccountHistory.BlockInfo payment : receivedPayments) {
                totalNano = totalNano.add(payment.getAmount().getAsNano());
                if (!surpassedRequired && totalNano.compareTo(requiredNano) > 0) {
                    surpassedRequired = true;
                    BigDecimal totalExtra = totalNano.subtract(requiredNano);
                    try {
                        walletAccount.send(payment.getAccount(), NanoAmount.valueOfNano(totalExtra));
                    } catch (WalletActionException e) {
                        NanoPay.LOGGER.error("Couldn't refund NANO to sender of NANO to wallet (" + walletAccount.getAccount().toAddress() + ").", e);
                    }
                } else if (surpassedRequired) {
                    try {
                        walletAccount.send(payment.getAccount(), payment.getAmount());
                    } catch (WalletActionException e) {
                        NanoPay.LOGGER.error("Couldn't refund NANO to sender of NANO to wallet (" + walletAccount.getAccount().toAddress() + ").", e);
                    }
                }
            }
        } catch (IOException | RpcException e) {
            e.printStackTrace();
        }
    }

    private void refundAllBalance(Wallet wallet, LocalRpcWalletAccount<StateBlock> walletAccount) {
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
                            NanoPay.LOGGER.error("Couldn't refund NANO to sender of NANO to wallet (" + wallet + ").", e);
                        }
                    });
        } catch (RpcException | IOException e) {
            NanoPay.LOGGER.error("Couldn't refund NANO to senders of NANO to wallet (" + wallet + ").", e);
            try {
                walletAccount.sendAll(nanoStorageWallet);
            } catch (WalletActionException ex) {
                NanoPay.LOGGER.error("Couldn't send unwanted NANO from receiving wallet (" + wallet + ") to storage wallet (" + this.nanoStorageWallet + ") after failing to refund.", e);
            }
        }
    }

    private void refundDeadWallets() {
        this.walletStorageProvider.getDeadWalletStorage().getAllWallets()
                .forEach(wallet -> {
                    LocalRpcWalletAccount<StateBlock> walletAccount = new LocalRpcWalletAccount<>(new HexData(wallet.getPrivateKey()), rpcClient, blockFactory);
                    try {
                        walletAccount.receiveAll();
                    } catch (WalletActionException ignored) {}
                    refundAllBalance(wallet, walletAccount);
                });
    }

    private void pruneWallets() {
        Instant currentTime = Instant.now(clock);
        this.walletStorageProvider.getActiveWalletStorage().getAllWallets()
                .stream()
                .filter(wallet -> wallet.getCreationTime().isBefore(currentTime.minus(this.walletStorageProvider.getActiveWalletStorage().getWalletExpirationTime())))
                .forEach(wallet -> removeWallet(new LocalRpcWalletAccount<>(new HexData(wallet.getPrivateKey()), rpcClient, blockFactory), wallet, false, false));
        this.walletStorageProvider.getDeadWalletStorage().getAllWallets()
                .stream()
                .filter(wallet -> wallet.getCreationTime().isBefore(currentTime.minus(this.walletStorageProvider.getActiveWalletStorage().getWalletExpirationTime())))
                .forEach(this.walletStorageProvider.getDeadWalletStorage()::deleteWallet);
    }

    void checkWallet(String address) throws WalletActionException {
        Optional<Wallet> walletOptional = this.walletStorageProvider.getActiveWalletStorage().findWalletByAddress(address);
        if (!walletOptional.isPresent()) {
            return;
        }
        Wallet wallet = walletOptional.get();
        LocalRpcWalletAccount<StateBlock> walletAccount = new LocalRpcWalletAccount<>(new HexData(wallet.getPrivateKey()), rpcClient, blockFactory);
        walletAccount.receiveAll();
        BigDecimal extraToRefund = walletAccount.getBalance().getAsNano().subtract(wallet.getRequiredNano());
        int comparisonResult = extraToRefund.compareTo(BigDecimal.ZERO);
        if (comparisonResult >= 0) {
            removeWallet(walletAccount, wallet, true, comparisonResult > 0);
        }
    }

}
