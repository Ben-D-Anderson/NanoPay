package com.terraboxstudios.nanopay.wallet;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;

public class WalletGsonAdapter extends TypeAdapter<Wallet> {

    @Override
    public void write(JsonWriter jsonWriter, Wallet wallet) throws IOException {
        jsonWriter.beginObject();
        jsonWriter.name("address");
        jsonWriter.value(wallet.getAddress());
        jsonWriter.name("private_key");
        jsonWriter.value(wallet.getPrivateKey());
        jsonWriter.name("creation_time");
        jsonWriter.value(wallet.getCreationTime().toEpochMilli());
        jsonWriter.name("required_amount");
        jsonWriter.value(wallet.getRequiredAmount());
        jsonWriter.endObject();
    }

    @Override
    public Wallet read(JsonReader jsonReader) throws IOException {
        jsonReader.beginObject();
        jsonReader.nextName();
        String address = jsonReader.nextString();
        jsonReader.nextName();
        String privateKey = jsonReader.nextString();
        jsonReader.nextName();
        Instant creationTime = Instant.ofEpochMilli(jsonReader.nextLong());
        jsonReader.nextName();
        BigDecimal requiredAmount = new BigDecimal(jsonReader.nextString());
        jsonReader.endObject();
        return new Wallet(address, privateKey, creationTime, requiredAmount);
    }

}
