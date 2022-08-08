package gg.essential.loader.stage2;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.RollingRandomAccessFileAppender;
import org.apache.logging.log4j.core.appender.rolling.CompositeTriggeringPolicy;
import org.apache.logging.log4j.core.appender.rolling.OnStartupTriggeringPolicy;
import org.apache.logging.log4j.core.appender.rolling.SizeBasedTriggeringPolicy;
import org.apache.logging.log4j.core.config.AppenderRef;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.layout.PatternLayout;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Log4j2Hacks {
    public static void addDebugLogFile(Level level) {
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        Configuration config = ctx.getConfiguration();


        PatternLayout layout = PatternLayout.newBuilder()
            .withPattern("[%d{HH:mm:ss}] [%t/%level] (%logger) %msg{nolookups}%n")
            .build();

        Appender debugFileAppender = RollingRandomAccessFileAppender.newBuilder()
            .withName("DebugFile")
            .withFileName("logs/debug.log")
            .withFilePattern("logs/debug-%i.log.gz")
            .withPolicy(CompositeTriggeringPolicy.createPolicy(
                SizeBasedTriggeringPolicy.createPolicy("200MB"),
                OnStartupTriggeringPolicy.createPolicy(1)
            ))
            .withLayout(layout)
            .build();
        debugFileAppender.start();
        config.addAppender(debugFileAppender);


        LoggerConfig rootLogger = config.getRootLogger();

        Map<String, Appender> originalAppenderMap = new HashMap<>(rootLogger.getAppenders());
        for (String name : originalAppenderMap.keySet()) {
            rootLogger.removeAppender(name);
        }

        List<AppenderRef> appenderRefs = rootLogger.getAppenderRefs();
        for (int i = 0; i < appenderRefs.size(); i++) {
            AppenderRef ref = appenderRefs.get(i);
            if (ref.getLevel() == null) {
                ref = AppenderRef.createAppenderRef(ref.getRef(), rootLogger.getLevel(), ref.getFilter());
                appenderRefs.set(i, ref);
            }
            Appender appender = originalAppenderMap.get(ref.getRef());
            if (appender != null) {
                rootLogger.addAppender(appender, ref.getLevel(),ref.getFilter());
            }
        }

        rootLogger.addAppender(debugFileAppender, level, null);
        rootLogger.setLevel(level);


        ctx.updateLoggers();
    }
}
