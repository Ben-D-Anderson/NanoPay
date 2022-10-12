package xyz.benanderson.nanopay.hibernate;

import xyz.benanderson.nanopay.NanoPay;
import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;

public abstract class DatabaseAccessor implements AutoCloseable {

    private final SessionFactory databaseSessionFactory;

    public DatabaseAccessor(SessionFactory databaseSessionFactory) {
        this.databaseSessionFactory = databaseSessionFactory;
    }

    @Override
    public void close() {
        databaseSessionFactory.close();
    }

    protected Runnable createRunnable(Consumer<Session> sessionConsumer) {
        return () -> {
            try (Session session = databaseSessionFactory.openSession()) {
                sessionConsumer.accept(session);
            } catch (HibernateException e) {
                NanoPay.LOGGER.error("Hibernate error occurred.", e);
            }
        };
    }

    protected <T> Callable<Optional<T>> createCallable(Function<Session, T> sessionFunction) {
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

}
