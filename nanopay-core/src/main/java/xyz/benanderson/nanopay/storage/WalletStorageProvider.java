package xyz.benanderson.nanopay.storage;

public record WalletStorageProvider(WalletStorage activeWalletStorage, WalletStorage deadWalletStorage) {

}
