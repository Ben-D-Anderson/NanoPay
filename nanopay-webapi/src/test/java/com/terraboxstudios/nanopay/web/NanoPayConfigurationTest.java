package com.terraboxstudios.nanopay.web;

import com.terraboxstudios.nanopay.death.WalletDeathLogger;
import com.terraboxstudios.nanopay.hibernate.HibernateWalletDeathLogger;
import com.terraboxstudios.nanopay.hibernate.HibernateWalletStorage;
import com.terraboxstudios.nanopay.storage.MemoryWalletStorage;
import com.terraboxstudios.nanopay.storage.WalletStorage;
import com.terraboxstudios.nanopay.storage.WalletType;
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
        doCallRealMethod().when(nanoPayConfiguration).getInt(anyString());
        doCallRealMethod().when(nanoPayConfiguration).getBoolean(anyString());
        doCallRealMethod().when(nanoPayConfiguration).parseWalletDeathLogger();
        doReturn("database").when(nanoPayConfiguration).getString("nanopay.deathlog.type");
        doReturn("jdbc:h2:mem:testdb").when(nanoPayConfiguration).getString("nanopay.deathlog.url");
        doReturn("org.h2.Driver").when(nanoPayConfiguration).getString("nanopay.deathlog.driver");
        doReturn("create-drop").when(nanoPayConfiguration).getString("nanopay.deathlog.hbm2ddl");

        Optional<WalletDeathLogger> walletDeathLoggerOptional = nanoPayConfiguration.parseWalletDeathLogger();
        assertTrue(walletDeathLoggerOptional.isPresent());
        WalletDeathLogger walletDeathLogger = walletDeathLoggerOptional.get();
        assertInstanceOf(HibernateWalletDeathLogger.class, walletDeathLogger);
    }

    @Test
    void testParseDatabaseWalletStorage() {
        NanoPayConfiguration nanoPayConfiguration = mock(NanoPayConfiguration.class);
        doCallRealMethod().when(nanoPayConfiguration).getInt(anyString());
        doCallRealMethod().when(nanoPayConfiguration).getBoolean(anyString());
        doCallRealMethod().when(nanoPayConfiguration).parseWalletStorage(WalletType.ACTIVE);
        doReturn("database").when(nanoPayConfiguration).getString("nanopay.storage.active.type");
        doReturn("30").when(nanoPayConfiguration).getString("nanopay.storage.active.duration.amount");
        doReturn("minutes").when(nanoPayConfiguration).getString("nanopay.storage.active.duration.unit");
        doReturn("jdbc:h2:mem:testdb").when(nanoPayConfiguration).getString("nanopay.storage.active.url");
        doReturn("org.h2.Driver").when(nanoPayConfiguration).getString("nanopay.storage.active.driver");
        doReturn("create-drop").when(nanoPayConfiguration).getString("nanopay.storage.active.hbm2ddl");

        Optional<WalletStorage> walletStorageOptional = nanoPayConfiguration.parseWalletStorage(WalletType.ACTIVE);
        assertTrue(walletStorageOptional.isPresent());
        WalletStorage walletStorage = walletStorageOptional.get();
        assertInstanceOf(HibernateWalletStorage.class, walletStorage);
        assertEquals(walletStorage.getWalletExpirationTime(), Duration.of(30, ChronoUnit.MINUTES));
    }

    @Test
    void testParseMemoryWalletStorage() {
        NanoPayConfiguration nanoPayConfiguration = mock(NanoPayConfiguration.class);
        doCallRealMethod().when(nanoPayConfiguration).getInt(anyString());
        doCallRealMethod().when(nanoPayConfiguration).getBoolean(anyString());
        doCallRealMethod().when(nanoPayConfiguration).parseWalletStorage(WalletType.ACTIVE);
        doReturn("memory").when(nanoPayConfiguration).getString("nanopay.storage.active.type");
        doReturn("30").when(nanoPayConfiguration).getString("nanopay.storage.active.duration.amount");
        doReturn("minutes").when(nanoPayConfiguration).getString("nanopay.storage.active.duration.unit");

        Optional<WalletStorage> walletStorageOptional = nanoPayConfiguration.parseWalletStorage(WalletType.ACTIVE);
        assertTrue(walletStorageOptional.isPresent());
        WalletStorage walletStorage = walletStorageOptional.get();
        assertInstanceOf(MemoryWalletStorage.class, walletStorage);
        assertEquals(walletStorage.getWalletExpirationTime(), Duration.of(30, ChronoUnit.MINUTES));
    }

}