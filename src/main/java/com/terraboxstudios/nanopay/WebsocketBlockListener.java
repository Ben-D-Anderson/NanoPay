package com.terraboxstudios.nanopay;

import uk.oczadly.karl.jnano.util.wallet.WalletActionException;
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

    public void connectWebsocket() {
        webSocketClient = new NanoWebSocketClient(websocketURI);
        try {
            if (!webSocketClient.connect()) {
                WalletManager.LOGGER.error("Could not connect to WebSocket (" + websocketURI.toString() + ")");
                return;
            }
        } catch (InterruptedException e) {
            WalletManager.LOGGER.error("Exception occurred connecting to websocket", e);
            return;
        }
        webSocketClient.getTopics().topicConfirmedBlocks().registerListener((message, context) -> {
            WalletManager.LOGGER.info("Checking wallet (" + message.getAccount() + ")");
            if (this.walletManager.isActiveWallet(message.getAccount().toAddress())) {
                try {
                    this.walletManager.checkWallet(message.getAccount().toAddress());
                } catch (WalletActionException e) {
                    WalletManager.LOGGER.error("Exception occurred checking wallet (" + message.getAccount().toAddress() + ")", e);
                }
            }
        });
        webSocketClient.getTopics().topicConfirmedBlocks().subscribe(new TopicConfirmation.SubArgs());
    }

    //todo fix websocket stuff - I am not getting callbacks for block confirmations
    public void addWalletFilter(String address) {
        webSocketClient.getTopics().topicConfirmedBlocks().update(new TopicConfirmation.UpdateArgs().addAccountsFilter(address));
    }

    public void removeWalletFilter(String address) {
        webSocketClient.getTopics().topicConfirmedBlocks().update(new TopicConfirmation.UpdateArgs().removeAccountsFilter(address));
    }

}
