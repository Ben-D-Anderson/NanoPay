package com.terraboxstudios.nanopay.web;

import com.terraboxstudios.nanopay.NanoPay;
import com.terraboxstudios.nanopay.wallet.Wallet;
import io.javalin.http.Context;
import io.javalin.http.HttpCode;
import io.javalin.http.NotFoundResponse;
import uk.oczadly.karl.jnano.model.NanoAccount;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public record PaymentController(NanoPay nanoPay) {

    public void getWallet(Context ctx) {
        NanoAccount walletAccount = ctx.pathParamAsClass("wallet", NanoAccount.class).get();
        Wallet wallet = nanoPay.getWalletStorage().activeWalletStorage().findWalletByAddress(walletAccount.toAddress())
                .orElseThrow(() -> new NotFoundResponse("wallet not found in active storage"));
        BigDecimal walletBalance = nanoPay.getBalance(wallet).orElse(BigDecimal.ZERO);
        ctx.status(HttpCode.OK).json(new NanoPayAPI.ViewableWallet(wallet.address(), wallet.creationTime().toEpochMilli(),
                wallet.requiredAmount(), walletBalance));
    }

    public void getAllWallets(Context ctx) {
        Collection<Wallet> wallets = nanoPay.getWalletStorage().activeWalletStorage().getAllWallets();
        Map<String, Optional<BigDecimal>> balances = nanoPay.getBalances(wallets);
        ctx.status(HttpCode.OK).json(wallets.stream()
                .map(wallet -> new NanoPayAPI.ViewableWallet(wallet.address(), wallet.creationTime().toEpochMilli(),
                        wallet.requiredAmount(), balances.get(wallet.address()).orElse(BigDecimal.ZERO)))
                .collect(Collectors.toSet()));
    }

    public void createWallet(Context ctx) {
        BigDecimal amount = ctx.queryParamAsClass("amount", BigDecimal.class)
                .check(val -> val != null && !val.equals(BigDecimal.ZERO), "'amount' cannot be zero")
                .get();
        ctx.status(HttpCode.OK).json(new NanoPayAPI.JsonResponse(true, nanoPay.requestPayment(amount)));
    }


    public void deleteWallet(Context ctx) {
        NanoAccount walletAccount = ctx.pathParamAsClass("wallet", NanoAccount.class).get();
        if (nanoPay.cancelPayment(walletAccount.toAddress())) {
            ctx.status(HttpCode.OK).json(new NanoPayAPI.JsonResponse(true, "wallet killed"));
        } else {
            throw new NotFoundResponse("wallet not found in active storage");
        }
    }

}
