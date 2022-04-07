package com.terraboxstudios.nanopay;

import uk.oczadly.karl.jnano.model.NanoAccount;
import uk.oczadly.karl.jnano.model.NanoAmount;

record Transaction(NanoAccount sender, NanoAccount receiver, NanoAmount amount) {

}
