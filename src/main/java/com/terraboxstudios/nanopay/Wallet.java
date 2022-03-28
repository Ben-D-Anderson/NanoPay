package com.terraboxstudios.nanopay;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.Instant;

@AllArgsConstructor
@Getter
@EqualsAndHashCode
@ToString
public final class Wallet {

    private final String address, privateKey;
    private final Instant creationTime;
    private final BigDecimal requiredNano;

}
