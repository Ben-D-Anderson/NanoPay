package com.terraboxstudios.nanopay.hibernate.entity;

import com.terraboxstudios.nanopay.storage.WalletType;
import com.terraboxstudios.nanopay.wallet.Wallet;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.Hibernate;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

@Getter
@Setter
@Entity
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WalletEntity {

    @Getter
    @Setter
    @Embeddable
    @EqualsAndHashCode
    @AllArgsConstructor
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    public static class WalletEntityId implements Serializable {
        @Column(name = "address", nullable = false, unique = true, updatable = false)
        private String address;

        @Enumerated(EnumType.STRING)
        @Column(name = "type", nullable = false, updatable = false)
        private WalletType walletType;
    }

    public WalletEntity(Wallet wallet, WalletType walletType) {
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        WalletEntity that = (WalletEntity) o;
        return walletEntityId != null && Objects.equals(walletEntityId, that.walletEntityId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(walletEntityId);
    }
}
