package com.terraboxstudios.nanopay;

import com.terraboxstudios.nanopay.death.WalletDeathHandler;
import com.terraboxstudios.nanopay.death.WalletDeathLogger;
import com.terraboxstudios.nanopay.death.WalletDeathState;
import com.terraboxstudios.nanopay.storage.WalletStorageProvider;
import com.terraboxstudios.nanopay.wallet.DeadWallet;
import com.terraboxstudios.nanopay.wallet.SecureRandomUtil;
import com.terraboxstudios.nanopay.wallet.Wallet;
import lombok.SneakyThrows;
import org.jetbrains.annotations.NotNull;
import uk.oczadly.karl.jnano.model.HexData;
import uk.oczadly.karl.jnano.model.NanoAccount;
import uk.oczadly.karl.jnano.model.block.StateBlock;
import uk.oczadly.karl.jnano.model.block.factory.StateBlockFactory;
import uk.oczadly.karl.jnano.rpc.RpcQueryNode;
import uk.oczadly.karl.jnano.util.WalletUtil;
import uk.oczadly.karl.jnano.util.wallet.LocalRpcWalletAccount;
import uk.oczadly.karl.jnano.util.wallet.WalletActionException;
import uk.oczadly.karl.jnano.util.workgen.NodeWorkGenerator;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;

final class WalletManager {

    private final WalletStorageProvider walletStorageProvider;
    private final WalletDeathHandler walletDeathHandler;
    private final WalletDeathLogger walletDeathLogger;
    private final RpcQueryNode rpcClient;
    private final StateBlockFactory blockFactory;
    private final WebSocketListener webSocketListener;
    private final Clock clock;
    WalletManager(@NotNull WalletStorageProvider walletStorageProvider,
                  @NotNull WalletDeathHandler walletDeathHandler,
                  @NotNull WalletDeathLogger walletDeathLogger,
                  @NotNull WebSocketListener webSocketListener,
                  @NotNull RpcQueryNode rpcClient,
                  @NotNull NanoAccount nanoRepresentative,
                  @NotNull Clock clock) {
        this.walletStorageProvider = walletStorageProvider;
        this.walletDeathHandler = walletDeathHandler;
        this.walletDeathLogger = walletDeathLogger;
        this.webSocketListener = webSocketListener;
        this.rpcClient = rpcClient;
        this.blockFactory = new StateBlockFactory(nanoRepresentative, new NodeWorkGenerator(this.rpcClient));
        this.clock = clock;
    }

    WalletDeathLogger getWalletDeathLogger() {
        return walletDeathLogger;
    }

    WalletStorageProvider getWalletStorageProvider() {
        return walletStorageProvider;
    }

    RpcQueryNode getRpcClient() {
        return rpcClient;
    }

    String requestPayment(BigDecimal requiredAmount) {
        Wallet wallet = createWallet(requiredAmount);
        addWallet(wallet);
        return wallet.address();
    }

    void startWalletPruneService(ScheduledExecutorService walletPruneService, NanoPay.RepeatingDelay walletPruneDelay) {
        walletPruneService.scheduleWithFixedDelay(this::pruneWallets, walletPruneDelay.initialDelayAmount(),
                walletPruneDelay.repeatingDelayAmount(), walletPruneDelay.delayUnit());
    }

    void startWalletRefundService(ScheduledExecutorService refundDeadWalletService, NanoPay.RepeatingDelay walletPruneDelay) {
        refundDeadWalletService.scheduleWithFixedDelay(this::refundDeadWallets, walletPruneDelay.initialDelayAmount(),
                walletPruneDelay.repeatingDelayAmount(), walletPruneDelay.delayUnit());
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

    private void addWallet(Wallet wallet) {
        this.walletStorageProvider.activeWalletStorage().saveWallet(wallet);
        this.webSocketListener.addWalletFilter(wallet.address());
    }

    void killWallet(LocalRpcWalletAccount<StateBlock> walletAccount, Wallet wallet, WalletDeathState walletDeathState) {
        this.walletDeathLogger.log(DeadWallet.kill(wallet, walletDeathState.success()));
        this.walletDeathHandler.handleDeath(walletAccount, wallet, walletDeathState);
        this.walletStorageProvider.deadWalletStorage().saveWallet(wallet);
        this.walletStorageProvider.activeWalletStorage().deleteWallet(wallet);
    }

    private void refundDeadWallets() {
        this.walletStorageProvider.deadWalletStorage().getAllWallets()
                .forEach(wallet -> {
                    LocalRpcWalletAccount<StateBlock> walletAccount = getLocalRpcWallet(wallet);
                    try {
                        walletAccount.receiveAll();
                    } catch (WalletActionException ignored) {}
                    walletDeathHandler.refundAllBalance(walletAccount);
                });
    }

    private void pruneWallets() {
        Instant currentTime = clock.instant();
        this.walletStorageProvider.activeWalletStorage().getAllWallets()
                .stream()
                .filter(wallet -> wallet.creationTime().isBefore(
                        currentTime.minus(this.walletStorageProvider.activeWalletStorage().getWalletExpirationTime())))
                .forEach(wallet -> killWallet(getLocalRpcWallet(wallet), wallet, WalletDeathState.failure()));
        this.walletStorageProvider.deadWalletStorage().getAllWallets()
                .stream()
                .filter(wallet -> wallet.creationTime().isBefore(
                        currentTime.minus(this.walletStorageProvider.activeWalletStorage().getWalletExpirationTime())))
                .forEach(this.walletStorageProvider.deadWalletStorage()::deleteWallet);
    }

    @SneakyThrows
    Wallet createWallet(BigDecimal requiredAmount) {
        HexData privateKey = WalletUtil.generateRandomKey(SecureRandomUtil.getSecureRandom());
        String address = NanoAccount.fromPrivateKey(privateKey).toAddress();
        return new Wallet(address, privateKey.toString(), clock.instant(), requiredAmount);
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
