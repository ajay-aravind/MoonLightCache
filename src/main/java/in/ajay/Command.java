package in.ajay;

public class Command {
    /*
    Should be one of the valid operations
     */
    private Operation operation;
    /*
    Key shouldn't be null
    minSize: 3; maxSize: 100
     */
    private String key;
    /*
    value shouldn't be null
    minSize: 1byte, maxSize: 100 bytes
     */
    private String value;
    /*
    should be a valid int and cannot be less than current epoch
     */
    private long ttl;
    /*
    should be a valid datatype enum
     */
    private DataType type;

    public Command(Operation operation, String key, String value, long ttl, DataType type) {
        this.operation = operation;
        this.key = key;
        this.value = value;
        this.ttl = ttl;
        this.type = type;
    }

    public Operation getOperation() {
        return operation;
    }

    public void setOperation(Operation operation) {
        this.operation = operation;
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

    public DataType getType() {
        return type;
    }

    public void setType(DataType type) {
        this.type = type;
    }
}

