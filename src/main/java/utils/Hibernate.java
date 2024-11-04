package utils;

import java.io.File;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Logger;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.cfg.Configuration;
import org.hibernate.exception.ConstraintViolationException;

import tukano.api.Result;
import tukano.api.Result.ErrorCode;
import tukano.impl.azure.AzureBlobs;

/**
 * A helper class to perform POJO (Plain Old Java Objects) persistence, using
 * Hibernate and a backing relational database.
 * 
 * @param <Session>
 */
public class Hibernate {
    private SessionFactory sessionFactory;
    private static Hibernate instance;
    private static Logger Log = Logger.getLogger(Hibernate.class.getName());

    private Hibernate() {

        try {
            var hibernateCfgFile = System.getProperty("ENV").equals("local")
                    ? "/usr/local/tomcat/webapps/tukano/WEB-INF/classes/hibernate.cfg.xml"
                    : "/usr/local/tomcat/webapps/ROOT/WEB-INF/classes/hibernate.cfg.xml";

            Log.info("Attempting to load Hibernate config from: " + hibernateCfgFile);

            File configFile = new File(hibernateCfgFile);
            if (!configFile.exists()) {
                Log.severe("Hibernate config file does not exist at: " + hibernateCfgFile);
                throw new RuntimeException("Hibernate config file not found");
            }

            Configuration configuration = new Configuration();
            try {
                configuration.configure(configFile);
            } catch (Exception e) {
                Log.severe("Error configuring Hibernate: " + e.getMessage());
                throw e;
            }

            try {
                sessionFactory = configuration.buildSessionFactory();
                Log.info("Hibernate SessionFactory successfully initialized");
            } catch (Exception e) {
                Log.severe("Error building SessionFactory: " + e.getMessage());
                throw e;
            }

        } catch (Exception e) {
            Log.severe("Failed to initialize Hibernate: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Could not initialize Hibernate", e);
        }
    }
    // [/usr/local/tomcat/webapp/WEB-INF/classes/hibernate.cfg.xml]

    /**
     * Returns the Hibernate instance, initializing if necessary. Requires a
     * configuration file (hibernate.cfg.xml)
     * 
     * @return
     */
    synchronized public static Hibernate getInstance() {
        if (instance == null)
            instance = new Hibernate();
        return instance;
    }

    public Result<Void> persistOne(Object obj) {
        return execute((hibernate) -> {
            hibernate.persist(obj);
        });
    }

    public <T> Result<T> updateOne(T obj) {
        return execute(hibernate -> {
            var res = hibernate.merge(obj);
            if (res == null)
                return Result.error(ErrorCode.NOT_FOUND);

            return Result.ok(res);
        });
    }

    public <T> Result<T> deleteOne(T obj) {
        return execute(hibernate -> {
            hibernate.remove(obj);
            return Result.ok(obj);
        });
    }

    public <T> Result<T> getOne(Object id, Class<T> clazz) {
        try (var session = sessionFactory.openSession()) {
            var res = session.find(clazz, id);
            if (res == null)
                return Result.error(ErrorCode.NOT_FOUND);
            else
                return Result.ok(res);
        } catch (Exception e) {
            throw e;
        }
    }

    public <T> List<T> sql(String sqlStatement, Class<T> clazz) {
        Session session = null;
        try {
            session = sessionFactory.openSession();
            var query = session.createNativeQuery(sqlStatement, clazz);
            return query.list();
        } catch (Exception e) {
            throw e;
        } finally {
            if (session != null && session.isOpen()) {
                session.close();
            }
        }
    }

    public <T> Result<T> execute(Consumer<Session> proc) {
        return execute((hibernate) -> {
            proc.accept(hibernate);
            return Result.ok();
        });
    }

    public <T> Result<T> execute(Function<Session, Result<T>> func) {
        Transaction tx = null;
        Session session = null;

        try {
            session = sessionFactory.openSession();
            tx = session.beginTransaction();
            var res = func.apply(session);
            session.flush();
            tx.commit();
            return res;

        } catch (ConstraintViolationException __) {
            if (tx != null)
                tx.rollback();
            return Result.error(ErrorCode.CONFLICT);

        } catch (Exception e) {
            if (tx != null)
                tx.rollback();
            e.printStackTrace();
            throw e;

        } finally {
            if (session != null && session.isOpen()) {
                session.close();
            }
        }
    }
}
