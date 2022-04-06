package com.terraboxstudios.nanopay;

import lombok.*;
import uk.oczadly.karl.jnano.model.NanoAccount;
import uk.oczadly.karl.jnano.model.NanoAmount;

@Value
class Transaction {

    NanoAccount sender, receiver;
    NanoAmount amount;

}
