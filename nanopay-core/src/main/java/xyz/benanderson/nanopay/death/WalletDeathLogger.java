package xyz.benanderson.nanopay.death;

import xyz.benanderson.nanopay.wallet.DeadWallet;

public interface WalletDeathLogger {

    void log(DeadWallet deadWallet);

}
