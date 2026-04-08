package com.example.javaproject;

import java.io.*;
import java.net.*;
import java.util.List;
import java.util.function.Consumer;

/**
 * Persistent TCP client for Challenge Mode.
 * Connects to ExamServer on port 9090 (same port 5000) and uses CH_ prefixed commands.
 */
public class ChallengeClient {

    private final String host;
    private final int    port;

    private Socket         socket;
    private BufferedReader reader;
    private PrintWriter    writer;
    private Thread         listenerThread;
    private boolean        running = false;

    private Consumer<String> pushListener;

    public ChallengeClient(String host, int port) {
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
                    pushListener.accept("CH_CONNECTION_LOST {}");
            }
        }, "ChallengeClientListener");
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

    // ── Core API ──────────────────────────────────────────────────────

    public void register(String username) {
        send("CH_REGISTER", "{\"username\":\"" + esc(username) + "\"}");
    }

    public void unregister(String username) {
        send("CH_UNREGISTER", "{\"username\":\"" + esc(username) + "\"}");
    }

    public void getUsers() {
        send("CH_GET_USERS", "{}");
    }

    public void getRooms() {
        send("CH_GET_ROOMS", "{}");
    }

    public void createRoom(String username, ChallengeRoom.GameMode mode,
                           ChallengeRoom.RoomType type, String password,
                           int maxPlayers, int totalRounds, int roundTimerSeconds) {
        String payload = "{\"username\":\"" + esc(username) + "\""
                + ",\"mode\":\"" + mode.name() + "\""
                + ",\"type\":\"" + type.name() + "\""
                + ",\"password\":\"" + esc(password != null ? password : "") + "\""
                + ",\"maxPlayers\":" + maxPlayers
                + ",\"totalRounds\":" + totalRounds
                + ",\"roundTimerSeconds\":" + roundTimerSeconds + "}";
        send("CH_CREATE_ROOM", payload);
    }

    public void joinRoom(String username, String roomId, String password) {
        String payload = "{\"username\":\"" + esc(username) + "\""
                + ",\"roomId\":\"" + esc(roomId) + "\""
                + ",\"password\":\"" + esc(password != null ? password : "") + "\"}";
        send("CH_JOIN_ROOM", payload);
    }

    public void leaveRoom(String username, String roomId) {
        send("CH_LEAVE_ROOM", "{\"username\":\"" + esc(username)
                + "\",\"roomId\":\"" + esc(roomId) + "\"}");
    }

    public void setReady(String username, String roomId, boolean ready) {
        send("CH_SET_READY", "{\"username\":\"" + esc(username)
                + "\",\"roomId\":\"" + esc(roomId)
                + "\",\"ready\":" + ready + "}");
    }

    public void invite(String from, String to, String roomId) {
        send("CH_INVITE", "{\"from\":\"" + esc(from)
                + "\",\"to\":\"" + esc(to)
                + "\",\"roomId\":\"" + esc(roomId) + "\"}");
    }

    public void acceptInvite(String username, String roomId) {
        send("CH_ACCEPT_INVITE", "{\"username\":\"" + esc(username)
                + "\",\"roomId\":\"" + esc(roomId) + "\"}");
    }

    public void declineInvite(String username, String inviter, String roomId) {
        send("CH_DECLINE_INVITE", "{\"username\":\"" + esc(username)
                + "\",\"inviter\":\"" + esc(inviter)
                + "\",\"roomId\":\"" + esc(roomId) + "\"}");
    }

    public void selectExam(String roomId, String examId, String examTitle, String examPassword) {
        send("CH_SELECT_EXAM", "{\"roomId\":\"" + esc(roomId)
                + "\",\"examId\":\"" + esc(examId)
                + "\",\"examTitle\":\"" + esc(examTitle)
                + "\",\"examPassword\":\"" + esc(examPassword != null ? examPassword : "") + "\"}");
    }

    public void startGame(String username, String roomId) {
        send("CH_START_GAME", "{\"username\":\"" + esc(username)
                + "\",\"roomId\":\"" + esc(roomId) + "\"}");
    }

    public void getStats(String username) {
        send("CH_GET_STATS", "{\"username\":\"" + esc(username) + "\"}");
    }

    public void getChallengeExams(String roomId) {
        send("CH_GET_CHALLENGE_EXAMS", "{\"roomId\":\"" + esc(roomId) + "\"}");
    }

    public void kickPlayer(String host, String roomId, String target) {
        send("CH_KICK_PLAYER", "{\"host\":\"" + esc(host)
                + "\",\"roomId\":\"" + esc(roomId)
                + "\",\"target\":\"" + esc(target) + "\"}");
    }

    public void getRoomState(String roomId) {
        send("CH_GET_ROOM_STATE", "{\"roomId\":\"" + esc(roomId) + "\"}");
    }

    // ── Swap Duel API ─────────────────────────────────────────────────

    /**
     * Upload question for a swap duel round.
     * questionText: descriptive text of the question.
     * imageBase64: optional image data (or empty string).
     */
    public void swapUploadQuestion(String username, String roomId, int round,
                                   String questionText, String imageBase64) {
        send("CH_SWAP_UPLOAD_QUESTION",
                "{\"username\":\"" + esc(username)
                + "\",\"roomId\":\"" + esc(roomId)
                + "\",\"round\":" + round
                + ",\"questionText\":\"" + esc(questionText)
                + "\",\"imageBase64\":\"" + esc(imageBase64 != null ? imageBase64 : "") + "\"}");
    }

    /**
     * Submit answer for a swap duel round.
     * answerText: descriptive text answer.
     * imageBase64: optional image answer.
     */
    public void swapSubmitAnswer(String username, String roomId, int round,
                                 String answerText, String imageBase64) {
        send("CH_SWAP_SUBMIT_ANSWER",
                "{\"username\":\"" + esc(username)
                + "\",\"roomId\":\"" + esc(roomId)
                + "\",\"round\":" + round
                + ",\"answerText\":\"" + esc(answerText)
                + "\",\"imageBase64\":\"" + esc(imageBase64 != null ? imageBase64 : "") + "\"}");
    }

    /**
     * Submit marks given by evaluator for the opponent's answer.
     * marksGiven: numeric marks the evaluator assigns.
     */
    public void swapSubmitMarks(String username, String roomId, int round, int marksGiven) {
        send("CH_SWAP_SUBMIT_MARKS",
                "{\"username\":\"" + esc(username)
                + "\",\"roomId\":\"" + esc(roomId)
                + "\",\"round\":" + round
                + ",\"marks\":" + marksGiven + "}");
    }

    /**
     * Send a chat message in the swap duel question creation phase.
     */
    public void swapChatMessage(String username, String roomId, String text) {
        send("CH_SWAP_CHAT",
                "{\"username\":\"" + esc(username)
                + "\",\"roomId\":\"" + esc(roomId)
                + "\",\"text\":\"" + esc(text) + "\"}");
    }

    /**
     * Set the exam phase timer (host only, during question creation phase).
     */
    public void swapSetTimer(String username, String roomId, int seconds) {
        send("CH_SWAP_SET_TIMER",
                "{\"username\":\"" + esc(username)
                + "\",\"roomId\":\"" + esc(roomId)
                + "\",\"seconds\":" + seconds + "}");
    }

    /**
     * Submit answers for Speed mode exam.
     */
    public void speedSubmitAnswers(String username, String roomId, java.util.List<Integer> answers) {
        StringBuilder sb = new StringBuilder("{\"username\":\"" + esc(username)
                + "\",\"roomId\":\"" + esc(roomId) + "\",\"answers\":[");
        for (int i = 0; i < answers.size(); i++) {
            sb.append(answers.get(i));
            if (i < answers.size() - 1) sb.append(",");
        }
        sb.append("]}");
        send("CH_SPEED_SUBMIT", sb.toString());
    }

    // ── Internal ──────────────────────────────────────────────────────

    private void send(String command, String payload) {
        if (writer != null && isConnected()) {
            writer.println(command + " " + payload);
        }
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "");
    }

    // ── Parse helpers ─────────────────────────────────────────────────

    public static String parseStr(String json, String key) {
        return ExamJsonUtil.parseString(json, key);
    }

    public static int parseInt(String json, String key) {
        return ExamJsonUtil.parseInt(json, key);
    }

    public static boolean parseBool(String json, String key) {
        return ExamJsonUtil.parseBool(json, key);
    }

    public static List<ChallengeUser> parseUsers(String payload) {
        List<ChallengeUser> list = new java.util.ArrayList<>();
        String arr = ExamJsonUtil.extractArray(payload, "users");
        for (String obj : ExamJsonUtil.splitObjectArray(arr)) {
            ChallengeUser u = new ChallengeUser();
            u.setUsername(ExamJsonUtil.parseString(obj, "username"));
            u.setIp(ExamJsonUtil.parseString(obj, "ip"));
            u.setOnline(ExamJsonUtil.parseBool(obj, "online"));
            u.setInRoom(ExamJsonUtil.parseBool(obj, "inRoom"));
            u.setInGame(ExamJsonUtil.parseBool(obj, "inGame"));
            u.setWins(ExamJsonUtil.parseInt(obj, "wins"));
            u.setLosses(ExamJsonUtil.parseInt(obj, "losses"));
            u.setScore(ExamJsonUtil.parseInt(obj, "score"));
            u.setCurrentRoomId(ExamJsonUtil.parseString(obj, "currentRoomId"));
            u.setChallengeHistory(ExamJsonUtil.parseString(obj, "challengeHistory"));
            list.add(u);
        }
        return list;
    }

    public static List<ChallengeRoom> parseRooms(String payload) {
        List<ChallengeRoom> list = new java.util.ArrayList<>();
        String arr = ExamJsonUtil.extractArray(payload, "rooms");
        for (String obj : ExamJsonUtil.splitObjectArray(arr)) {
            ChallengeRoom r = new ChallengeRoom();
            r.setRoomId(ExamJsonUtil.parseString(obj, "roomId"));
            r.setHost(ExamJsonUtil.parseString(obj, "host"));
            try { r.setMode(ChallengeRoom.GameMode.valueOf(ExamJsonUtil.parseString(obj, "mode"))); }
            catch (Exception ignored) {}
            r.setMaxPlayers(ExamJsonUtil.parseInt(obj, "maxPlayers"));
            try { r.setStatus(ChallengeRoom.RoomStatus.valueOf(ExamJsonUtil.parseString(obj, "status"))); }
            catch (Exception ignored) {}
            try { r.setType(ChallengeRoom.RoomType.valueOf(ExamJsonUtil.parseString(obj, "type"))); }
            catch (Exception ignored) {}
            r.setSelectedExamId(ExamJsonUtil.parseString(obj, "selectedExamId"));
            r.setSelectedExamTitle(ExamJsonUtil.parseString(obj, "selectedExamTitle"));
            r.setTotalRounds(ExamJsonUtil.parseInt(obj, "totalRounds"));
            r.setCurrentRound(ExamJsonUtil.parseInt(obj, "currentRound"));
            r.setRoundTimerSeconds(ExamJsonUtil.parseInt(obj, "roundTimerSeconds"));
            r.setRoundPhase(ExamJsonUtil.parseString(obj, "roundPhase"));
            r.setPlayers(ExamJsonUtil.parseStringArray(ExamJsonUtil.extractArray(obj, "players")));
            String readyArr = ExamJsonUtil.extractArray(obj, "readyStatus");
            java.util.List<Boolean> ready = new java.util.ArrayList<>();
            if (!readyArr.isBlank()) {
                for (String tok : readyArr.split(",")) {
                    tok = tok.trim();
                    if (!tok.isEmpty()) ready.add(Boolean.parseBoolean(tok));
                }
            }
            r.setReadyStatus(ready);
            list.add(r);
        }
        return list;
    }

    public static ChallengeRoom parseRoom(String payload) {
        List<ChallengeRoom> list = parseRooms("{\"rooms\":[" + payload + "]}");
        return list.isEmpty() ? null : list.get(0);
    }
}
