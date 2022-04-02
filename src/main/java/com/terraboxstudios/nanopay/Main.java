package com.terraboxstudios.nanopay;

import com.terraboxstudios.nanopay.storage.MemoryWalletStorageProvider;
import org.apache.log4j.Level;
import uk.oczadly.karl.jnano.model.NanoAccount;
import uk.oczadly.karl.jnano.rpc.RpcQueryNode;
import uk.oczadly.karl.jnano.util.wallet.WalletActionException;

import java.math.BigDecimal;
import java.net.URI;
import java.util.concurrent.Executors;

public class Main {

    public static void main(String[] args) {
        WalletManager.LOGGER.setLevel(Level.INFO);
        WebsocketListener websocketListener = new WebsocketListener(URI.create("wss://socket.nanos.cc/"));
        WalletManager walletManager = new WalletManager(
                new MemoryWalletStorageProvider(),
                websocketListener,
                Executors.newSingleThreadScheduledExecutor(),
                NanoAccount.parseAddress("nano_1kbx1n9xbjg6twmhrax3g1eyn3o55aab6f6o6njhfzfxz66ka3zwkaubdu9g"),
                new RpcQueryNode("proxy.nanos.cc/proxy", 443),
                NanoAccount.parseAddress("nano_1natrium1o3z5519ifou7xii8crpxpk8y65qmkih8e8bpsjri651oza8imdd"),
                walletAddress -> System.out.println("Wallet " + walletAddress + " Finished")
        );

        websocketListener.connectWebsocket(transaction -> {
            WalletManager.LOGGER.info("Wallet " + transaction.getReceiver() + " received " + transaction.getAmount().toString());
            try {
                walletManager.checkWallet(transaction.getReceiver().toAddress());
            } catch (WalletActionException e) {
                WalletManager.LOGGER.error("Exception occurred checking wallet (" + transaction.getReceiver().toAddress() + ")", e);
            }
        }, walletManager::isActiveWallet);

        System.out.println("Send 0.00001 NANO to " + walletManager.newPayment(new BigDecimal("0.00001")));
    }

}
