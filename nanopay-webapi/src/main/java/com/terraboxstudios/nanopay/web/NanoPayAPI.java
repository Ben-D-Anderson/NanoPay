package com.terraboxstudios.nanopay.web;

import com.google.gson.Gson;
import com.terraboxstudios.nanopay.NanoPay;
import com.terraboxstudios.nanopay.web.controller.EventAccessController;
import com.terraboxstudios.nanopay.web.controller.PaymentController;
import io.javalin.Javalin;
import io.javalin.core.validation.JavalinValidation;
import io.javalin.http.HttpCode;
import io.javalin.websocket.WsContext;
import uk.oczadly.karl.jnano.model.NanoAccount;

import java.math.BigDecimal;
import java.nio.file.Paths;
import java.time.Instant;
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
        this.config = new NanoPayConfiguration(Paths.get(System.getProperty("user.dir")));
        this.nanoPay = this.config.createNanoPay(this::onPaymentSuccess, this::onPaymentFailure);
        this.javalin = Javalin.create();
        this.gson = new Gson();
        JavalinValidation.register(NanoAccount.class, NanoAccount::parseAddress);
        JavalinValidation.register(Instant.class, s -> Instant.ofEpochMilli(Long.parseLong(s)));
    }

    public static void main(String[] args) {
        NanoPayAPI nanoPayAPI = new NanoPayAPI();
        nanoPayAPI.applyAuthKeyRequirementHandler();
        nanoPayAPI.applyHandlers();
        nanoPayAPI.applyEventsWebsocketHandler();
        nanoPayAPI.start();
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

    private void applyHandlers() {
        PaymentController paymentController = new PaymentController(nanoPay);
        EventAccessController eventAccessController = new EventAccessController(nanoPay);

        javalin.routes(() -> {
            path("payments", () -> {
                get(paymentController::getAllWallets);
                post(paymentController::createWallet);
                path("{wallet}", () -> {
                    get(paymentController::getWallet);
                    delete(paymentController::deleteWallet);
                });
            });
            path("events", () -> {
                get(eventAccessController::getEvents);
            });
        });
    }

    private void applyEventsWebsocketHandler() {
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
