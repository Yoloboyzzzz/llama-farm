package be.ucll.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class LoginRateLimitFilter implements Filter {

    private final Map<String, LoginAttempts> attemptTracker = new ConcurrentHashMap<>();

    private static final int MAX_ATTEMPTS = 5;
    private static final int LOCKOUT_DURATION_MINUTES = 15;
    private static final int ATTEMPT_WINDOW_MINUTES = 5;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        if (!httpRequest.getRequestURI().equals("/api/auth/login") || 
            !httpRequest.getMethod().equals("POST")) {
            chain.doFilter(request, response);
            return;
        }

        String clientIp = getClientIp(httpRequest);
        LoginAttempts attempts = attemptTracker.computeIfAbsent(clientIp, k -> new LoginAttempts());

        if (attempts.isLockedOut()) {
            long minutesLeft = attempts.getMinutesUntilUnlock();
            httpResponse.setStatus(429);
            httpResponse.setContentType("application/json");
            httpResponse.getWriter().write(String.format(
                "{\"error\": \"Too many login attempts. Try again in %d minutes.\", \"retryAfter\": %d}",
                minutesLeft, minutesLeft * 60
            ));
            return;
        }

        attempts.cleanOldAttempts();

        if (attempts.getAttemptCount() >= MAX_ATTEMPTS) {
            attempts.lockOut();
            httpResponse.setStatus(429);
            httpResponse.setContentType("application/json");
            httpResponse.getWriter().write(String.format(
                "{\"error\": \"Too many login attempts. Account locked for %d minutes.\", \"retryAfter\": %d}",
                LOCKOUT_DURATION_MINUTES, LOCKOUT_DURATION_MINUTES * 60
            ));
            System.out.println("🚫 IP " + clientIp + " locked out for " + LOCKOUT_DURATION_MINUTES + " minutes");
            return;
        }

        attempts.recordAttempt();

        chain.doFilter(request, response);
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    private static class LoginAttempts {
        private int count = 0;
        private LocalDateTime firstAttempt = null;
        private LocalDateTime lockoutUntil = null;

        public void recordAttempt() {
            if (firstAttempt == null) {
                firstAttempt = LocalDateTime.now();
            }
            count++;
        }

        public int getAttemptCount() {
            return count;
        }

        public void cleanOldAttempts() {
            if (firstAttempt != null && 
                LocalDateTime.now().isAfter(firstAttempt.plusMinutes(ATTEMPT_WINDOW_MINUTES))) {
                count = 0;
                firstAttempt = null;
            }
        }

        public void lockOut() {
            lockoutUntil = LocalDateTime.now().plusMinutes(LOCKOUT_DURATION_MINUTES);
            count = 0;
            firstAttempt = null;
        }

        public boolean isLockedOut() {
            if (lockoutUntil == null) {
                return false;
            }
            if (LocalDateTime.now().isAfter(lockoutUntil)) {
                lockoutUntil = null;
                return false;
            }
            return true;
        }

        public long getMinutesUntilUnlock() {
            if (lockoutUntil == null) {
                return 0;
            }
            return java.time.Duration.between(LocalDateTime.now(), lockoutUntil).toMinutes() + 1;
        }
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(3600000);
                    LocalDateTime cutoff = LocalDateTime.now().minusHours(1);
                    attemptTracker.entrySet().removeIf(entry -> {
                        LoginAttempts attempts = entry.getValue();
                        return !attempts.isLockedOut() && 
                               (attempts.firstAttempt == null || 
                                attempts.firstAttempt.isBefore(cutoff));
                    });
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }).start();
    }

    @Override
    public void destroy() {
        attemptTracker.clear();
    }
}