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

    AuthInterceptor(AuthProperties auth) {
        this.auth = auth;
    }

    static String sessionUser(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session == null ? null : (String) session.getAttribute(AuthController.SESSION_USER);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        String user = auth.required() ? sessionUser(request) : auth.demoUser();
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
