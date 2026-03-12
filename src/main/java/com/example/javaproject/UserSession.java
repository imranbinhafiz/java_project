package com.example.javaproject;

/**
 * Singleton that holds the currently logged-in user's session.
 * Used across the app to know which user's files to read/write.
 */
public class UserSession {

    private static UserSession instance;
    private String username;
    private String serverHost = "localhost";

    private UserSession() {
    }

    public static UserSession getInstance() {
        if (instance == null) {
            instance = new UserSession();
        }
        return instance;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getServerHost() {
        return serverHost;
    }

    public void setServerHost(String serverHost) {
        if (serverHost == null || serverHost.trim().isEmpty()) {
            this.serverHost = "localhost";
            return;
        }
        this.serverHost = NetworkUtil.normalizeHostInput(serverHost);
    }

    /** Call on logout to clear the session. */
    public void clear() {
        this.username = null;
        this.serverHost = "localhost";
    }

    public boolean isLoggedIn() {
        return username != null && !username.isEmpty();
    }
}
