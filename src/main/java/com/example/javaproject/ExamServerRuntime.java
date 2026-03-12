package com.example.javaproject;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ExamServerRuntime {

    private static final AtomicBoolean START_REQUESTED = new AtomicBoolean(false);

    private ExamServerRuntime() {
    }

    public static void ensureRunningForLocalTarget(String targetHost) {
        if (!NetworkUtil.isLocalEndpoint(targetHost)) return;
        if (isPortOpen(targetHost, ExamServer.PORT) || isPortOpen("127.0.0.1", ExamServer.PORT)) return;
        if (!START_REQUESTED.compareAndSet(false, true)) return;

        Thread serverThread = new Thread(() -> {
            try {
                new ExamServer().start();
            } catch (IOException e) {
                START_REQUESTED.set(false);
                System.err.println("[ExamServer] Failed to start embedded server: " + e.getMessage());
            }
        }, "embedded-exam-server");
        serverThread.setDaemon(true);
        serverThread.start();

        // Give server bind a short moment before UI actions that may connect.
        try {
            Thread.sleep(300);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    public static boolean waitForServer(String host, int port, int attempts, int timeoutMs) {
        int safeAttempts = Math.max(1, attempts);
        int safeTimeout = Math.max(100, timeoutMs);
        for (int i = 0; i < safeAttempts; i++) {
            if (isPortOpen(host, port)) return true;
            try {
                Thread.sleep(150);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    private static boolean isPortOpen(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 350);
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }
}
