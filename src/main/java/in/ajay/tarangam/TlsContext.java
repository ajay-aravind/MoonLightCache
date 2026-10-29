package in.ajay.tarangam;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;

public final class TlsContext {

    private TlsContext() {}

    public static SSLContext createServerContext()
            throws GeneralSecurityException, IOException {

        String keystorePath =
                requiredEnv("CACHE_TLS_KEYSTORE");

        char[] password =
                requiredEnv("CACHE_TLS_KEYSTORE_PASSWORD").toCharArray();

        try {
            KeyStore keyStore = KeyStore.getInstance("PKCS12");

            try (InputStream input =
                         Files.newInputStream(Path.of(keystorePath))) {
                keyStore.load(input, password);
            }

            KeyManagerFactory keyManagerFactory =
                    KeyManagerFactory.getInstance(
                            KeyManagerFactory.getDefaultAlgorithm());

            keyManagerFactory.init(keyStore, password);

            SSLContext context = SSLContext.getInstance("TLS");
            context.init(
                    keyManagerFactory.getKeyManagers(),
                    null,
                    null);

            return context;
        } finally {
            java.util.Arrays.fill(password, '\0');
        }
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);

        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Missing required environment variable: " + name);
        }

        return value;
    }
}
