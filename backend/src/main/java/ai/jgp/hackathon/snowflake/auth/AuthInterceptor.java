package ai.jgp.hackathon.snowflake.auth;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/** Every /api/** call except /api/auth/** needs a signed-in session; the user is bound for the request. */
@Component
class AuthInterceptor implements HandlerInterceptor {

    private final AuthProperties auth;
    private final SignInCookie cookie;

    AuthInterceptor(AuthProperties auth, SignInCookie cookie) {
        this.auth = auth;
        this.cookie = cookie;
    }

    static String sessionUser(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session == null ? null : (String) session.getAttribute(AuthController.SESSION_USER);
    }

    /** The session's user; after a restart, the signed cookie's user, put back into a new session. */
    static String signedInUser(HttpServletRequest request, SignInCookie cookie) {
        String user = sessionUser(request);
        if (user != null) {
            return user;
        }
        user = cookie.verify(request);
        if (user != null) {
            request.getSession(true).setAttribute(AuthController.SESSION_USER, user);
        }
        return user;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        String user = auth.required() ? signedInUser(request, cookie) : auth.demoUser();
        if (user == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"message\":\"Sign in first\"}");
            return false;
        }
        UserContext.set(user);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
            Exception ex) {
        UserContext.clear();
    }
}
