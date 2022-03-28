package com.terraboxstudios.nanopay;

import com.terraboxstudios.nanopay.storage.WalletStorageProvider;
import lombok.SneakyThrows;
import org.apache.log4j.Logger;
import uk.oczadly.karl.jnano.model.HexData;
import uk.oczadly.karl.jnano.model.NanoAccount;
import uk.oczadly.karl.jnano.model.NanoAmount;
import uk.oczadly.karl.jnano.model.block.BlockType;
import uk.oczadly.karl.jnano.rpc.RpcQueryNode;
import uk.oczadly.karl.jnano.rpc.exception.RpcException;
import uk.oczadly.karl.jnano.rpc.request.node.RequestAccountHistory;
import uk.oczadly.karl.jnano.rpc.response.ResponseAccountHistory;
import uk.oczadly.karl.jnano.rpc.util.wallet.LocalRpcWalletAccount;
import uk.oczadly.karl.jnano.rpc.util.wallet.WalletActionException;
import uk.oczadly.karl.jnano.util.WalletUtil;
import uk.oczadly.karl.jnano.util.blockproducer.BlockProducer;
import uk.oczadly.karl.jnano.util.blockproducer.BlockProducerSpecification;
import uk.oczadly.karl.jnano.util.blockproducer.StateBlockProducer;
import uk.oczadly.karl.jnano.util.workgen.NodeWorkGenerator;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class WalletManager {

    private static final Logger LOGGER = Logger.getLogger("com.terraboxstudios.nanopay");

    private final WalletStorageProvider walletStorageProvider;
    private final Map<String, LocalRpcWalletAccount> walletAccounts;
    private final RpcQueryNode rpcClient;
    private final BlockProducer blockProducer;
    private final NanoAccount nanoStorageWallet;
    private final Consumer<String> paymentCompletionListener;

    public WalletManager(WalletStorageProvider walletStorageProvider,
                         ScheduledExecutorService walletPruneService,
                         NanoAccount nanoStorageWallet,
                         RpcQueryNode rpcClient,
                         NanoAccount nanoRepresentative,
                         Consumer<String> paymentCompletionListener) {
        this.walletStorageProvider = walletStorageProvider;
        this.walletAccounts = Collections.synchronizedMap(new HashMap<>());
        this.rpcClient = rpcClient;
        this.nanoStorageWallet = nanoStorageWallet;
        this.blockProducer = new StateBlockProducer(
                BlockProducerSpecification.builder()
                        .defaultRepresentative(nanoRepresentative)
                        .workGenerator(new NodeWorkGenerator(this.rpcClient))
                        .build()
        );
        this.loadWallets();
        this.paymentCompletionListener = paymentCompletionListener;
        walletPruneService.scheduleWithFixedDelay(this::pruneWallets, 1, 1, TimeUnit.MINUTES);
    }

    /**
     * Creates a nano wallet, stores it in the WalletStorageProvider used by this object
     * and adds the wallet to the event loop.
     * @return wallet address
     */
    @SneakyThrows
    public String newWallet(BigDecimal requiredNano) {
        LocalRpcWalletAccount walletAccount = new LocalRpcWalletAccount(WalletUtil.generateRandomKey(), rpcClient, blockProducer);
        addWallet(walletAccount, requiredNano);
        return walletAccount.getAccount().toAddress();
    }

    private void loadWallets() {
        this.walletStorageProvider.getAllWallets().forEach(wallet -> {
            LocalRpcWalletAccount walletAccount = new LocalRpcWalletAccount(new HexData(wallet.getPrivateKey()), rpcClient, blockProducer);
            this.walletAccounts.put(walletAccount.getAccount().toAddress(), walletAccount);
            try {
                checkWallet(walletAccount.getAccount().toAddress());
            } catch (WalletActionException e) {
                LOGGER.error("Failed to check balance of receiving wallet (" + wallet + ").", e);
            }
        });
    }

    private void addWallet(LocalRpcWalletAccount walletAccount, BigDecimal requiredNano) {
        this.walletStorageProvider.storeWallet(new Wallet(walletAccount.getAccount().toAddress(), walletAccount.getPrivateKey().toHexString(), Instant.now(), requiredNano));
        this.walletAccounts.put(walletAccount.getAccount().toAddress(), walletAccount);
        //todo add wallet address to websocket block listener
    }

    private void removeWallet(Wallet wallet, boolean paymentSuccess, BigDecimal extraToRefund) {
        //todo remove wallet address from websocket block listener
        if (paymentSuccess) {
            LocalRpcWalletAccount walletAccount = this.walletAccounts.get(wallet.getAddress());
            try {
                walletAccount.send(this.nanoStorageWallet, NanoAmount.valueOfNano(wallet.getRequiredNano()));
            } catch (WalletActionException e) {
                LOGGER.error("Couldn't send funds from receiving wallet (" + wallet + ") to storage wallet (" + this.nanoStorageWallet + ").", e);
            }
            if (extraToRefund.compareTo(BigDecimal.ZERO) > 0) {
                refundBalance(wallet, walletAccount, extraToRefund);
            }
            this.paymentCompletionListener.accept(wallet.getAddress());
        }
        this.walletStorageProvider.deleteWallet(wallet);
        this.walletAccounts.remove(wallet.getAddress());
    }

    private void refundBalance(Wallet wallet, LocalRpcWalletAccount walletAccount, BigDecimal extraToRefund) {
        try {
            NanoAccount mostReceiveAccount = getMostReceiveAccount(walletAccount.getAccount())
                    .orElseThrow(() -> new WalletActionException("Couldn't find last NANO sender"));
            walletAccount.send(mostReceiveAccount, NanoAmount.valueOfNano(extraToRefund));
        } catch (RpcException | IOException | WalletActionException e) {
            LOGGER.error("Couldn't get last NANO sender for receiving wallet (" + wallet + ").", e);
            try {
                walletAccount.sendAll(nanoStorageWallet);
            } catch (WalletActionException ex) {
                LOGGER.error("Couldn't send extra NANO from receiving wallet (" + wallet + ") to storage wallet (" + this.nanoStorageWallet + ") after failing to refund.", e);
            }
        }
    }

    private Optional<NanoAccount> getMostReceiveAccount(NanoAccount account) throws RpcException, IOException {
        RequestAccountHistory requestAccountHistory = new RequestAccountHistory(account.toAddress());
        ResponseAccountHistory responseAccountHistory = rpcClient.processRequest(requestAccountHistory);
        return responseAccountHistory.getHistory()
                .stream()
                .filter(blockInfo -> blockInfo.getType() == BlockType.RECEIVE)
                .max(Comparator.comparing(ResponseAccountHistory.BlockInfo::getAmount))
                .map(ResponseAccountHistory.BlockInfo::getAccount);
    }

    private void pruneWallets() {
        Instant currentTime = Instant.now();
        this.walletStorageProvider.getAllWallets()
                .stream()
                .filter(wallet -> wallet.getCreationTime().isBefore(currentTime.minus(this.walletStorageProvider.getWalletExpirationTime())))
                .forEach(wallet -> removeWallet(wallet, false, BigDecimal.ZERO));
    }

    //todo have websocket listener call this method on block events
    public void checkWallet(String address) throws WalletActionException {
        Wallet wallet = this.walletStorageProvider.getWallet(address);
        LocalRpcWalletAccount walletAccount = this.walletAccounts.get(address);
        walletAccount.receiveAll();
        BigDecimal extraToRefund = walletAccount.getBalance().getAsNano().subtract(wallet.getRequiredNano());
        if (extraToRefund.compareTo(BigDecimal.ZERO) >= 0) {
            removeWallet(wallet, true, extraToRefund);
        }
    }

}
