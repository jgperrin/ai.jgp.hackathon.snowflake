package ai.jgp.hackathon.snowflake.auth;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Keeps a user signed in across backend restarts. The HTTP session lives in memory, so every
 * deploy used to sign everyone out mid-flow; this signed cookie lets the next request rebuild it.
 * Value: base64url(user|expiresEpochSeconds|hmacSha256(user|expires)).
 */
@Component
public class SignInCookie {

    /** The default cookie name. A second instance on the same host overrides it (DIND#106). */
    static final String DEFAULT_NAME = "cb_signin";
    static final Duration LIFETIME = Duration.ofHours(12);

    private final byte[] key;
    private final Clock clock;
    private final String name;

    @org.springframework.beans.factory.annotation.Autowired
    SignInCookie(@Value("${contextbuilder.auth.secret:}") String secret, AuthProperties auth,
            @Value("${contextbuilder.auth.cookie-name:cb_signin}") String cookieName) {
        this(secret, auth, cookieName, Clock.systemUTC());
    }

    SignInCookie(String secret, AuthProperties auth, Clock clock) {
        this(secret, auth, DEFAULT_NAME, clock);
    }

    /**
     * Without a configured secret the key is derived from the demo credentials, which keeps it
     * stable across restarts. Those credentials are public on the sign-in page anyway, so a
     * forged cookie could only ever grant what anyone can already get by signing in.
     */
    SignInCookie(String secret, AuthProperties auth, String cookieName, Clock clock) {
        String material = secret != null && !secret.isBlank() ? secret
                : "contextbuilder:" + auth.demoUser() + ":" + auth.demoPassword();
        this.key = sha256(material);
        this.clock = clock;
        this.name = cookieName;
    }

    public void issue(String user, HttpServletRequest request, HttpServletResponse response) {
        long expires = clock.instant().plus(LIFETIME).getEpochSecond();
        String payload = user + "|" + expires;
        String value = Base64.getUrlEncoder().withoutPadding()
                .encodeToString((payload + "|" + sign(payload)).getBytes(StandardCharsets.UTF_8));
        response.addCookie(cookie(value, (int) LIFETIME.toSeconds(), request));
    }

    public void clear(HttpServletRequest request, HttpServletResponse response) {
        response.addCookie(cookie("", 0, request));
    }

    /** The user named by a valid, unexpired cookie on this request, or null. */
    public String verify(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie c : cookies) {
            if (name.equals(c.getName())) {
                return verify(c.getValue());
            }
        }
        return null;
    }

    String verify(String value) {
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
            String[] parts = decoded.split("\\|", -1);
            if (parts.length != 3 || parts[0].isEmpty()) {
                return null;
            }
            String payload = parts[0] + "|" + parts[1];
            if (!MessageDigest.isEqual(sign(payload).getBytes(StandardCharsets.UTF_8),
                    parts[2].getBytes(StandardCharsets.UTF_8))) {
                return null;
            }
            return Long.parseLong(parts[1]) > clock.instant().getEpochSecond() ? parts[0] : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private Cookie cookie(String value, int maxAge, HttpServletRequest request) {
        Cookie c = new Cookie(name, value);
        c.setHttpOnly(true);
        c.setPath("/");
        c.setMaxAge(maxAge);
        c.setSecure(request.isSecure() || "https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto")));
        c.setAttribute("SameSite", "Lax");
        return c;
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }

    private static byte[] sha256(String s) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
