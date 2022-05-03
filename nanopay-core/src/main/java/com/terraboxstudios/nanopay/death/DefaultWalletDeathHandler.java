package com.terraboxstudios.nanopay.death;

import com.terraboxstudios.nanopay.NanoPay;
import com.terraboxstudios.nanopay.wallet.Wallet;
import uk.oczadly.karl.jnano.model.NanoAccount;
import uk.oczadly.karl.jnano.model.NanoAmount;
import uk.oczadly.karl.jnano.model.block.BlockType;
import uk.oczadly.karl.jnano.model.block.StateBlock;
import uk.oczadly.karl.jnano.rpc.RpcQueryNode;
import uk.oczadly.karl.jnano.rpc.exception.RpcException;
import uk.oczadly.karl.jnano.rpc.request.node.RequestAccountHistory;
import uk.oczadly.karl.jnano.rpc.response.ResponseAccountHistory;
import uk.oczadly.karl.jnano.util.wallet.LocalRpcWalletAccount;
import uk.oczadly.karl.jnano.util.wallet.WalletActionException;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

public class DefaultWalletDeathHandler extends WalletDeathHandler {

    public DefaultWalletDeathHandler(Consumer<String> paymentSuccessListener,
                                     Consumer<String> paymentFailListener,
                                     NanoAccount storageWallet,
                                     RpcQueryNode rpcClient) {
        super(paymentSuccessListener, paymentFailListener, storageWallet, rpcClient);
    }

    @Override
    public void handleDeath(LocalRpcWalletAccount<StateBlock> walletAccount,
                            Wallet wallet,
                            WalletDeathState walletDeathState) {
        if (walletDeathState.success()) {
            try {
                walletAccount.send(storageWallet, NanoAmount.valueOfNano(wallet.requiredAmount()));
            } catch (WalletActionException e) {
                NanoPay.LOGGER.error("Couldn't send funds from receiving wallet (" + wallet.address() + ")" +
                        " to storage wallet (" + storageWallet + ").", e);
            }
            if (walletDeathState.receivedExtra()) {
                refundExtraBalance(walletAccount, wallet.requiredAmount());
            }
            getPaymentSuccessListener().accept(wallet.address());
        } else {
            try {
                if (walletAccount.getBalance().compareTo(NanoAmount.ZERO) > 0) {
                    refundAllBalance(walletAccount);
                }
            } catch (WalletActionException e) {
                NanoPay.LOGGER.error("Couldn't get balance of receiving wallet (" + wallet + ").", e);
            }
            getPaymentFailListener().accept(wallet.address());
        }
    }

    public void refundExtraBalance(LocalRpcWalletAccount<StateBlock> walletAccount, BigDecimal requiredAmount) {
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

    public void refundAllBalance(LocalRpcWalletAccount<StateBlock> walletAccount) {
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
                walletAccount.sendAll(storageWallet);
            } catch (WalletActionException ex) {
                NanoPay.LOGGER.error("Couldn't send unwanted NANO from receiving wallet ("
                        + walletAccount.getAccount().toAddress() + ")" + " to storage wallet ("
                        + this.storageWallet + ") after failing to refund.", e);
            }
        }
    }

}
