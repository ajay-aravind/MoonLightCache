package in.ajay.logging;

public interface ILogger {
    public void LogInfo(String message);
    public void LogDebug(String message);
    public void LogTrace(String message);
    public void LogWarning(String message);
    public void LogError(String message);
}
