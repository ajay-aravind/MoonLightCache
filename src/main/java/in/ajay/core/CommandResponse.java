package in.ajay.core;

import java.util.Arrays;

public class CommandResponse {

    // operation response status
    private OperationResponseCode statusCode;
    private String key;
    private byte[] value;
    private long ttl;
    private DataType type;

    public CommandResponse(OperationResponseCode statusCode, String key, byte[] value, DataType type) {
        this.statusCode = statusCode;
        this.key = key;
        this.value = value;
        this.ttl = ttl;
        this.type = type;
    }

    public CommandResponse() {
    }

    @Override
    public String toString() {
        return "CommandResponse{" +
                "statusCode=" + statusCode +
                ", key='" + key + '\'' +
                ", value=" + Arrays.toString(value) +
                ", ttl=" + ttl +
                ", type=" + type +
                '}';
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

    public byte[] getValue() {
        return value;
    }

    public void setValue(byte[] value) {
        this.value = value;
    }

    public long getTtl() {
        return ttl;
    }

    public void setTtl(long ttl) {
        this.ttl = ttl;
    }

    public DataType getType() {
        return type;
    }

    public void setType(DataType type) {
        this.type = type;
    }

    public CommandResponse(OperationResponseCode statusCode, String key) {
        this.statusCode = statusCode;
        this.key = key;
    }
}

