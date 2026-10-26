package in.ajay.core;

import java.io.IOException;

public enum OperationResponseCode {
    EXISTS(1),
    NOTEXISTS(2),
    CREATED(3),
    FOUND(4),
    FAILED(5),
    DELETED(6),
    INVALID_OPERATION(7);

    private final int code;

    OperationResponseCode(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static OperationResponseCode fromCode(int code) throws IOException {
        for (OperationResponseCode command : values()) {
            if (command.code == code) {
                return command;
            }
        }
        throw new IOException("Unknown command code: " + code);
    }
}
