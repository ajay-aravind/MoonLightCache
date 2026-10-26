package in.ajay.core;

import java.io.IOException;

public enum DataType {
    stringType(1),
    integerType(2),
    longType(3),
    boolType(4),
    characterType(5);

    private final int code;

    DataType(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static DataType fromCode(int code) throws IOException {
        for (DataType command : values()) {
            if (command.code == code) {
                return command;
            }
        }
        throw new IOException("Unknown command code: " + code);
    }
}
