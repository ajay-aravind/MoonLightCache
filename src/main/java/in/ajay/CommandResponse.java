package in.ajay;

public class CommandResponse {

    // operation response status
    private OperationResponseCode statusCode;
    private String key;
    private String value;
    private long ttl;

    public CommandResponse(OperationResponseCode statusCode, String key, String value, long ttl) {
        this.statusCode = statusCode;
        this.key = key;
        this.value = value;
        this.ttl = ttl;
    }

    public OperationResponseCode getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(OperationResponseCode statusCode) {
        this.statusCode = statusCode;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public long getTtl() {
        return ttl;
    }

    public void setTtl(long ttl) {
        this.ttl = ttl;
    }

    public CommandResponse(OperationResponseCode statusCode) {
        this.statusCode = statusCode;
    }
}

