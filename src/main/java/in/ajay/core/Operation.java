package in.ajay.core;
import java.io.IOException;

public enum Operation {
    SET(1),
    GET(2),
    EXISTS(3),
    DELETE(4);

    private final int code;

    Operation(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static Operation fromCode(int code) throws IOException {
        for (Operation command : values()) {
            if (command.code == code) {
                return command;
            }
        }
        throw new IOException("Unknown command code: " + code);
    }
}