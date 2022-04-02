package com.terraboxstudios.nanopay;

import com.terraboxstudios.nanopay.storage.MemoryWalletStorageProvider;
import org.apache.log4j.Level;
import uk.oczadly.karl.jnano.model.NanoAccount;
import uk.oczadly.karl.jnano.rpc.RpcQueryNode;

import java.math.BigDecimal;
import java.net.URI;
import java.util.concurrent.Executors;

public class Main {

    public static void main(String[] args) {
        WalletManager.LOGGER.setLevel(Level.INFO);
        WalletManager walletManager = new WalletManager(
                new MemoryWalletStorageProvider(),
                Executors.newSingleThreadScheduledExecutor(),
                NanoAccount.parseAddress("nano_1kbx1n9xbjg6twmhrax3g1eyn3o55aab6f6o6njhfzfxz66ka3zwkaubdu9g"),
                new RpcQueryNode("127.0.0.1", 7076),
                NanoAccount.parseAddress("nano_1natrium1o3z5519ifou7xii8crpxpk8y65qmkih8e8bpsjri651oza8imdd"),
                walletAddress -> System.out.println("Wallet " + walletAddress + " Finished")
        );
        WebsocketBlockListener websocketBlockListener = new WebsocketBlockListener(walletManager, URI.create("ws://127.0.0.1:7078"));
        websocketBlockListener.connectWebsocket();
        walletManager.setWebsocketBlockListener(websocketBlockListener);
        System.out.println("Send 0.001 NANO to " + walletManager.newPayment(new BigDecimal("0.001")));
    }

}
