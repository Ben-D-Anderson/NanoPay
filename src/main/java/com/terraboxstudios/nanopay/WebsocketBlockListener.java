package com.terraboxstudios.nanopay;

import lombok.SneakyThrows;
import uk.oczadly.karl.jnano.model.NanoAccount;
import uk.oczadly.karl.jnano.rpc.util.wallet.WalletActionException;
import uk.oczadly.karl.jnano.util.WalletUtil;
import uk.oczadly.karl.jnano.websocket.NanoWebSocketClient;
import uk.oczadly.karl.jnano.websocket.topic.TopicConfirmation;

import java.net.URI;

public final class WebsocketBlockListener {

    private final WalletManager walletManager;
    private final URI websocketURI;
    private NanoWebSocketClient webSocketClient;

    public WebsocketBlockListener(WalletManager walletManager, URI websocketURI) {
        this.walletManager = walletManager;
        this.websocketURI = websocketURI;
    }

    @SneakyThrows
    public void connectWebsocket() {
        webSocketClient = new NanoWebSocketClient(websocketURI);
        if (!webSocketClient.connect()) {
            WalletManager.LOGGER.error("Could not connect to WebSocket (" + websocketURI.toString() + ")");
            return;
        }
        webSocketClient.getTopics().topicConfirmedBlocks().registerListener((message, context) -> {
            if (this.walletManager.isActiveWallet(message.getAccount().toAddress())) {
                try {
                    this.walletManager.checkWallet(message.getAccount().toAddress());
                } catch (WalletActionException e) {
                    WalletManager.LOGGER.error("Exception occurred checking wallet (" + message.getAccount().toAddress() + ")", e);
                }
            }
        });
        webSocketClient.getTopics().topicConfirmedBlocks().subscribeBlocking(new TopicConfirmation.SubArgs().filterAccounts(NanoAccount.fromPrivateKey(WalletUtil.generateRandomKey()).toAddress()));
    }

    public void addWalletFilter(String address) throws InterruptedException {
        webSocketClient.getTopics().topicConfirmedBlocks().updateBlocking(new TopicConfirmation.UpdateArgs().addAccountsFilter(address));
    }

    public void removeWalletFilter(String address) throws InterruptedException {
        webSocketClient.getTopics().topicConfirmedBlocks().updateBlocking(new TopicConfirmation.UpdateArgs().removeAccountsFilter(address));
    }

}
