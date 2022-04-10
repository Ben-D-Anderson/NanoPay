package com.terraboxstudios.nanopay.wallet;

import com.google.gson.annotations.JsonAdapter;
import uk.oczadly.karl.jnano.model.NanoAccount;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

@JsonAdapter(value = WalletGsonAdapter.class)
public record Wallet(String address, String privateKey, Instant creationTime, BigDecimal requiredAmount)
        implements Serializable {

    public Wallet {
        if (!NanoAccount.isValidNano(address))
            throw new IllegalArgumentException("Invalid wallet address");
        if (requiredAmount.equals(BigDecimal.ZERO))
            throw new IllegalArgumentException("Required amount cannot be zero");
    }

}
