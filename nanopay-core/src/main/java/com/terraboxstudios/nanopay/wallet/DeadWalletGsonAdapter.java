package com.terraboxstudios.nanopay.wallet;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;

class DeadWalletGsonAdapter extends TypeAdapter<DeadWallet> {

    @Override
    public void write(JsonWriter jsonWriter, DeadWallet deadWallet) throws IOException {
        jsonWriter.beginObject();
        jsonWriter.name("address");
        jsonWriter.value(deadWallet.address());
        jsonWriter.name("private_key");
        jsonWriter.value(deadWallet.privateKey());
        jsonWriter.name("death_time");
        jsonWriter.value(deadWallet.deathTime().toEpochMilli());
        jsonWriter.name("required_amount");
        jsonWriter.value(deadWallet.requiredAmount());
        jsonWriter.name("success");
        jsonWriter.value(deadWallet.success());
        jsonWriter.endObject();
    }

    @Override
    public DeadWallet read(JsonReader jsonReader) throws IOException {
        jsonReader.beginObject();
        jsonReader.nextName();
        String address = jsonReader.nextString();
        jsonReader.nextName();
        String privateKey = jsonReader.nextString();
        jsonReader.nextName();
        Instant deadTime = Instant.ofEpochMilli(jsonReader.nextLong());
        jsonReader.nextName();
        BigDecimal requiredAmount = new BigDecimal(jsonReader.nextString());
        jsonReader.nextName();
        boolean success = jsonReader.nextBoolean();
        jsonReader.endObject();
        return new DeadWallet(address, privateKey, deadTime, requiredAmount, success);
    }

}
