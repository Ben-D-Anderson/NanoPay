package com.terraboxstudios.nanopay.wallet;

import uk.oczadly.karl.jnano.model.NanoAccount;
import uk.oczadly.karl.jnano.model.NanoAmount;

public record Transaction(NanoAccount sender, NanoAccount receiver, NanoAmount amount) {

}
