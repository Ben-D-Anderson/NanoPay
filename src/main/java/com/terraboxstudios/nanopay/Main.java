package com.terraboxstudios.nanopay;

import com.terraboxstudios.nanopay.storage.MemoryWalletStorageProvider;
import uk.oczadly.karl.jnano.model.NanoAccount;
import uk.oczadly.karl.jnano.rpc.RpcServiceProviders;
import uk.oczadly.karl.jnano.util.wallet.WalletActionException;

import java.math.BigDecimal;
import java.net.URI;
import java.util.concurrent.Executors;

public class Main {

    public static void main(String[] args) {
        WebSocketListener webSocketListener = new WebSocketListener(URI.create("wss://socket.nanos.cc/"), true);
        WalletManager walletManager = new WalletManager(
                new MemoryWalletStorageProvider(),
                webSocketListener,
                Executors.newSingleThreadScheduledExecutor(),
                NanoAccount.parseAddress("nano_1kbx1n9xbjg6twmhrax3g1eyn3o55aab6f6o6njhfzfxz66ka3zwkaubdu9g"),
                RpcServiceProviders.myNanoNinja(),
                NanoAccount.parseAddress("nano_1natrium1o3z5519ifou7xii8crpxpk8y65qmkih8e8bpsjri651oza8imdd"),
                walletAddress -> System.out.println("Wallet " + walletAddress + " Finished")
        );

        webSocketListener.connectWebSocket(transaction -> {
            NanoPay.LOGGER.debug("Wallet " + transaction.getReceiver() + " received " + transaction.getAmount().toNanoString() + " NANO");
            try {
                walletManager.checkWallet(transaction.getReceiver().toAddress());
            } catch (WalletActionException e) {
                NanoPay.LOGGER.error("Exception occurred checking wallet (" + transaction.getReceiver().toAddress() + ")", e);
            }
        }, walletManager::isActiveWallet);

        System.out.println("Send 0.00001 NANO to " + walletManager.newPayment(new BigDecimal("0.00001")));
    }

}
