package com.terraboxstudios.nanopay.wallet;

import uk.oczadly.karl.jnano.model.NanoAccount;

import java.math.BigDecimal;
import java.time.Instant;

public record Wallet(String address, String privateKey, Instant creationTime, BigDecimal requiredAmount) {

    public Wallet {
        if (!NanoAccount.isValidNano(address))
            throw new IllegalArgumentException("Invalid wallet address");
        if (requiredAmount.equals(BigDecimal.ZERO))
            throw new IllegalArgumentException("Required amount cannot be zero");
    }

}
