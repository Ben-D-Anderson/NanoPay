package com.terraboxstudios.nanopay.hibernate.entity;

import com.terraboxstudios.nanopay.wallet.DeadWallet;
import lombok.*;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@Entity
@EqualsAndHashCode
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

}
