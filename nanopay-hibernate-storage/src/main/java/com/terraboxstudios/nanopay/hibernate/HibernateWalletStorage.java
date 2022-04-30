package com.terraboxstudios.nanopay.hibernate;

import com.terraboxstudios.nanopay.NanoPay;
import com.terraboxstudios.nanopay.storage.WalletStorage;
import com.terraboxstudios.nanopay.wallet.Wallet;
import lombok.Getter;
import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.query.Query;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;
import java.time.Duration;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class HibernateWalletStorage implements WalletStorage, AutoCloseable {

    private final Duration walletExpiryTime;
    private final SessionFactory databaseSessionFactory;
    @Getter
    private final boolean activeWalletStorage;

    @Override
    public void close() {
        databaseSessionFactory.close();
    }

    enum WalletType {
        ACTIVE,
        DEAD
    }

    public HibernateWalletStorage(WalletType walletType, Duration walletExpiryTime, SessionFactory databaseSessionFactory) {
        this.activeWalletStorage = walletType == WalletType.ACTIVE;
        this.walletExpiryTime = walletExpiryTime;
        this.databaseSessionFactory = databaseSessionFactory;
    }

    private Runnable createRunnable(Consumer<Session> sessionConsumer) {
        return () -> {
            try (Session session = databaseSessionFactory.openSession()) {
                sessionConsumer.accept(session);
            } catch (HibernateException e) {
                NanoPay.LOGGER.error("Hibernate error occurred.", e);
            }
        };
    }

    private <T> Callable<Optional<T>> createCallable(Function<Session, T> sessionFunction) {
        return () -> {
            T callbackValue;
            try (Session session = databaseSessionFactory.openSession()) {
                callbackValue = sessionFunction.apply(session);
            } catch (HibernateException e) {
                NanoPay.LOGGER.error("Hibernate error occurred.", e);
                return Optional.empty();
            }
            return Optional.ofNullable(callbackValue);
        };
    }

    @Override
    public Collection<Wallet> getAllWallets() {
        Callable<Optional<List<WalletEntity>>> findCallable = createCallable(session -> {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<WalletEntity> cr = cb.createQuery(WalletEntity.class);
            Root<WalletEntity> root = cr.from(WalletEntity.class);
            cr.select(root).where(cb.equal(root.get("walletEntityId").get("active"), isActiveWalletStorage()));
            Query<WalletEntity> query = session.createQuery(cr);
            return query.getResultList();
        });
        try {
            return findCallable.call().map(walletEntities ->
                    walletEntities.stream().map(WalletEntity::asWallet).collect(Collectors.toSet())
            ).orElse(new HashSet<>());
        } catch (Exception e) {
            NanoPay.LOGGER.error("Hibernate error occurred when getting all wallets.", e);
            return new HashSet<>();
        }
    }

    @Override
    public Optional<Wallet> findWalletByAddress(String address) {
        Callable<Optional<WalletEntity>> findCallable = createCallable(session ->
                session.get(WalletEntity.class, new WalletEntity.WalletEntityId(address, isActiveWalletStorage())));
        try {
            return findCallable.call().map(WalletEntity::asWallet);
        } catch (Exception e) {
            NanoPay.LOGGER.error("Hibernate error occurred when finding wallet '" + address + "'.", e);
            return Optional.empty();
        }
    }

    @Override
    public void saveWallet(Wallet wallet) {
        Runnable saveRunnable = createRunnable(session -> {
            session.beginTransaction();
            try {
                session.save(new WalletEntity(wallet, isActiveWalletStorage()));
            } catch (HibernateException e) {
                NanoPay.LOGGER.error("Hibernate error occurred when saving wallet '" + wallet.address() + "'.", e);
            }
            session.getTransaction().commit();
        });
        saveRunnable.run();
    }

    @Override
    public void deleteWallet(Wallet wallet) {
        Runnable deleteRunnable = createRunnable(session -> {
            session.beginTransaction();
            try {
                session.delete(new WalletEntity(wallet, isActiveWalletStorage()));
            } catch (HibernateException e) {
                NanoPay.LOGGER.error("Hibernate error occurred when deleting wallet '" + wallet.address() + "'.", e);
            }
            session.getTransaction().commit();
        });
        deleteRunnable.run();
    }

    @Override
    public Duration getWalletExpirationTime() {
        return walletExpiryTime;
    }

}
