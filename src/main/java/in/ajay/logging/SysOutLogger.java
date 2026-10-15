package in.ajay.logging;

public class SysOutLogger implements ILogger{

    //todo log different levels of messages based on env variables

    @Override
    public void LogInfo(String message) {
        IO.println(this.formatMessage(message));
    }

    @Override
    public void LogDebug(String message) {
        IO.println(this.formatMessage(message));
    }

    @Override
    public void LogTrace(String message) {
        IO.println(message);
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
