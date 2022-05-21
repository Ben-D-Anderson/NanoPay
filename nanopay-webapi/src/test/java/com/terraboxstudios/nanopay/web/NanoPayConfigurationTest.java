package com.terraboxstudios.nanopay.web;

import com.terraboxstudios.nanopay.death.WalletDeathLogger;
import com.terraboxstudios.nanopay.hibernate.HibernateWalletDeathLogger;
import com.terraboxstudios.nanopay.hibernate.HibernateWalletStorage;
import com.terraboxstudios.nanopay.storage.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NanoPayConfigurationTest {

    @Test
    void testConfigCreateAndLoad(@TempDir Path configFolder) throws IOException {
        NanoPayConfiguration nanoPayConfiguration = new NanoPayConfiguration(configFolder);
        assertTrue(Files.exists(nanoPayConfiguration.getConfigFile()));

        Properties loadedProperties = nanoPayConfiguration.getProperties();
        Properties expectedProperties = new Properties();
        expectedProperties.load(getClass().getClassLoader()
                .getResourceAsStream(nanoPayConfiguration.getConfigFileName()));
        assertEquals(expectedProperties, loadedProperties);
    }

    @Test
    void testParseDatabaseWalletLogger() {
        NanoPayConfiguration nanoPayConfiguration = mock(NanoPayConfiguration.class);
        doCallRealMethod().when(nanoPayConfiguration).getRequiredString(anyString());
        doCallRealMethod().when(nanoPayConfiguration).getRequiredInt(anyString());
        doCallRealMethod().when(nanoPayConfiguration).getInt(anyString());
        doCallRealMethod().when(nanoPayConfiguration).getBoolean(anyString(), anyBoolean());
        doCallRealMethod().when(nanoPayConfiguration).parseWalletDeathLogger();
        doCallRealMethod().when(nanoPayConfiguration).parseDatabase(anyString());
        doReturn(Optional.of("database")).when(nanoPayConfiguration).getString("nanopay.deathlog.type");
        doReturn(Optional.of("jdbc:h2:mem:testdb")).when(nanoPayConfiguration).getString("nanopay.deathlog.url");
        doReturn(Optional.of("org.h2.Driver")).when(nanoPayConfiguration).getString("nanopay.deathlog.driver");
        doReturn(Optional.of("create-drop")).when(nanoPayConfiguration).getString("nanopay.deathlog.hbm2ddl");

        Optional<WalletDeathLogger> walletDeathLoggerOptional = nanoPayConfiguration.parseWalletDeathLogger();
        assertTrue(walletDeathLoggerOptional.isPresent());
        WalletDeathLogger walletDeathLogger = walletDeathLoggerOptional.get();
        assertInstanceOf(HibernateWalletDeathLogger.class, walletDeathLogger);
    }

    @Test
    void testParseDatabaseWalletStorage() {
        NanoPayConfiguration nanoPayConfiguration = mock(NanoPayConfiguration.class);
        doCallRealMethod().when(nanoPayConfiguration).getRequiredString(anyString());
        doCallRealMethod().when(nanoPayConfiguration).getRequiredInt(anyString());
        doCallRealMethod().when(nanoPayConfiguration).getInt(anyString());
        doCallRealMethod().when(nanoPayConfiguration).getBoolean(anyString(), anyBoolean());
        doCallRealMethod().when(nanoPayConfiguration).parseWalletStorage(WalletType.ACTIVE);
        doCallRealMethod().when(nanoPayConfiguration).parseDatabase(anyString());
        doReturn(Optional.of("database")).when(nanoPayConfiguration).getString("nanopay.storage.active.type");
        doReturn(Optional.of("30")).when(nanoPayConfiguration).getString("nanopay.storage.active.duration.amount");
        doReturn(Optional.of("minutes")).when(nanoPayConfiguration).getString("nanopay.storage.active.duration.unit");
        doReturn(Optional.of("jdbc:h2:mem:testdb")).when(nanoPayConfiguration).getString("nanopay.storage.active.url");
        doReturn(Optional.of("org.h2.Driver")).when(nanoPayConfiguration).getString("nanopay.storage.active.driver");
        doReturn(Optional.of("create-drop")).when(nanoPayConfiguration).getString("nanopay.storage.active.hbm2ddl");
        doReturn(Optional.of("false")).when(nanoPayConfiguration).getString("nanopay.storage.active.cache");

        Optional<WalletStorage> walletStorageOptional = nanoPayConfiguration.parseWalletStorage(WalletType.ACTIVE);
        assertTrue(walletStorageOptional.isPresent());
        WalletStorage walletStorage = walletStorageOptional.get();
        assertInstanceOf(HibernateWalletStorage.class, walletStorage);
        assertEquals(walletStorage.getWalletExpirationTime(), Duration.of(30, ChronoUnit.MINUTES));
    }

    @Test
    void testParseMemoryWalletStorage() {
        NanoPayConfiguration nanoPayConfiguration = mock(NanoPayConfiguration.class);
        doCallRealMethod().when(nanoPayConfiguration).getRequiredString(anyString());
        doCallRealMethod().when(nanoPayConfiguration).getRequiredInt(anyString());
        doCallRealMethod().when(nanoPayConfiguration).getInt(anyString());
        doCallRealMethod().when(nanoPayConfiguration).getBoolean(anyString(), anyBoolean());
        doCallRealMethod().when(nanoPayConfiguration).parseWalletStorage(WalletType.ACTIVE);
        doReturn(Optional.of("memory")).when(nanoPayConfiguration).getString("nanopay.storage.active.type");
        doReturn(Optional.of("30")).when(nanoPayConfiguration).getString("nanopay.storage.active.duration.amount");
        doReturn(Optional.of("minutes")).when(nanoPayConfiguration).getString("nanopay.storage.active.duration.unit");

        Optional<WalletStorage> walletStorageOptional = nanoPayConfiguration.parseWalletStorage(WalletType.ACTIVE);
        assertTrue(walletStorageOptional.isPresent());
        WalletStorage walletStorage = walletStorageOptional.get();
        assertInstanceOf(MemoryWalletStorage.class, walletStorage);
        assertEquals(walletStorage.getWalletExpirationTime(), Duration.of(30, ChronoUnit.MINUTES));
    }

    @Test
    void testParseSingleFileWalletStorage(@TempDir Path tempDir) throws IOException {
        Path tempFile = Files.createFile(tempDir.resolve("wallet_storage.json"));

        NanoPayConfiguration nanoPayConfiguration = mock(NanoPayConfiguration.class);
        doCallRealMethod().when(nanoPayConfiguration).getRequiredString(anyString());
        doCallRealMethod().when(nanoPayConfiguration).getRequiredInt(anyString());
        doCallRealMethod().when(nanoPayConfiguration).getInt(anyString());
        doCallRealMethod().when(nanoPayConfiguration).getBoolean(anyString(), anyBoolean());
        doCallRealMethod().when(nanoPayConfiguration).parseWalletStorage(WalletType.ACTIVE);
        doReturn(Optional.of("single_file")).when(nanoPayConfiguration).getString("nanopay.storage.active.type");
        doReturn(Optional.of(tempFile.toString())).when(nanoPayConfiguration).getString("nanopay.storage.active.path");
        doReturn(Optional.of("30")).when(nanoPayConfiguration).getString("nanopay.storage.active.duration.amount");
        doReturn(Optional.of("minutes")).when(nanoPayConfiguration).getString("nanopay.storage.active.duration.unit");

        Optional<WalletStorage> walletStorageOptional = nanoPayConfiguration.parseWalletStorage(WalletType.ACTIVE);
        assertTrue(walletStorageOptional.isPresent());
        WalletStorage walletStorage = walletStorageOptional.get();
        assertInstanceOf(SingleFileWalletStorage.class, walletStorage);
        assertEquals(walletStorage.getWalletExpirationTime(), Duration.of(30, ChronoUnit.MINUTES));
    }

    @Test
    void testParseMultiFileWalletStorage(@TempDir Path tempDir) {
        NanoPayConfiguration nanoPayConfiguration = mock(NanoPayConfiguration.class);
        doCallRealMethod().when(nanoPayConfiguration).getRequiredString(anyString());
        doCallRealMethod().when(nanoPayConfiguration).getRequiredInt(anyString());
        doCallRealMethod().when(nanoPayConfiguration).getInt(anyString());
        doCallRealMethod().when(nanoPayConfiguration).getBoolean(anyString(), anyBoolean());
        doCallRealMethod().when(nanoPayConfiguration).parseWalletStorage(WalletType.ACTIVE);
        doReturn(Optional.of("multi_file")).when(nanoPayConfiguration).getString("nanopay.storage.active.type");
        doReturn(Optional.of(tempDir.toString())).when(nanoPayConfiguration).getString("nanopay.storage.active.path");
        doReturn(Optional.of("30")).when(nanoPayConfiguration).getString("nanopay.storage.active.duration.amount");
        doReturn(Optional.of("minutes")).when(nanoPayConfiguration).getString("nanopay.storage.active.duration.unit");

        Optional<WalletStorage> walletStorageOptional = nanoPayConfiguration.parseWalletStorage(WalletType.ACTIVE);
        assertTrue(walletStorageOptional.isPresent());
        WalletStorage walletStorage = walletStorageOptional.get();
        assertInstanceOf(MultipleFileWalletStorage.class, walletStorage);
        assertEquals(walletStorage.getWalletExpirationTime(), Duration.of(30, ChronoUnit.MINUTES));
    }

}