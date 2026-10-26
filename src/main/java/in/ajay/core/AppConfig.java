package in.ajay.core;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public final class AppConfig {

    private static final Properties PROPERTIES = new Properties();

    static {
        // Load common defaults first.
        load("application.properties", false);

        // Load environment-specific overrides.
        String profile = System.getProperty(
                "app.profile", "test"
        );

        if (!profile.equals("test") && !profile.equals("prod")) {
            throw new IllegalArgumentException(
                    "Unknown app.profile: " + profile
            );
        }

        load("application-" + profile + ".properties", true);
    }

    private AppConfig() {}

    private static void load(String file, boolean required) {
        try (InputStream input =
                     AppConfig.class.getClassLoader()
                             .getResourceAsStream(file)) {

            if (input == null) {
                if (required) {
                    throw new IllegalStateException(
                            "Configuration file not found: " + file
                    );
                }
                return;
            }

            PROPERTIES.load(input);

        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to load " + file, e
            );
        }
    }

    public static String get(String key) {
        String value = PROPERTIES.getProperty(key);

        if (value == null) {
            throw new IllegalArgumentException(
                    "Missing configuration: " + key
            );
        }

        return value;
    }

    public static String get(
            String key, String defaultValue) {
        return PROPERTIES.getProperty(key, defaultValue);
    }

    public static int getInt(String key) {
        return Integer.parseInt(get(key));
    }

    public static void list(){
        PROPERTIES.list(System.out);
    }

    public static boolean getBoolean(String key) {
        return Boolean.parseBoolean(get(key));
    }
}
