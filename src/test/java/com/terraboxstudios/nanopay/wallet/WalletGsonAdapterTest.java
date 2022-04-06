package com.terraboxstudios.nanopay.wallet;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.terraboxstudios.nanopay.SecureRandomUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import uk.oczadly.karl.jnano.model.HexData;
import uk.oczadly.karl.jnano.model.NanoAccount;
import uk.oczadly.karl.jnano.util.WalletUtil;

import java.io.StringReader;
import java.math.BigDecimal;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WalletGsonAdapterTest {

    private static Gson gson;

    @BeforeAll
    static void setupGson() {
        GsonBuilder builder = new GsonBuilder();
        builder.registerTypeAdapter(Wallet.class, new WalletGsonAdapter());
        gson = builder.create();
    }

    @Test
    void write() throws NoSuchAlgorithmException {
        HexData privateKey = WalletUtil.generateRandomKey(SecureRandomUtil.getSecureRandom());
        String walletAddress = NanoAccount.fromPrivateKey(privateKey).toAddress();
        Instant creationTime = Instant.now();
        BigDecimal amount = new BigDecimal("0.1");
        Wallet wallet = new Wallet(walletAddress,
                privateKey.toString(),
                creationTime,
                amount);

        String expectedJson = "{\"address\":\"" + walletAddress + "\",\"private_key\":\"" + privateKey
                + "\",\"creation_time\":" + creationTime.toEpochMilli() + ",\"required_amount\":" + amount + "}";
        String actualJson = gson.toJson(wallet);

        assertEquals(expectedJson, actualJson);
    }

    @Test
    void read() {
        String json = "{\"address\":\"nano_18xbfx1czna9178ah7gkyg6ukrdg919ebn9xt7j6fkq31kh4qwia4r3i7674\"," +
                "\"private_key\":\"B18852DAB11E34B4C0BEE3C53FCABF75560791E13EC7A5D5F9B7670277DD4643\"," +
                "\"creation_time\":1649247684032,\"required_amount\":0.1}";
        StringReader stringReader = new StringReader(json);

        Wallet expectedWallet = new Wallet("nano_18xbfx1czna9178ah7gkyg6ukrdg919ebn9xt7j6fkq31kh4qwia4r3i7674",
                "B18852DAB11E34B4C0BEE3C53FCABF75560791E13EC7A5D5F9B7670277DD4643",
                Instant.ofEpochMilli(1649247684032L),
                new BigDecimal("0.1"));
        Wallet readWallet = gson.fromJson(stringReader, Wallet.class);

        assertEquals(expectedWallet, readWallet);
    }

}