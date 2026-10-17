package ai.jgp.hackathon.snowflake.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.http.Cookie;

class SignInCookieTest {

    static final AuthProperties AUTH = new AuthProperties("jgp", "odcs", true);
    static final Instant NOW = Instant.parse("2026-10-16T19:00:00Z");

    static SignInCookie at(Instant now, String secret) {
        return new SignInCookie(secret, AUTH, Clock.fixed(now, ZoneOffset.UTC));
    }

    static String issued(SignInCookie c) {
        MockHttpServletResponse response = new MockHttpServletResponse();
        c.issue("jgp", new MockHttpServletRequest(), response);
        Cookie cookie = response.getCookie(SignInCookie.DEFAULT_NAME);
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getMaxAge()).isEqualTo(12 * 3600);
        assertThat(cookie.getAttribute("SameSite")).isEqualTo("Lax");
        return cookie.getValue();
    }

    @Test
    void survivesARestartWithTheSameKey() {
        String value = issued(at(NOW, ""));
        // A new instance stands for the restarted backend.
        assertThat(at(NOW.plusSeconds(60), "").verify(value)).isEqualTo("jgp");
    }

    @Test
    void rejectsExpiredTamperedAndForeign() {
        String value = issued(at(NOW, ""));
        assertThat(at(NOW.plusSeconds(13 * 3600), "").verify(value)).isNull();
        assertThat(at(NOW, "another-secret").verify(value)).isNull();
        String decoded = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
        String forged = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(decoded.replaceFirst("^jgp", "admin").getBytes(StandardCharsets.UTF_8));
        assertThat(at(NOW, "").verify(forged)).isNull();
        assertThat(at(NOW, "").verify("not base64 !!")).isNull();
        assertThat(at(NOW, "").verify(Base64.getUrlEncoder().encodeToString("a|b".getBytes()))).isNull();
    }

    @Test
    void readsOnlyItsOwnCookieAndClears() {
        SignInCookie c = at(NOW, "");
        MockHttpServletRequest request = new MockHttpServletRequest();
        assertThat(c.verify(request)).isNull();
        request.setCookies(new Cookie("other", "x"), new Cookie(SignInCookie.DEFAULT_NAME, issued(c)));
        assertThat(c.verify(request)).isEqualTo("jgp");
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader("X-Forwarded-Proto", "https");
        c.clear(request, response);
        assertThat(response.getCookie(SignInCookie.DEFAULT_NAME).getMaxAge()).isZero();
        assertThat(response.getCookie(SignInCookie.DEFAULT_NAME).getSecure()).isTrue();
    }
}
