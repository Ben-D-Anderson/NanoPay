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
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public final class WalletManager {

    private final WalletStorageProvider walletStorageProvider;
    private final Map<String, LocalRpcWalletAccount<StateBlock>> walletAccounts;
    private final RpcQueryNode rpcClient;
    private final StateBlockFactory blockFactory;
    private final NanoAccount nanoStorageWallet;
    private final Consumer<String> paymentCompletionListener, walletExpireListener;
    private final WebSocketListener webSocketListener;

    WalletManager(@NotNull WalletStorageProvider walletStorageProvider,
                  @NotNull WebSocketListener webSocketListener,
                  @NotNull ScheduledExecutorService walletPruneService,
                  @NotNull NanoAccount nanoStorageWallet,
                  @NotNull RpcQueryNode rpcClient,
                  @NotNull NanoAccount nanoRepresentative,
                  @NotNull Consumer<String> paymentCompletionListener,
                  @NotNull Consumer<String> walletExpireListener) {
        this.walletStorageProvider = walletStorageProvider;
        this.webSocketListener = webSocketListener;
        this.walletAccounts = Collections.synchronizedMap(new HashMap<>());
        this.rpcClient = rpcClient;
        this.nanoStorageWallet = nanoStorageWallet;
        this.blockFactory = new StateBlockFactory(nanoRepresentative, new NodeWorkGenerator(this.rpcClient));
        this.loadWallets();
        this.paymentCompletionListener = paymentCompletionListener;
        this.walletExpireListener = walletExpireListener;
        walletPruneService.scheduleWithFixedDelay(this::pruneWallets, 1, 1, TimeUnit.MINUTES);
    }

    @SneakyThrows
    String newPayment(BigDecimal requiredNano) {
        LocalRpcWalletAccount<StateBlock> walletAccount = new LocalRpcWalletAccount<>(WalletUtil.generateRandomKey(), rpcClient, blockFactory);
        addWallet(walletAccount, requiredNano);
        return walletAccount.getAccount().toAddress();
    }

    private void loadWallets() {
        this.walletStorageProvider.getAllWallets().forEach(wallet -> {
            LocalRpcWalletAccount<StateBlock> walletAccount = new LocalRpcWalletAccount<>(new HexData(wallet.getPrivateKey()), rpcClient, blockFactory);
            addWalletNoStorage(walletAccount);
            try {
                checkWallet(walletAccount.getAccount().toAddress());
            } catch (WalletActionException e) {
                NanoPay.LOGGER.error("Failed to check balance of receiving wallet (" + wallet + ").", e);
            }
        });
    }

    private void addWallet(LocalRpcWalletAccount<StateBlock> walletAccount, BigDecimal requiredNano) {
        this.walletStorageProvider.storeWallet(new Wallet(walletAccount.getAccount().toAddress(), walletAccount.getPrivateKey().toHexString(), Instant.now(), requiredNano));
        addWalletNoStorage(walletAccount);
    }

    private void addWalletNoStorage(LocalRpcWalletAccount<StateBlock> walletAccount) {
        this.walletAccounts.put(walletAccount.getAccount().toAddress(), walletAccount);
        this.webSocketListener.addWalletFilter(walletAccount.getAccount().toAddress());
    }

    private void removeWallet(Wallet wallet, boolean paymentSuccess, boolean moreThanRequested) {
        LocalRpcWalletAccount<StateBlock> walletAccount = this.walletAccounts.get(wallet.getAddress());
        if (paymentSuccess) {
            try {
                walletAccount.send(this.nanoStorageWallet, NanoAmount.valueOfNano(wallet.getRequiredNano()));
            } catch (WalletActionException e) {
                NanoPay.LOGGER.error("Couldn't send funds from receiving wallet (" + wallet + ") to storage wallet (" + this.nanoStorageWallet + ").", e);
            }
            if (moreThanRequested) {
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
        this.walletStorageProvider.deleteWallet(wallet);
        this.walletAccounts.remove(wallet.getAddress());
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
                    .forEach(blockInfo -> {
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

    private void pruneWallets() {
        Instant currentTime = Instant.now();
        this.walletStorageProvider.getAllWallets()
                .stream()
                .filter(wallet -> wallet.getCreationTime().isBefore(currentTime.minus(this.walletStorageProvider.getWalletExpirationTime())))
                .forEach(wallet -> removeWallet(wallet, false, false));
    }

    boolean isActiveWallet(String address) {
        return walletAccounts.containsKey(address);
    }

    void checkWallet(String address) throws WalletActionException {
        Optional<Wallet> walletOptional = this.walletStorageProvider.getWallet(address);
        if (!walletOptional.isPresent()) {
            NanoPay.LOGGER.warn("Received wallet check for unrecognised address (" + address + ").");
            return;
        }
        Wallet wallet = walletOptional.get();
        LocalRpcWalletAccount<StateBlock> walletAccount = this.walletAccounts.get(address);
        walletAccount.receiveAll();
        BigDecimal extraToRefund = walletAccount.getBalance().getAsNano().subtract(wallet.getRequiredNano());
        int comparisonResult = extraToRefund.compareTo(BigDecimal.ZERO);
        if (comparisonResult >= 0) {
            removeWallet(wallet, true, comparisonResult > 0);
        }
    }

}
