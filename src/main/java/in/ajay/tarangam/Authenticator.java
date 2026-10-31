package in.ajay.tarangam;

import in.ajay.core.AuthenticationRequest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class Authenticator {

    private final byte[] username;
    private final byte[] password;

    public Authenticator() {
        String user = System.getenv("ADMIN_USERNAME");
        String pass = System.getenv("ADMIN_PASSWORD");

        if (user == null || user.isBlank()
                || pass == null || pass.isEmpty()) {
            throw new IllegalStateException(
                    "Admin credentials are not configured");
        }

        this.username = user.getBytes(StandardCharsets.UTF_8);
        this.password = pass.getBytes(StandardCharsets.UTF_8);
    }

    public boolean authenticate(AuthenticationRequest authRequest) {
        byte[] suppliedUser =
                authRequest.getUserName().getBytes(StandardCharsets.UTF_8);

        byte[] suppliedPass =
                authRequest.getPassword().getBytes(StandardCharsets.UTF_8);

        return MessageDigest.isEqual(username, suppliedUser)
                & MessageDigest.isEqual(password, suppliedPass);
    }
}