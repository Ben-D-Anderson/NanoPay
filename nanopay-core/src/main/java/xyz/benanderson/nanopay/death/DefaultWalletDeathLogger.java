package xyz.benanderson.nanopay.death;

import xyz.benanderson.nanopay.NanoPay;
import xyz.benanderson.nanopay.wallet.DeadWallet;

public class DefaultWalletDeathLogger implements WalletDeathLogger {

    @Override
    public void log(DeadWallet deadWallet) {
        NanoPay.LOGGER.debug("Payment " + deadWallet.address() + " " + (deadWallet.success() ? "succeeded" : "failed"));
    }

}
