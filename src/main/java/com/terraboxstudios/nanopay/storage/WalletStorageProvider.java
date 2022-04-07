package com.terraboxstudios.nanopay.storage;

public record WalletStorageProvider(WalletStorage activeWalletStorage, WalletStorage deadWalletStorage) {

}
