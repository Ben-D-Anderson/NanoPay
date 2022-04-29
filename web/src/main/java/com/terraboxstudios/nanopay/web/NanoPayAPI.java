package com.terraboxstudios.nanopay.web;

import com.google.gson.Gson;
import com.terraboxstudios.nanopay.NanoPay;
import io.javalin.Javalin;
import io.javalin.core.validation.JavalinValidation;
import io.javalin.http.HttpCode;
import io.javalin.websocket.WsContext;
import uk.oczadly.karl.jnano.model.NanoAccount;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static io.javalin.apibuilder.ApiBuilder.*;

public class NanoPayAPI {

    private final NanoPayConfiguration config;
    private final NanoPay nanoPay;
    private final Javalin javalin;
    private final Gson gson;
    private final Set<WsContext> websocketClients = Collections.synchronizedSet(new HashSet<>());

    public record JsonResponse(boolean success, String message) {
    }
    public record PaymentResult(String result, String walletAddress) {
        public static PaymentResult success(String walletAddress) {
            return new PaymentResult("success", walletAddress);
        }
        public static PaymentResult failure(String walletAddress) {
            return new PaymentResult("failure", walletAddress);
        }
    }
    public record ViewableWallet(String address, long creationTime, BigDecimal requiredAmount,
                                 BigDecimal receivedAmount) {
    }

    private NanoPayAPI() {
        this.config = new NanoPayConfiguration();
        this.nanoPay = createNanoPay(this.config);
        this.javalin = Javalin.create();
        this.gson = new Gson();
        JavalinValidation.register(NanoAccount.class, NanoAccount::parseAddress);
    }

    public static void main(String[] args) {
        NanoPayAPI nanoPayAPI = new NanoPayAPI();
        nanoPayAPI.applyAuthKeyRequirementHandler();
        nanoPayAPI.applyPaymentHandlers();
        nanoPayAPI.applyPollEventsWebsocketHandler();
        nanoPayAPI.start();
    }

    private NanoPay createNanoPay(NanoPayConfiguration config) {
        NanoPay.Builder builder = new NanoPay.Builder(config.getString("nanopay.storage_wallet"),
                this::onPaymentSuccess, this::onPaymentFailure);
        //todo add more customisation in config
        return builder.build();
    }

    private void onPaymentSuccess(String walletAddress) {
        broadcastWebsocketMessage(gson.toJson(PaymentResult.success(walletAddress)));
    }

    private void onPaymentFailure(String walletAddress) {
        broadcastWebsocketMessage(gson.toJson(PaymentResult.failure(walletAddress)));
    }

    private void broadcastWebsocketMessage(String message) {
        websocketClients.stream().filter(ctx -> ctx.session.isOpen()).forEach(session -> session.send(message));
    }

    private void start() {
        javalin.start(config.getString("api.address"), config.getInt("api.port"));
    }

    private void applyPaymentHandlers() {
        PaymentController paymentController = new PaymentController(nanoPay);

        javalin.routes(() -> path("payments", () -> {
            get(paymentController::getAllWallets);
            post(paymentController::createWallet);
            path("{wallet}", () -> {
                get(paymentController::getWallet);
                delete(paymentController::deleteWallet);
            });
        }));
    }

    private void applyPollEventsWebsocketHandler() {
        javalin.ws("/events", wsConfig -> {
            if (config.getBoolean("api.require_auth_key")) {
                wsConfig.onMessage(ctx -> {
                    if (ctx.message().equals(config.getString("api.auth_key"))) {
                        websocketClients.add(ctx);
                    }
                });
            } else {
                wsConfig.onConnect(websocketClients::add);
                wsConfig.onConnect(wsCtx -> websocketClients.removeIf(ctx -> !ctx.session.isOpen()));
            }
        });
    }

    private void applyAuthKeyRequirementHandler() {
        if (config.getBoolean("api.require_auth_key")) {
            javalin.before(ctx -> {
                String authKey = ctx.queryParam("auth_key");
                if (authKey == null || authKey.isEmpty()) {
                    ctx.status(HttpCode.FORBIDDEN).json(new JsonResponse(false, "parameter 'auth_key' must be set"));
                } else if (!authKey.equals(config.getString("api.auth_key"))) {
                    ctx.status(HttpCode.FORBIDDEN).json(new JsonResponse(false, "provided 'auth_key' is invalid"));
                }
            });
        }
    }

}
