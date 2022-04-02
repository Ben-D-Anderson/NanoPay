package com.terraboxstudios.nanopay;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import uk.oczadly.karl.jnano.model.NanoAccount;
import uk.oczadly.karl.jnano.model.NanoAmount;

@AllArgsConstructor
@Getter
@EqualsAndHashCode
@ToString
final class Transaction {

    private final NanoAccount sender, receiver;
    private final NanoAmount amount;

}
