package com.terraboxstudios.nanopay;

import uk.oczadly.karl.jnano.model.NanoAccount;
import uk.oczadly.karl.jnano.model.block.BlockType;
import uk.oczadly.karl.jnano.websocket.NanoWebSocketClient;
import uk.oczadly.karl.jnano.websocket.WsObserver;
import uk.oczadly.karl.jnano.websocket.topic.TopicConfirmation;

import java.net.URI;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class WebsocketListener {

    private final NanoWebSocketClient webSocketClient;

    public WebsocketListener(URI websocketURI) {
        this.webSocketClient = new NanoWebSocketClient(websocketURI);
        this.webSocketClient.setObserver(new WsObserver() {
            @Override
            public void onOpen(int i) {
                WalletManager.LOGGER.info("Websocket opened, " + i);
            }

            @Override
            public void onClose(int i, String s, boolean b) {
                WalletManager.LOGGER.info("Websocket closed, " + i + ", " + s + ", " + b);
            }

            @Override
            public void onSocketError(Exception e) {
                WalletManager.LOGGER.error("Websocket error", e);
            }
        });
    }

    public void connectWebsocket(Consumer<Transaction> websocketCallback, Predicate<String> activeWalletChecker) {
        try {
            if (!webSocketClient.connect()) {
                WalletManager.LOGGER.error("Could not connect to WebSocket");
                return;
            }
        } catch (InterruptedException e) {
            WalletManager.LOGGER.error("Exception occurred connecting to websocket", e);
            return;
        }
        webSocketClient.getTopics().topicConfirmedBlocks().registerListener((message, context) -> {
            if (message.getBlock().getType() != BlockType.STATE && message.getBlock().getType() != BlockType.SEND) return;
            String toAddress = message.getBlock().toJsonObject().get("link_as_account").getAsString();
            if (!activeWalletChecker.test(toAddress)) return;
            websocketCallback.accept(new Transaction(
                    message.getAccount(),
                    NanoAccount.parse(toAddress),
                    message.getAmount()
            ));
        });
    }

    public void addWalletFilter(String address) {
        webSocketClient.getTopics().topicConfirmedBlocks().subscribe(new TopicConfirmation.SubArgs().filterAccounts(address).includeBlockContents());
    }

}
