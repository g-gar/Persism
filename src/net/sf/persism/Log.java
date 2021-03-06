package net.sf.persism;


import net.sf.persism.logging.AbstractLogger;
import net.sf.persism.logging.LogMode;
import net.sf.persism.logging.implementation.JulLogger;
import net.sf.persism.logging.implementation.Log4jLogger;
import net.sf.persism.logging.implementation.Slf4jLogger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Logging wrapper to avoid runtime dependencies.
 * It will use slf4j if available or log4j if available otherwise it falls back to JUL (java.util.logging).
 * <p/>
 * JUL will use the default java logging.properties
 * log4j will use log4j.properties
 * slf4j will use logback.xml
 * <p/>
 * log4j DEBUG maps to JUL FINE
 * log4j ERROR maps to JUL SEVERE
 * <p/>
 * The class includes an overloaded error method to log stack traces as well as the message.
 *
 * @author Dan Howard
 * @since 4/21/12 6:47 AM
 */
final class Log {

    private AbstractLogger logger;

    private static final Map<String, Log> loggers = new ConcurrentHashMap<String, Log>(12);

    private Log() {
    }

    public Log(String logName) {
        try {
            Class.forName("org.slf4j.Logger");
            logger = new Slf4jLogger(logName);
        } catch (ClassNotFoundException e) {
            try {
                Class.forName("org.apache.log4j.Logger");
                logger = new Log4jLogger(logName);
            } catch (ClassNotFoundException e1) {
                logger = new JulLogger(logName);
            }
        }
        assert logger != null;
    }

    public static Log getLogger(Class logName) {
        return getLogger(logName.getName());
    }

    public static Log getLogger(String logName) {
        if (loggers.containsKey(logName)) {
            return loggers.get(logName);
        }
        Log log = new Log(logName);
        loggers.put(logName, log);
        return log;
    }


    public boolean isDebugEnabled() {
        return logger.isDebugEnabled();
    }

    public void debug(Object message, Object... params) {
        logger.debug(message, params);
    }

    public void info(Object message) {
        logger.info(message);
    }

    public void warn(Object message) {
        logger.warn(message);
    }

    public void warn(Object message, Throwable t) {
        logger.warn(message, t);
    }


    public void error(Object message) {
        logger.error(message);
    }

    public void error(Object message, Throwable t) {
        logger.error(message, t);
    }

    public LogMode getLogMode() {
        return logger.getLogMode();
    }

    public String getLogName() {
        return logger.getLogName();
    }
}
