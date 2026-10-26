package in.ajay.core;

import in.ajay.tarangam.RequestParser;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public class Command {

    private static final int LENGTH_PREFIX_BYTES = 4;
    private static final int MAX_FRAME_BYTES = 2 * 1024 * 1024;
    private static final int MAX_KEY_BYTES = 65_535;
    private static final int MAX_VALUE_BYTES = 1024 * 1024;
    
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
    private byte[] value;
    /*
    should be a valid int and cannot be less than current epoch
     */
    private long ttl;
    /*
    should be a valid datatype enum
     */
    private DataType type;

    public Command(Operation operation, String key, byte[] value, long ttl, DataType type) {
        this.operation = operation;
        this.key = key;
        this.value = value;
        this.ttl = ttl;
        this.type = type;
    }

    public Command(Operation operation, String key) {
        this.operation = operation;
        this.key = key;
    }

    public Command() {}

    private void validateMessage() {
        if (key.isEmpty()) {
            throw new IllegalArgumentException("Key cannot be empty");
        }

        int keyLength = key.getBytes(StandardCharsets.UTF_8).length;
        if (keyLength > MAX_KEY_BYTES) {
            throw new IllegalArgumentException(
                    "Key exceeds " + MAX_KEY_BYTES + " UTF-8 bytes");
        }

        if (this.ttl < -1) {
            throw new IllegalArgumentException(
                    "TTL must be -1 (no expiry) or nonnegative");
        }

        if (operation == Operation.SET) {
            Objects.requireNonNull(type, "SET requires a data type");
            Objects.requireNonNull(value, "SET requires a value");

            if (value.length > MAX_VALUE_BYTES) {
                throw new IllegalArgumentException(
                        "Value exceeds " + MAX_VALUE_BYTES + " bytes");
            }

            validateValueLength(type, value.length);
        } else {
            if (type != null || value != null) {
                throw new IllegalArgumentException(
                        operation + " must not contain a value or data type");
            }
        }
    }

    public static void validateValueLength(
            DataType type, int length) {

        switch (type) {
            case DataType.integerType:
                if (length != Integer.BYTES) {
                    throw new IllegalArgumentException(
                            "INT32 requires exactly 4 bytes");
                }
                break;
            case DataType.longType:
                if (length != Long.BYTES) {
                    throw new IllegalArgumentException(
                            "INT64 requires exactly 8 bytes");
                }
                break;
            case DataType.boolType:
                if (length != 1) {
                    throw new IllegalArgumentException(
                            "BOOLEAN requires exactly 1 byte");
                }
                if (valueBooleanByteInvalidPlaceholder()) {
                    // Not used; boolean validation occurs in decode.
                }
                break;
            default:
                break;
        }
    }

    private static boolean valueBooleanByteInvalidPlaceholder() {
        return false;
    }

    public static Command set(
            String key, DataType type, byte[] value, long ttlMillis) {
        return new Command(Operation.SET, key, value, ttlMillis, type);
    }

    public static Command get(String key) {
        return new Command(Operation.GET, key, null, -1, null);
    }

    public static Command exists(String key) {
        return new Command(Operation.EXISTS, key, null,-1, null);
    }

    public static Command delete(String key) {
        return new Command(Operation.DELETE, key, null, -1, null);
    }

    public String toParse() {
        StringBuilder result = new StringBuilder()
                .append("Operation: ").append(operation)
                .append("\nKey: ").append(key)
                .append("\nTTL: ");

        if (ttl == -1) {
            result.append("no expiry");
        } else {
            result.append(ttl).append(" ms");
        }

        if (value != null) {
            result.append("\nDataType: ").append(type)
                    .append("\nValueLength: ").append(value.length)
                    .append(" bytes\nValue: ").append(formatValue());
        }

        return result.toString();
    }

    private String formatValue() {
        ByteBuffer buffer = ByteBuffer.wrap(value)
                .order(ByteOrder.BIG_ENDIAN);

        switch (this.type) {
            case DataType.stringType:
                return new String(value, StandardCharsets.UTF_8);
            case DataType.characterType:
                return new String(value, StandardCharsets.UTF_8);
            case DataType.integerType:
                return Integer.toString(buffer.getInt());
            case DataType.longType:
                return Long.toString(buffer.getLong());
            case DataType.boolType:
                return value[0] == 1 ? "true" : "false";
            default:
                return "<unknown>";
        }
    }

    @Override
    public String toString() {
        return toParse();
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
}

