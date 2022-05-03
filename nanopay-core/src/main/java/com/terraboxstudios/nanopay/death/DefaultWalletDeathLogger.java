package com.terraboxstudios.nanopay.death;

import com.terraboxstudios.nanopay.NanoPay;
import com.terraboxstudios.nanopay.wallet.DeadWallet;

public class DefaultWalletDeathLogger implements WalletDeathLogger {

    @Override
    public void log(DeadWallet deadWallet) {
        NanoPay.LOGGER.debug("Payment " + deadWallet.address() + " " + (deadWallet.success() ? "succeeded" : "failed"));
    }

}
