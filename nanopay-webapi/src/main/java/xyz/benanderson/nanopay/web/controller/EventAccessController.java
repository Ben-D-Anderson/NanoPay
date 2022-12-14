package xyz.benanderson.nanopay.web.controller;

import xyz.benanderson.nanopay.NanoPay;
import xyz.benanderson.nanopay.death.RangeSearchable;
import xyz.benanderson.nanopay.wallet.DeadWallet;
import xyz.benanderson.nanopay.web.NanoPayAPI;
import io.javalin.http.Context;
import io.javalin.http.HttpCode;

import java.time.Instant;
import java.util.List;

public record EventAccessController(NanoPay nanoPay) {

    public void getEvents(Context ctx) {
        if (!(nanoPay().getWalletDeathLogger() instanceof RangeSearchable<?, ?>)) {
            ctx.status(HttpCode.NOT_IMPLEMENTED)
                    .json(new NanoPayAPI.JsonResponse(false, "WalletDeathLogger does not support searching"));
            return;
        }
        Instant after = ctx.queryParamAsClass("after", Instant.class).getOrDefault(Instant.EPOCH);
        Instant before = ctx.queryParamAsClass("before", Instant.class).getOrDefault(Instant.MAX);
        //noinspection unchecked
        RangeSearchable<? extends DeadWallet, ? super Instant> deathLogger
                = (RangeSearchable<? extends DeadWallet, ? super Instant>) nanoPay().getWalletDeathLogger();
        List<? extends DeadWallet> deadWallets = deathLogger.findByRange(after, before);
        ctx.status(HttpCode.OK).json(deadWallets.toArray());
    }

}
