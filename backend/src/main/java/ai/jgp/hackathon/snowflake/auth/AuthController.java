package ai.jgp.hackathon.snowflake.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

@RestController
@RequestMapping("/api/auth")
class AuthController {

    static final String SESSION_USER = "contextbuilder.user";

    private final AuthProperties auth;

    AuthController(AuthProperties auth) {
        this.auth = auth;
    }

    record Credentials(String username, String password) {
    }

    record Me(String username) {
    }

    /** Public on purpose: the sign-in page prints these for the judges. */
    @GetMapping("/demo")
    ResponseEntity<Credentials> demo() {
        if (auth.demoUser() == null || auth.demoUser().isBlank()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(new Credentials(auth.demoUser(), auth.demoPassword()));
    }

    @PostMapping("/login")
    ResponseEntity<?> login(@RequestBody Credentials credentials, HttpServletRequest request) {
        if (credentials == null || !matches(credentials.username(), auth.demoUser())
                || !matches(credentials.password(), auth.demoPassword())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Wrong username or password"));
        }
        HttpSession old = request.getSession(false);
        if (old != null) {
            old.invalidate();
        }
        // A fresh session id on sign-in, so a session id seen before sign-in is worthless after it.
        request.getSession(true).setAttribute(SESSION_USER, auth.demoUser());
        return ResponseEntity.ok(new Me(auth.demoUser()));
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    ResponseEntity<?> me(HttpServletRequest request) {
        String user = AuthInterceptor.sessionUser(request);
        return user == null
                ? ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Sign in first"))
                : ResponseEntity.ok(new Me(user));
    }

    /** Constant-time, so response timing does not reveal how much of a guess was right. */
    static boolean matches(String given, String expected) {
        if (given == null || expected == null) {
            return false;
        }
        return MessageDigest.isEqual(given.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8));
    }
}
