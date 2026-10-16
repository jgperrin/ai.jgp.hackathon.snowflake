package ai.jgp.hackathon.snowflake.auth;

import java.util.function.Supplier;

/**
 * The signed-in user for the current request. Set by {@link AuthInterceptor}; worker threads
 * (Cortex and DQ fan-out) get it through {@link #wrap(Supplier)}, never implicitly.
 */
public final class UserContext {

    private static final ThreadLocal<String> USER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(String username) {
        USER.set(username);
    }

    public static void clear() {
        USER.remove();
    }

    /** The current user, or null outside a signed-in request. */
    public static String current() {
        return USER.get();
    }

    /** Captures the caller's user so a task submitted to another thread runs as that user. */
    public static <T> Supplier<T> wrap(Supplier<T> task) {
        String user = USER.get();
        return () -> {
            String previous = USER.get();
            USER.set(user);
            try {
                return task.get();
            } finally {
                if (previous == null) {
                    USER.remove();
                } else {
                    USER.set(previous);
                }
            }
        };
    }
}
