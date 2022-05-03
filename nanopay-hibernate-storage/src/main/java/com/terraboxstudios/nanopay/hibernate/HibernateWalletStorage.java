package com.terraboxstudios.nanopay.hibernate;

import com.terraboxstudios.nanopay.NanoPay;
import com.terraboxstudios.nanopay.hibernate.entity.WalletEntity;
import com.terraboxstudios.nanopay.storage.WalletStorage;
import com.terraboxstudios.nanopay.storage.WalletType;
import com.terraboxstudios.nanopay.wallet.Wallet;
import org.hibernate.HibernateException;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.hibernate.query.Query;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

public class HibernateWalletStorage extends DatabaseAccessor implements WalletStorage, AutoCloseable {

    private final Duration walletExpiryTime;
    private final WalletType walletType;

    public HibernateWalletStorage(WalletType walletType, Duration walletExpiryTime, Configuration databaseConfiguration) {
        this(walletType, walletExpiryTime, databaseConfiguration
                .addAnnotatedClass(WalletEntity.class)
                .addAnnotatedClass(WalletEntity.WalletEntityId.class)
                .buildSessionFactory());
    }

    HibernateWalletStorage(WalletType walletType, Duration walletExpiryTime, SessionFactory databaseSessionFactory) {
        super(databaseSessionFactory);
        this.walletType = walletType;
        this.walletExpiryTime = walletExpiryTime;
    }

    @Override
    public Collection<Wallet> getAllWallets() {
        Callable<Optional<List<WalletEntity>>> findCallable = createCallable(session -> {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<WalletEntity> cr = cb.createQuery(WalletEntity.class);
            Root<WalletEntity> root = cr.from(WalletEntity.class);
            cr.select(root).where(cb.equal(root.get("walletEntityId").get("walletType"), walletType));
            Query<WalletEntity> query = session.createQuery(cr);
            return query.getResultList();
        });
        try {
            return findCallable.call().map(walletEntities ->
                    walletEntities.stream().map(WalletEntity::asWallet).collect(Collectors.toSet())
            ).orElse(Collections.emptySet());
        } catch (Exception e) {
            NanoPay.LOGGER.error("Hibernate error occurred when getting all wallets.", e);
            return Collections.emptySet();
        }
    }

    @Override
    public Optional<Wallet> findWalletByAddress(String address) {
        Callable<Optional<WalletEntity>> findCallable = createCallable(session ->
                session.get(WalletEntity.class, new WalletEntity.WalletEntityId(address, walletType)));
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
                session.save(new WalletEntity(wallet, walletType));
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
                session.delete(new WalletEntity(wallet, walletType));
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
