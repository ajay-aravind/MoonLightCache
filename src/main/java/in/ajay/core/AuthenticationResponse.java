package in.ajay.core;

import java.io.IOException;

public enum AuthenticationResponse {
    SUCCESSFUL(1),
    UNSUCCESSFUL(2);

    private final int code;

    AuthenticationResponse(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static AuthenticationResponse fromCode(int code) throws IOException {
        for (AuthenticationResponse command : values()) {
            if (command.code == code) {
                return command;
            }
        }
        throw new IOException("Unknown Response code: " + code);
    }
}
