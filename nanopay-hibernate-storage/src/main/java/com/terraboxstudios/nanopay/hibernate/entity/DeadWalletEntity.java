package com.terraboxstudios.nanopay.hibernate.entity;

import com.terraboxstudios.nanopay.wallet.DeadWallet;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.*;
import org.hibernate.Hibernate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

@Getter
@Setter
@Entity
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeadWalletEntity {

    public DeadWalletEntity(DeadWallet deadWallet) {
        this(deadWallet.address(), deadWallet.privateKey(), deadWallet.deathTime(),
                deadWallet.requiredAmount(), deadWallet.success());
    }

    @Id
    @Column(name = "address", nullable = false, unique = true, updatable = false)
    private String address;

    @Column(name = "private_key", nullable = false, unique = true, updatable = false)
    private String privateKey;

    @Column(name = "death_time", nullable = false, updatable = false)
    private Instant deathTime;

    @Column(name = "required_amount", nullable = false, updatable = false)
    private BigDecimal requiredAmount;

    @Column(name = "success", nullable = false, updatable = false)
    private boolean success;

    public DeadWallet asDeadWallet() {
        return new DeadWallet(address, privateKey, deathTime, requiredAmount, success);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        DeadWalletEntity that = (DeadWalletEntity) o;
        return address != null && Objects.equals(address, that.address);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
