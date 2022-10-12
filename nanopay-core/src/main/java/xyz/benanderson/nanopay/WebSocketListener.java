package xyz.benanderson.nanopay;

import xyz.benanderson.nanopay.wallet.Transaction;
import uk.oczadly.karl.jnano.model.NanoAccount;
import uk.oczadly.karl.jnano.websocket.NanoWebSocketClient;
import uk.oczadly.karl.jnano.websocket.WsObserver;
import uk.oczadly.karl.jnano.websocket.topic.TopicConfirmation;

import java.net.URI;
import java.util.function.Consumer;

final class WebSocketListener {

    private final NanoWebSocketClient webSocketClient;
    private Consumer<Transaction> webSocketCallback;

    WebSocketListener(URI webSocketURI) {
        this (webSocketURI, true);
    }

    WebSocketListener(NanoWebSocketClient nanoWebSocketClient) {
        this (nanoWebSocketClient, true);
    }

    WebSocketListener(URI webSocketURI, boolean reconnect) {
        this(new NanoWebSocketClient(webSocketURI), reconnect);
    }

    WebSocketListener(NanoWebSocketClient nanoWebSocketClient, boolean reconnect) {
        this.webSocketClient = nanoWebSocketClient;
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
                    connectWebSocket(webSocketCallback);
                }
            }

            @Override
            public void onSocketError(Exception e) {
                NanoPay.LOGGER.error("WebSocket error.", e);
                if (reconnect) {
                    NanoPay.LOGGER.info("Attempting to reconnect to WebSocket");
                    connectWebSocket(webSocketCallback);
                }
            }
        });
    }

    void connectWebSocket(Consumer<Transaction> webSocketCallback) {
        this.webSocketCallback = webSocketCallback;
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
            if (!message.getBlock().toJsonObject().get("subtype").getAsString().equalsIgnoreCase("send")) return;
            String toAddress = message.getBlock().toJsonObject().get("link_as_account").getAsString();
            webSocketCallback.accept(new Transaction(
                    message.getAccount(),
                    NanoAccount.parse(toAddress),
                    message.getAmount()
            ));
        });
//        webSocketClient.getTopics().topicConfirmedBlocks().subscribe();
    }

    void addWalletFilter(String address) {
        webSocketClient.getTopics().topicConfirmedBlocks()
                .subscribe(new TopicConfirmation.SubArgs().filterAccounts(address).includeBlockContents());
    }

//    void addWalletFilter(String address) {
//        webSocketClient.getTopics().topicConfirmedBlocks()
//                .update(new TopicConfirmation.UpdateArgs().addAccountsFilter(address));
//    }
//
//    void removeWalletFilter(String address) {
//        webSocketClient.getTopics().topicConfirmedBlocks()
//                .update(new TopicConfirmation.UpdateArgs().removeAccountsFilter(address));
//    }

}
