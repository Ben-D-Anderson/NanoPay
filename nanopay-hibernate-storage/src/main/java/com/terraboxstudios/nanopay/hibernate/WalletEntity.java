package com.terraboxstudios.nanopay.hibernate;

import com.terraboxstudios.nanopay.storage.WalletType;
import com.terraboxstudios.nanopay.wallet.Wallet;
import lombok.*;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@Entity
@EqualsAndHashCode
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WalletEntity {

    @Getter
    @Setter
    @Embeddable
    @EqualsAndHashCode
    @AllArgsConstructor(access = AccessLevel.PROTECTED)
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    public static class WalletEntityId implements Serializable {
        @Column(name = "address", nullable = false, unique = true, updatable = false)
        private String address;
        @Column(name = "type", nullable = false, updatable = false)
        private WalletType walletType;
    }

    protected WalletEntity(Wallet wallet, WalletType walletType) {
        this(new WalletEntityId(wallet.address(), walletType), wallet.privateKey(), wallet.creationTime(), wallet.requiredAmount());
    }

    @EmbeddedId
    private WalletEntityId walletEntityId;

    @Column(name = "private_key", nullable = false, unique = true, updatable = false)
    private String privateKey;

    @Column(name = "creation_time", nullable = false, updatable = false)
    private Instant creationTime;

    @Column(name = "required_amount", nullable = false, updatable = false)
    private BigDecimal requiredAmount;

    public Wallet asWallet() {
        return new Wallet(walletEntityId.getAddress(), privateKey, creationTime, requiredAmount);
    }

}
