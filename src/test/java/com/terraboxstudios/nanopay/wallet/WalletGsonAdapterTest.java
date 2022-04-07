package com.terraboxstudios.nanopay.wallet;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WalletGsonAdapterTest {

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    @Test
    void write() {
        Wallet wallet = new Wallet(
                "nano_18xbfx1czna9178ah7gkyg6ukrdg919ebn9xt7j6fkq31kh4qwia4r3i7674",
                "B18852DAB11E34B4C0BEE3C53FCABF75560791E13EC7A5D5F9B7670277DD4643",
                Instant.ofEpochMilli(1649247684032L),
                new BigDecimal("0.1")
        );

        String expectedJson = """
                {
                  "address": "nano_18xbfx1czna9178ah7gkyg6ukrdg919ebn9xt7j6fkq31kh4qwia4r3i7674",
                  "private_key": "B18852DAB11E34B4C0BEE3C53FCABF75560791E13EC7A5D5F9B7670277DD4643",
                  "creation_time": 1649247684032,
                  "required_amount": 0.1
                }""";
        String actualJson = gson.toJson(wallet);

        assertEquals(expectedJson, actualJson);
    }

    @Test
    void read() {
        String json = """
                {
                  "address":"nano_18xbfx1czna9178ah7gkyg6ukrdg919ebn9xt7j6fkq31kh4qwia4r3i7674",
                  "private_key":"B18852DAB11E34B4C0BEE3C53FCABF75560791E13EC7A5D5F9B7670277DD4643",
                  "creation_time":1649247684032,
                  "required_amount":0.1
                }
                """;
        StringReader stringReader = new StringReader(json);

        Wallet expectedWallet = new Wallet(
                "nano_18xbfx1czna9178ah7gkyg6ukrdg919ebn9xt7j6fkq31kh4qwia4r3i7674",
                "B18852DAB11E34B4C0BEE3C53FCABF75560791E13EC7A5D5F9B7670277DD4643",
                Instant.ofEpochMilli(1649247684032L),
                new BigDecimal("0.1")
        );
        Wallet readWallet = gson.fromJson(stringReader, Wallet.class);

        assertEquals(expectedWallet, readWallet);
    }

}