package tn.esprit.fahamni.utils;

import org.mindrot.jbcrypt.BCrypt;

public final class PasswordSecurity {

    private PasswordSecurity() {
    }

    public static String hashPassword(String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            return rawPassword;
        }

        if (isBcryptHash(rawPassword)) {
            return rawPassword;
        }

        return BCrypt.hashpw(rawPassword, BCrypt.gensalt());
    }

    public static boolean matches(String rawPassword, String storedPassword) {
        if (rawPassword == null || storedPassword == null || storedPassword.isBlank()) {
            return false;
        }

        if (isBcryptHash(storedPassword)) {
            try {
                return BCrypt.checkpw(rawPassword, storedPassword);
            } catch (IllegalArgumentException ignored) {
                return false;
            }
        }

        return storedPassword.equals(rawPassword);
    }

    private static boolean isBcryptHash(String value) {
        return value.startsWith("$2a$")
            || value.startsWith("$2b$")
            || value.startsWith("$2y$");
    }
}
