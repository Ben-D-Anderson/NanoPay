package com.terraboxstudios.nanopay.wallet;

import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Value
public class Wallet {

    String address, privateKey;
    Instant creationTime;
    BigDecimal requiredAmount;

}
