package com.terraboxstudios.nanopay.death;

import com.terraboxstudios.nanopay.wallet.Wallet;
import lombok.AllArgsConstructor;
import lombok.Getter;
import uk.oczadly.karl.jnano.model.NanoAccount;
import uk.oczadly.karl.jnano.model.block.StateBlock;
import uk.oczadly.karl.jnano.rpc.RpcQueryNode;
import uk.oczadly.karl.jnano.util.wallet.LocalRpcWalletAccount;

import java.math.BigDecimal;
import java.util.function.Consumer;

@AllArgsConstructor
public abstract class WalletDeathHandler {

    @Getter
    private final Consumer<String> paymentSuccessListener, paymentFailListener;
    protected final NanoAccount storageWallet;
    protected final RpcQueryNode rpcClient;

    public abstract void handleDeath(LocalRpcWalletAccount<StateBlock> walletAccount,
                                     Wallet wallet,
                                     WalletDeathState walletDeathState);

    public abstract void refundExtraBalance(LocalRpcWalletAccount<StateBlock> walletAccount, BigDecimal requiredAmount);

    public abstract void refundAllBalance(LocalRpcWalletAccount<StateBlock> walletAccount);

}
