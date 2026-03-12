package com.example.javaproject;

import java.io.*;
import java.net.*;
import java.util.function.Consumer;

public class MessengerClient {

    private final String host;
    private final int    port;

    private Socket         socket;
    private BufferedReader reader;
    private PrintWriter    writer;
    private Thread         listenerThread;
    private volatile boolean running = false;

    private Consumer<String> pushListener;

    public MessengerClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void setPushListener(Consumer<String> listener) {
        this.pushListener = listener;
    }

    // ── Connection ────────────────────────────────────────────────────

    public void connect() throws Exception {
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), 5000);
        socket.setSoTimeout(0);
        reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
        writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);
    }

    public void startListening() {
        running = true;
        listenerThread = new Thread(() -> {
            try {
                String line;
                while (running && (line = reader.readLine()) != null) {
                    final String msg = line;
                    if (pushListener != null) pushListener.accept(msg);
                }
            } catch (IOException e) {
                if (running && pushListener != null)
                    pushListener.accept("MSG_CONNECTION_LOST {}");
            }
        }, "MessengerClientListener");
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    public void disconnect() {
        running = false;
        try { if (socket != null && !socket.isClosed()) socket.close(); } catch (IOException ignored) {}
        if (listenerThread != null) listenerThread.interrupt();
    }

    public boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    // ── Messenger API ─────────────────────────────────────────────────

    public void register(String username) {
        send("MSG_REGISTER", "{\"username\":\"" + esc(username) + "\"}");
    }

    public void unregister(String username) {
        send("MSG_UNREGISTER", "{\"username\":\"" + esc(username) + "\"}");
    }

    public void sendDM(String from, String to, String text) {
        String payload = "{\"from\":\"" + esc(from)
                + "\",\"to\":\"" + esc(to)
                + "\",\"text\":\"" + esc(text) + "\"}";
        send("MSG_SEND", payload);
    }

    public void getHistory(String userA, String userB) {
        send("MSG_HISTORY", "{\"userA\":\"" + esc(userA)
                + "\",\"userB\":\"" + esc(userB) + "\"}");
    }

    public void markRead(String reader, String sender) {
        send("MSG_READ", "{\"reader\":\"" + esc(reader)
                + "\",\"sender\":\"" + esc(sender) + "\"}");
    }

    public void getOnlineUsers() {
        send("MSG_GET_USERS", "{}");
    }

    // ── Group Chat API ────────────────────────────────────────────────

    public void createRoom(String creator, String roomName) {
        send("CHAT_CREATE_ROOM", "{\"creator\":\"" + esc(creator)
                + "\",\"roomName\":\"" + esc(roomName) + "\"}");
    }

    public void joinRoom(String username, String roomId) {
        send("CHAT_JOIN_ROOM", "{\"username\":\"" + esc(username)
                + "\",\"roomId\":\"" + esc(roomId) + "\"}");
    }

    public void leaveRoom(String username, String roomId) {
        send("CHAT_LEAVE_ROOM", "{\"username\":\"" + esc(username)
                + "\",\"roomId\":\"" + esc(roomId) + "\"}");
    }

    public void sendRoomMessage(String from, String roomId, String text) {
        String payload = "{\"from\":\"" + esc(from)
                + "\",\"roomId\":\"" + esc(roomId)
                + "\",\"text\":\"" + esc(text) + "\"}";
        send("CHAT_MESSAGE", payload);
    }

    public void getRoomList() {
        send("CHAT_ROOM_LIST", "{}");
    }

    public void getRoomHistory(String roomId) {
        send("CHAT_ROOM_HISTORY", "{\"roomId\":\"" + esc(roomId) + "\"}");
    }

    public void addMember(String host, String roomId, String newMember) {
        send("CHAT_ADD_MEMBER", "{\"host\":\"" + esc(host)
                + "\",\"roomId\":\"" + esc(roomId)
                + "\",\"member\":\"" + esc(newMember) + "\"}");
    }

    public void removeMember(String host, String roomId, String member) {
        send("CHAT_REMOVE_MEMBER", "{\"host\":\"" + esc(host)
                + "\",\"roomId\":\"" + esc(roomId)
                + "\",\"member\":\"" + esc(member) + "\"}");
    }

    /** Delete (permanently remove) an entire group chat room. Only host can do this. */
    public void deleteRoom(String host, String roomId) {
        send("CHAT_DELETE_ROOM", "{\"host\":\"" + esc(host)
                + "\",\"roomId\":\"" + esc(roomId) + "\"}");
    }

    /** Delete a specific message from a group chat (server-side). */
    public void deleteGroupMessage(String roomId, String from, String time) {
        send("CHAT_DELETE_MESSAGE", "{\"roomId\":\"" + esc(roomId)
                + "\",\"from\":\"" + esc(from)
                + "\",\"time\":\"" + esc(time) + "\"}");
    }

    public void deleteDM(String from, String to, String time) {
        send("MSG_DELETE", "{\"from\":\"" + esc(from)
                + "\",\"to\":\"" + esc(to)
                + "\",\"time\":\"" + esc(time) + "\"}");
    }

    // ── Internal ──────────────────────────────────────────────────────

    private synchronized void send(String command, String payload) {
        if (writer != null && isConnected()) {
            writer.println(command + " " + payload);
        }
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "");
    }
}
