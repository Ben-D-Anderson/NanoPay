package com.terraboxstudios.nanopay.storage;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class WalletStorageProvider {

    private final WalletStorage activeWalletStorage, deadWalletStorage;

}
