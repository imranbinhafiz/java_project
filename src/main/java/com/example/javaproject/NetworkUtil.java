package com.example.javaproject;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

public final class NetworkUtil {

    private NetworkUtil() {
    }

    public static String normalizeHostInput(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) {
            return "localhost";
        }

        if (value.startsWith("http://") || value.startsWith("https://")) {
            try {
                URI uri = URI.create(value);
                if (uri.getHost() != null && !uri.getHost().isBlank()) {
                    value = uri.getHost();
                }
            } catch (Exception ignored) {
                // Fall back to best-effort parsing below.
            }
        }

        if (value.contains("/")) {
            value = value.substring(0, value.indexOf('/'));
        }

        // Accept host:port input but keep host only (app uses fixed port).
        if (!value.startsWith("[") && value.chars().filter(ch -> ch == ':').count() == 1) {
            int colonIndex = value.lastIndexOf(':');
            if (colonIndex > 0 && colonIndex < value.length() - 1) {
                String maybePort = value.substring(colonIndex + 1);
                boolean allDigits = maybePort.chars().allMatch(Character::isDigit);
                if (allDigits) {
                    value = value.substring(0, colonIndex);
                }
            }
        }

        return value.isBlank() ? "localhost" : value.trim();
    }

    public static boolean isValidHostValue(String host) {
        if (host == null || host.isBlank()) return false;
        return !host.contains(" ") && !host.contains("\t");
    }

    public static boolean isLocalEndpoint(String host) {
        if (host == null || host.isBlank()) return false;
        String normalized = normalizeHostInput(host);
        if ("localhost".equalsIgnoreCase(normalized)) return true;

        try {
            InetAddress address = InetAddress.getByName(normalized);
            if (address.isAnyLocalAddress() || address.isLoopbackAddress()) return true;
            return NetworkInterface.getByInetAddress(address) != null;
        } catch (Exception e) {
            return false;
        }
    }

    public static String getPreferredLanIpv4() {
        List<String> all = getLocalLanIpv4Addresses();
        return all.isEmpty() ? null : all.get(0);
    }

    public static List<String> getLocalLanIpv4Addresses() {
        List<String> ips = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces == null) return ips;

            for (NetworkInterface ni : Collections.list(interfaces)) {
                if (!isUsableInterface(ni)) continue;
                for (InetAddress addr : Collections.list(ni.getInetAddresses())) {
                    if (addr instanceof Inet4Address
                            && !addr.isLoopbackAddress()
                            && !addr.isLinkLocalAddress()) {
                        ips.add(addr.getHostAddress());
                    }
                }
            }
        } catch (SocketException ignored) {
            return ips;
        }
        return ips;
    }

    private static boolean isUsableInterface(NetworkInterface ni) throws SocketException {
        if (ni == null || !ni.isUp() || ni.isLoopback()) return false;
        String name = ni.getName() == null ? "" : ni.getName().toLowerCase();
        return !name.contains("virtual") && !name.contains("vmware") && !name.contains("vbox");
    }
}
