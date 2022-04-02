package com.terraboxstudios.nanopay;

import uk.oczadly.karl.jnano.model.NanoAccount;
import uk.oczadly.karl.jnano.model.block.BlockType;
import uk.oczadly.karl.jnano.websocket.NanoWebSocketClient;
import uk.oczadly.karl.jnano.websocket.WsObserver;
import uk.oczadly.karl.jnano.websocket.topic.TopicConfirmation;

import java.net.URI;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class WebSocketListener {

    private final NanoWebSocketClient webSocketClient;
    private Consumer<Transaction> webSocketCallback;
    private Predicate<String> activeWalletChecker;

    WebSocketListener(URI webSocketURI, boolean reconnect) {
        this.webSocketClient = new NanoWebSocketClient(webSocketURI);
        this.webSocketClient.setObserver(new WsObserver() {
            @Override
            public void onOpen(int i) {
                NanoPay.LOGGER.debug("WebSocket opened. Code: " + i);
            }

            @Override
            public void onClose(int i, String s, boolean b) {
                NanoPay.LOGGER.info("WebSocket closed. Code: " + i + ". Reason: " + s);
                if (reconnect) {
                    NanoPay.LOGGER.info("Attempting to reconnect to WebSocket");
                    connectWebSocket(webSocketCallback, activeWalletChecker);
                }
            }

            @Override
            public void onSocketError(Exception e) {
                NanoPay.LOGGER.error("WebSocket error.", e);
                if (reconnect) {
                    NanoPay.LOGGER.info("Attempting to reconnect to WebSocket");
                    connectWebSocket(webSocketCallback, activeWalletChecker);
                }
            }
        });
    }

    void connectWebSocket(Consumer<Transaction> webSocketCallback, Predicate<String> activeWalletChecker) {
        this.webSocketCallback = webSocketCallback;
        this.activeWalletChecker = activeWalletChecker;
        try {
            if (!webSocketClient.connect()) {
                NanoPay.LOGGER.error("Could not connect to WebSocket");
                return;
            }
        } catch (InterruptedException e) {
            NanoPay.LOGGER.error("Exception occurred connecting to WebSocket", e);
            return;
        }
        webSocketClient.getTopics().topicConfirmedBlocks().registerListener((message, context) -> {
            if (message.getBlock().getType() != BlockType.STATE && message.getBlock().getType() != BlockType.SEND) return;
            String toAddress = message.getBlock().toJsonObject().get("link_as_account").getAsString();
            if (!activeWalletChecker.test(toAddress)) return;
            webSocketCallback.accept(new Transaction(
                    message.getAccount(),
                    NanoAccount.parse(toAddress),
                    message.getAmount()
            ));
        });
    }

    void addWalletFilter(String address) {
        webSocketClient.getTopics().topicConfirmedBlocks().subscribe(new TopicConfirmation.SubArgs().filterAccounts(address).includeBlockContents());
    }

}
