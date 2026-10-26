package in.ajay.logging;

import in.ajay.core.AppConfig;

public class SysOutLogger implements ILogger{

    //todo log different levels of messages based on env variables

    private final String LOG_LEVEL = AppConfig.get(
            "log.level");

    @Override
    public void LogInfo(String message) {
        if(LOG_LEVEL.equals("info")||LOG_LEVEL.equals("debug")||LOG_LEVEL.equals("trace")) {
            IO.println(this.formatMessage(message));
        }
    }

    @Override
    public void LogDebug(String message) {
        if(LOG_LEVEL.equals("debug")||LOG_LEVEL.equals("trace")) {
            IO.println(this.formatMessage(message));
        }
    }

    @Override
    public void LogTrace(String message) {
        if(LOG_LEVEL.equals("trace")) {
            IO.println(message);
        }
    }

    @Override
    public void LogWarning(String message) {
        IO.println(message);
    }

    @Override
    public void LogError(String message) {
        IO.println(message);
    }

    private String formatMessage(String message){
        String logLine = String.format(
                "[%s] [Thread-%d] %s",
                java.time.LocalDateTime.now(),
                Thread.currentThread().threadId(),
                message
        );

        return logLine;
    }
}
