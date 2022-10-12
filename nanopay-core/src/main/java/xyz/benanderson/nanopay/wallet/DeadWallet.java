package xyz.benanderson.nanopay.wallet;

import com.google.gson.annotations.JsonAdapter;
import uk.oczadly.karl.jnano.model.NanoAccount;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@JsonAdapter(value = DeadWalletGsonAdapter.class)
public record DeadWallet(String address, String privateKey, Instant deathTime, BigDecimal requiredAmount, boolean success)
        implements Serializable {

    public DeadWallet {
        if (!NanoAccount.isValidNano(address))
            throw new IllegalArgumentException("Invalid wallet address");
        if (requiredAmount.equals(BigDecimal.ZERO))
            throw new IllegalArgumentException("Required amount cannot be zero");
        deathTime = deathTime.truncatedTo(ChronoUnit.MILLIS);
    }

    public static DeadWallet kill(Wallet wallet, boolean success) {
        return new DeadWallet(wallet.address(), wallet.privateKey(), Instant.now(), wallet.requiredAmount(), success);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DeadWallet deadWallet = (DeadWallet) o;
        return address.equals(deadWallet.address)
                && privateKey.equals(deadWallet.privateKey)
                && deathTime.equals(deadWallet.deathTime)
                && (requiredAmount.compareTo(deadWallet.requiredAmount) == 0)
                && success == deadWallet.success;
    }

}
