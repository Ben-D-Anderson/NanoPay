package com.terraboxstudios.nanopay.web;

import com.google.gson.Gson;
import io.javalin.plugin.json.JsonMapper;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

@AllArgsConstructor
public class GsonJsonMapper implements JsonMapper {

    private final Gson gson;

    @NotNull
    @Override
    public String toJsonString(@NotNull Object obj) {
        return gson.toJson(obj);
    }

    @SneakyThrows
    @NotNull
    @Override
    public InputStream toJsonStream(@NotNull Object obj) {
        return new ByteArrayInputStream(gson.toJson(obj).getBytes(StandardCharsets.UTF_8));
    }

    @NotNull
    @Override
    public <T> T fromJsonString(@NotNull String json, @NotNull Class<T> targetClass) {
        return gson.fromJson(json, targetClass);
    }

    @NotNull
    @Override
    public <T> T fromJsonStream(@NotNull InputStream json, @NotNull Class<T> targetClass) {
        return gson.fromJson(new InputStreamReader(json), targetClass);
    }

}
