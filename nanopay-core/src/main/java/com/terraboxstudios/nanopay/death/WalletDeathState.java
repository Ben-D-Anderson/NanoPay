package com.terraboxstudios.nanopay.death;

public record WalletDeathState(boolean success, boolean receivedExtra) {

    public static WalletDeathState failure() {
        return new WalletDeathState(false, false);
    }

    public static WalletDeathState success(boolean receivedExtra) {
        return new WalletDeathState(true, receivedExtra);
    }

}
