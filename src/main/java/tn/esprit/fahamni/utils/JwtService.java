package tn.esprit.fahamni.utils;

import tn.esprit.fahamni.Models.User;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class JwtService {

    public record JwtClaims(
        int userId,
        String email,
        String fullName,
        String role,
        long issuedAtEpochSeconds,
        long expiresAtEpochSeconds
    ) {
    }

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final long TOKEN_LIFETIME_SECONDS = 8 * 60 * 60;
    private static final String SECRET_KEY = System.getenv().getOrDefault(
        "FAHAMNI_JWT_SECRET",
        "fahamni-desktop-demo-secret-change-me"
    );

    private JwtService() {
    }

    public static String generateToken(User user) {
        long issuedAt = Instant.now().getEpochSecond();
        long expiresAt = issuedAt + TOKEN_LIFETIME_SECONDS;

        String header = base64UrlEncode("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
        String payload = base64UrlEncode(buildPayload(user, issuedAt, expiresAt));
        String signature = sign(header + "." + payload);
        return header + "." + payload + "." + signature;
    }

    public static boolean isTokenValid(String token) {
        JwtClaims claims = extractClaims(token);
        return claims != null && claims.expiresAtEpochSeconds() > Instant.now().getEpochSecond();
    }

    public static boolean isTokenValidForUser(String token, User user) {
        JwtClaims claims = extractClaims(token);
        if (claims == null || user == null) {
            return false;
        }

        return claims.userId() == user.getId()
            && claims.email().equalsIgnoreCase(user.getEmail())
            && claims.role().equalsIgnoreCase(user.getRole().name())
            && claims.expiresAtEpochSeconds() > Instant.now().getEpochSecond();
    }

    public static JwtClaims extractClaims(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }

        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            return null;
        }

        String expectedSignature = sign(parts[0] + "." + parts[1]);
        if (!expectedSignature.equals(parts[2])) {
            return null;
        }

        String payloadJson = base64UrlDecode(parts[1]);
        if (payloadJson == null) {
            return null;
        }

        Integer userId = extractInt(payloadJson, "uid");
        Long issuedAt = extractLong(payloadJson, "iat");
        Long expiresAt = extractLong(payloadJson, "exp");
        String email = extractString(payloadJson, "sub");
        String fullName = extractString(payloadJson, "name");
        String role = extractString(payloadJson, "role");

        if (userId == null || issuedAt == null || expiresAt == null || email == null || fullName == null || role == null) {
            return null;
        }

        return new JwtClaims(userId, email, fullName, role, issuedAt, expiresAt);
    }

    private static String buildPayload(User user, long issuedAt, long expiresAt) {
        return "{"
            + "\"sub\":\"" + escape(user.getEmail()) + "\","
            + "\"uid\":" + user.getId() + ","
            + "\"name\":\"" + escape(user.getFullName()) + "\","
            + "\"role\":\"" + escape(user.getRole().name()) + "\","
            + "\"iat\":" + issuedAt + ","
            + "\"exp\":" + expiresAt
            + "}";
    }

    private static String sign(String content) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(SECRET_KEY.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] signatureBytes = mac.doFinal(content.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(signatureBytes);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to sign JWT token.", exception);
        }
    }

    private static String base64UrlEncode(String content) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(content.getBytes(StandardCharsets.UTF_8));
    }

    private static String base64UrlDecode(String encodedContent) {
        try {
            return new String(Base64.getUrlDecoder().decode(encodedContent), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static String extractString(String json, String key) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static Integer extractInt(String json, String key) {
        Long value = extractLong(json, key);
        return value == null ? null : value.intValue();
    }

    private static Long extractLong(String json, String key) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(\\d+)").matcher(json);
        return matcher.find() ? Long.parseLong(matcher.group(1)) : null;
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
