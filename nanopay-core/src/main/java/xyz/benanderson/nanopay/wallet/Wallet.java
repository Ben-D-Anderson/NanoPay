package xyz.benanderson.nanopay.wallet;

import com.google.gson.annotations.JsonAdapter;
import uk.oczadly.karl.jnano.model.NanoAccount;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@JsonAdapter(value = WalletGsonAdapter.class)
public record Wallet(String address, String privateKey, Instant creationTime, BigDecimal requiredAmount)
        implements Serializable {

    public Wallet {
        if (!NanoAccount.isValidNano(address))
            throw new IllegalArgumentException("Invalid wallet address");
        if (requiredAmount.equals(BigDecimal.ZERO))
            throw new IllegalArgumentException("Required amount cannot be zero");
        creationTime = creationTime.truncatedTo(ChronoUnit.MILLIS);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Wallet wallet = (Wallet) o;
        return address.equals(wallet.address)
                && privateKey.equals(wallet.privateKey)
                && creationTime.equals(wallet.creationTime)
                && (requiredAmount.compareTo(wallet.requiredAmount) == 0);
    }

}
