package com.example.javaproject;

import java.io.*;
import java.net.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ExamServer {

    public static final int PORT = 5000;
    private static final int DEFAULT_SWAP_TIMER_SECONDS = 120;
    private static final int MIN_SWAP_TIMER_SECONDS = 60;
    private static final int MAX_SWAP_TIMER_SECONDS = 600;

    private static final String DATA_DIR   = "data";
    private static final String STATE_FILE = DATA_DIR + "/server_state.json";

    private final java.util.Map<String, java.util.List<String>> dmHistory = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<String, PrintWriter> messengerWriters = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<String, ChatRoom> chatRooms = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicInteger chatRoomCounter = new java.util.concurrent.atomic.AtomicInteger(1);

    private final Map<String, Exam>           exams            = new ConcurrentHashMap<>();
    private final Map<String, Set<String>>    userAssignments  = new ConcurrentHashMap<>();
    private final Map<String, List<ExamResult>> userResults    = new ConcurrentHashMap<>();
    private final Map<String, Set<String>>    userAttempted    = new ConcurrentHashMap<>();
    private final Map<String, List<ExamResult>> examParticipants = new ConcurrentHashMap<>();
    private final AtomicInteger examCounter   = new AtomicInteger(1000);
    private final AtomicInteger resultCounter = new AtomicInteger(1);

    private final Map<String, ChallengeUser> challengeUsers  = new ConcurrentHashMap<>();
    private final Map<String, PrintWriter>   challengeWriters = new ConcurrentHashMap<>();
    private final Map<String, ChallengeRoom> challengeRooms  = new ConcurrentHashMap<>();
    private final AtomicInteger roomCounter = new AtomicInteger(100);

    // Swap Duel round state per room: key = roomId, value = SwapRoundState
    private final Map<String, SwapRoundState> swapRoundStates = new ConcurrentHashMap<>();

    public static void main(String[] args) throws IOException {
        new ExamServer().start();
    }

    public void start() throws IOException {
        ensureDataDir();
        loadState();

        ExecutorService pool = Executors.newCachedThreadPool();
        try (ServerSocket serverSocket = new ServerSocket()) {
            serverSocket.setReuseAddress(true);
            serverSocket.bind(new InetSocketAddress("0.0.0.0", PORT));
            System.out.println("[ExamServer] Listening on port " + PORT);
            List<String> lanIps = NetworkUtil.getLocalLanIpv4Addresses();
            if (!lanIps.isEmpty())
                System.out.println("[ExamServer] LAN IP(s): " + String.join(", ", lanIps));
            else
                System.out.println("[ExamServer] LAN IP(s): not detected");
            System.out.println("[ExamServer] Loaded " + exams.size() + " exam(s) from disk.");
            while (true) {
                Socket client = serverSocket.accept();
                pool.submit(() -> handleClient(client));
            }
        }
    }

    private void handleClient(Socket socket) {
        String remote = socket.getRemoteSocketAddress().toString();
        String ip     = socket.getInetAddress().getHostAddress();
        System.out.println("[ExamServer] Client connected: " + remote);
        final String[] registeredUsername = {null};

        PrintWriter out = null;
        try (
            BufferedReader in  = new BufferedReader(new InputStreamReader(socket.getInputStream(),  "UTF-8"));
            PrintWriter    outRef = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true)
        ) {
            out = outRef;
            String line;
            while ((line = in.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                String command = ExamJsonUtil.parseCommand(line);
                String payload = ExamJsonUtil.parsePayload(line);
                System.out.println("[ExamServer] <- " + command + " (from " + remote + ")");

                switch (command) {
                    case "CREATE_EXAM"           -> handleCreateExam(payload, out);
                    case "GET_ALL_USERS"         -> handleGetAllUsers(out);
                    case "ASSIGN_EXAM"           -> handleAssignExam(payload, out);
                    case "GET_AVAILABLE_EXAMS"   -> handleGetAvailableExams(payload, out);
                    case "GET_PREVIOUS_EXAMS"    -> handleGetPreviousExams(payload, out);
                    case "START_EXAM"            -> handleStartExam(payload, out);
                    case "SUBMIT_EXAM"           -> handleSubmitExam(payload, out);
                    case "GET_PUBLISHED_EXAMS"   -> handleGetPublishedExams(payload, out);
                    case "GET_EXAM_PARTICIPANTS" -> handleGetExamParticipants(payload, out);
                    case "GET_EXAM_QUESTIONS"    -> handleGetExamQuestions(payload, out);
                    case "CH_REGISTER"           -> { registeredUsername[0] = chHandleRegister(payload, ip, out); }
                    case "CH_UNREGISTER"         -> chHandleUnregister(payload, out);
                    case "CH_GET_USERS"          -> chHandleGetUsers(out);
                    case "CH_GET_ROOMS"          -> chHandleGetRooms(out);
                    case "CH_CREATE_ROOM"        -> chHandleCreateRoom(payload, out);
                    case "CH_JOIN_ROOM"          -> chHandleJoinRoom(payload, out);
                    case "CH_LEAVE_ROOM"         -> chHandleLeaveRoom(payload, out);
                    case "CH_SET_READY"          -> chHandleSetReady(payload, out);
                    case "CH_INVITE"             -> chHandleInvite(payload, out);
                    case "CH_ACCEPT_INVITE"      -> chHandleJoinRoom(payload, out);
                    case "CH_DECLINE_INVITE"     -> chHandleDeclineInvite(payload, out);
                    case "CH_SELECT_EXAM"        -> chHandleSelectExam(payload, out);
                    case "CH_START_GAME"         -> chHandleStartGame(payload, out);
                    case "CH_END_GAME"           -> chHandleEndGame(payload, out);
                    case "CH_GET_STATS"          -> chHandleGetStats(payload, out);
                    case "CH_GET_CHALLENGE_EXAMS"-> chHandleGetChallengeExams(payload, out);
                    case "CH_KICK_PLAYER"        -> chHandleKickPlayer(payload, out);
                    case "CH_GET_ROOM_STATE"     -> chHandleGetRoomState(payload, out);
                    // Swap Duel
                    case "CH_SWAP_UPLOAD_QUESTION" -> chHandleSwapUploadQuestion(payload, out);
                    case "CH_SWAP_SUBMIT_ANSWER"  -> chHandleSwapSubmitAnswer(payload, out);
                    case "CH_SWAP_SUBMIT_MARKS"   -> chHandleSwapSubmitMarks(payload, out);
                    case "CH_SWAP_CHAT"           -> chHandleSwapChat(payload, out);
                    case "CH_SWAP_SET_TIMER"      -> chHandleSwapSetTimer(payload, out);
                    // Speed
                    case "CH_SPEED_SUBMIT"        -> chHandleSpeedSubmit(payload, out);
                    case "MSG_REGISTER"          -> msgHandleRegister(payload, out);
                    case "MSG_UNREGISTER"        -> msgHandleUnregister(payload, out);
                    case "MSG_SEND"              -> msgHandleSend(payload, out);
                    case "MSG_HISTORY"           -> msgHandleHistory(payload, out);
                    case "MSG_READ"              -> msgHandleRead(payload, out);
                    case "MSG_DELETE"            -> msgHandleDelete(payload, out);
                    case "MSG_GET_USERS"         -> msgHandleGetUsers(out);
                    case "CHAT_CREATE_ROOM"      -> chatHandleCreateRoom(payload, out);
                    case "CHAT_JOIN_ROOM"        -> chatHandleJoinRoom(payload, out);
                    case "CHAT_LEAVE_ROOM"       -> chatHandleLeaveRoom(payload, out);
                    case "CHAT_MESSAGE"          -> chatHandleMessage(payload, out);
                    case "CHAT_ROOM_LIST"        -> chatHandleRoomList(out);
                    case "CHAT_ROOM_HISTORY"     -> chatHandleRoomHistory(payload, out);
                    case "CHAT_ADD_MEMBER"       -> chatHandleAddMember(payload, out);
                    case "CHAT_REMOVE_MEMBER"    -> chatHandleRemoveMember(payload, out);
                    case "CHAT_DELETE_MESSAGE"   -> chatHandleDeleteMessage(payload, out);
                    case "CHAT_DELETE_ROOM"      -> chatHandleDeleteRoom(payload, out);
                    default -> out.println("ERROR {\"message\":\"Unknown command: " + command + "\"}");
                }
            }
        } catch (IOException e) {
            System.err.println("[ExamServer] Client error (" + remote + "): " + e.getMessage());
        } finally {
            messengerWriters.values().remove(out);
            String uname = registeredUsername[0];
            if (uname != null) {
                ChallengeUser u = challengeUsers.get(uname);
                if (u != null) {
                    if (u.isInRoom() && u.getCurrentRoomId() != null)
                        chRemovePlayerFromRoom(uname, u.getCurrentRoomId());
                    u.setOnline(false);
                    challengeUsers.remove(uname);
                }
                challengeWriters.remove(uname);
                chBroadcastUserUpdate();
            }
            System.out.println("[ExamServer] Client disconnected: " + remote);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // EXAM HANDLERS
    // ═══════════════════════════════════════════════════════════════════

    private void handleCreateExam(String payload, PrintWriter out) {
        try {
            Exam exam = ExamJsonUtil.examFromJson(payload);
            if (exam.getTitle() == null || exam.getTitle().isBlank()) {
                out.println("CREATE_EXAM_FAIL {\"message\":\"Exam title is required\"}");
                return;
            }
            String id = "EXAM-" + examCounter.getAndIncrement();
            exam.setExamId(id);
            exams.put(id, exam);
            String publisher = exam.getPublisherUsername();
            if (publisher != null && !publisher.isBlank())
                userAssignments.computeIfAbsent(publisher, k -> ConcurrentHashMap.newKeySet()).add(id);
            saveState();
            out.println("CREATE_EXAM_SUCCESS {\"examId\":\"" + id + "\"}");
        } catch (Exception e) {
            out.println("CREATE_EXAM_FAIL {\"message\":\"" + e.getMessage() + "\"}");
        }
    }

    private void handleGetAllUsers(PrintWriter out) {
        java.util.Set<String> allUsersSet = new java.util.LinkedHashSet<>(UserFileManager.getAllUsernames());
        allUsersSet.addAll(messengerWriters.keySet());
        allUsersSet.addAll(challengeUsers.keySet());
        List<String> usernames = new java.util.ArrayList<>(allUsersSet);
        StringBuilder sb = new StringBuilder("{\"users\":[");
        for (int i = 0; i < usernames.size(); i++) {
            sb.append("{\"username\":\"").append(usernames.get(i)).append("\"}");
            if (i < usernames.size() - 1) sb.append(",");
        }
        sb.append("]}");
        out.println("USERS_LIST " + sb);
    }

    private void handleAssignExam(String payload, PrintWriter out) {
        try {
            String examId       = ExamJsonUtil.parseString(payload, "examId");
            boolean isPublic    = ExamJsonUtil.parseBool(payload, "isPublic");
            boolean isProtected = ExamJsonUtil.parseBool(payload, "isProtected");
            String password     = ExamJsonUtil.parseString(payload, "password");
            String usersArr     = ExamJsonUtil.extractArray(payload, "assignedUsers");
            List<String> users  = ExamJsonUtil.parseStringArray(usersArr);

            if (!exams.containsKey(examId)) { out.println("ASSIGN_FAIL {\"message\":\"Exam not found\"}"); return; }
            Exam exam = exams.get(examId);
            exam.setPublic(isPublic);
            exam.setProtected(isProtected);
            exam.setPassword(password);
            Set<String> mergedUsers = new LinkedHashSet<>(exam.getAssignedUsers());
            mergedUsers.addAll(users);
            exam.setAssignedUsers(new ArrayList<>(mergedUsers));
            for (String u : users)
                userAssignments.computeIfAbsent(u, k -> ConcurrentHashMap.newKeySet()).add(examId);
            saveState();
            out.println("ASSIGN_SUCCESS {\"examId\":\"" + examId + "\",\"count\":" + users.size() + "}");
        } catch (Exception e) {
            out.println("ASSIGN_FAIL {\"message\":\"" + e.getMessage() + "\"}");
        }
    }

    private void handleGetAvailableExams(String payload, PrintWriter out) {
        String username = ExamJsonUtil.parseString(payload, "username");
        List<Exam> available = new ArrayList<>();
        Set<String> addedExamIds = new HashSet<>();
        for (Exam exam : exams.values()) {
            if (username.equals(exam.getPublisherUsername())) continue;
            if (userAttempted.getOrDefault(username, Collections.emptySet()).contains(exam.getExamId())) continue;
            if (exam.isExpired()) continue;
            boolean assigned = exam.isPublic() || exam.isProtected()
                    || userAssignments.getOrDefault(username, Collections.emptySet()).contains(exam.getExamId())
                    || exam.getAssignedUsers().contains(username);
            if (assigned && addedExamIds.add(exam.getExamId())) available.add(exam);
        }
        StringBuilder sb = new StringBuilder("{\"exams\":[");
        for (int i = 0; i < available.size(); i++) {
            sb.append(ExamJsonUtil.examToJson(stripAnswers(available.get(i))));
            if (i < available.size() - 1) sb.append(",");
        }
        sb.append("]}");
        out.println("EXAMS_LIST " + sb);
    }

    private void handleGetPreviousExams(String payload, PrintWriter out) {
        String username = ExamJsonUtil.parseString(payload, "username");
        List<ExamResult> results = userResults.getOrDefault(username, Collections.emptyList());
        StringBuilder sb = new StringBuilder("{\"results\":[");
        for (int i = 0; i < results.size(); i++) {
            sb.append(ExamJsonUtil.resultToJson(results.get(i)));
            if (i < results.size() - 1) sb.append(",");
        }
        sb.append("]}");
        out.println("PREVIOUS_LIST " + sb);
    }

    private void handleStartExam(String payload, PrintWriter out) {
        String examId   = ExamJsonUtil.parseString(payload, "examId");
        String username = ExamJsonUtil.parseString(payload, "username");
        Exam   exam     = exams.get(examId);
        if (exam == null) { out.println("START_EXAM_FAIL {\"message\":\"Exam not found\"}"); return; }
        if (username.equals(exam.getPublisherUsername())) {
            out.println("START_EXAM_FAIL {\"message\":\"Publishers cannot take their own exam\"}"); return;
        }
        if (exam.getExamType() == Exam.ExamType.REAL_TIME && !exam.isAccessible()) {
            out.println("START_EXAM_FAIL {\"message\":\"This exam is not live yet\"}"); return;
        }
        if (exam.isExpired()) { out.println("START_EXAM_FAIL {\"message\":\"Exam window has expired\"}"); return; }
        if (userAttempted.getOrDefault(username, Collections.emptySet()).contains(examId)) {
            out.println("START_EXAM_FAIL {\"message\":\"Already attempted\"}"); return;
        }
        out.println("EXAM_QUESTIONS " + ExamJsonUtil.examToJson(stripAnswers(exam)));
    }

    private void handleSubmitExam(String payload, PrintWriter out) {
        String examId   = ExamJsonUtil.parseString(payload, "examId");
        String username = ExamJsonUtil.parseString(payload, "username");
        Exam   exam     = exams.get(examId);
        if (exam == null) { out.println("SUBMIT_FAIL {\"message\":\"Exam not found\"}"); return; }
        List<Integer> answers = ExamJsonUtil.answersFromJson(payload);
        ExamResult result = calculateResult(exam, username, answers);
        userResults.computeIfAbsent(username, k -> new CopyOnWriteArrayList<>()).add(result);
        userAttempted.computeIfAbsent(username, k -> ConcurrentHashMap.newKeySet()).add(examId);
        examParticipants.computeIfAbsent(examId, k -> new CopyOnWriteArrayList<>()).add(result);
        saveState();
        out.println("RESULT " + ExamJsonUtil.resultToJson(result));
    }

    private void handleGetPublishedExams(String payload, PrintWriter out) {
        String publisher = ExamJsonUtil.parseString(payload, "username");
        List<Exam> published = new ArrayList<>();
        for (Exam exam : exams.values())
            if (publisher.equals(exam.getPublisherUsername())) published.add(exam);
        published.sort(Comparator.comparing(Exam::getExamId));
        StringBuilder sb = new StringBuilder("{\"exams\":[");
        for (int i = 0; i < published.size(); i++) {
            Exam e = published.get(i);
            int cnt = examParticipants.getOrDefault(e.getExamId(), Collections.emptyList()).size();
            sb.append(ExamJsonUtil.examToJsonWithParticipantCount(e, cnt));
            if (i < published.size() - 1) sb.append(",");
        }
        sb.append("]}");
        out.println("PUBLISHED_LIST " + sb);
    }

    private void handleGetExamParticipants(String payload, PrintWriter out) {
        String examId = ExamJsonUtil.parseString(payload, "examId");
        List<ExamResult> participants = examParticipants.getOrDefault(examId, Collections.emptyList());
        StringBuilder sb = new StringBuilder("{\"participants\":[");
        for (int i = 0; i < participants.size(); i++) {
            sb.append(ExamJsonUtil.resultToJson(participants.get(i)));
            if (i < participants.size() - 1) sb.append(",");
        }
        sb.append("]}");
        out.println("PARTICIPANTS_LIST " + sb);
    }

    private void handleGetExamQuestions(String payload, PrintWriter out) {
        String examId    = ExamJsonUtil.parseString(payload, "examId");
        String publisher = ExamJsonUtil.parseString(payload, "username");
        Exam   exam      = exams.get(examId);
        if (exam == null) { out.println("ERROR {\"message\":\"Exam not found\"}"); return; }
        if (!publisher.equals(exam.getPublisherUsername())) {
            out.println("ERROR {\"message\":\"Access denied\"}"); return;
        }
        out.println("EXAM_QUESTIONS_FULL " + ExamJsonUtil.examToJson(exam));
    }

    // ═══════════════════════════════════════════════════════════════════
    // CHALLENGE MODE HANDLERS
    // ═══════════════════════════════════════════════════════════════════

    private String chHandleRegister(String payload, String ip, PrintWriter out) {
        String username = ExamJsonUtil.parseString(payload, "username");
        if (username == null || username.isBlank()) {
            out.println("CH_REGISTER_FAIL {\"message\":\"Username required\"}");
            return null;
        }
        ChallengeUser user = challengeUsers.computeIfAbsent(username,
                k -> new ChallengeUser(username, ip));
        user.setOnline(true);
        user.setIp(ip);
        challengeWriters.put(username, out);
        out.println("CH_REGISTER_OK " + chUserToJson(user));
        chBroadcastUserUpdate();
        return username;
    }

    private void chHandleUnregister(String payload, PrintWriter out) {
        String username = ExamJsonUtil.parseString(payload, "username");
        if (username != null) {
            ChallengeUser u = challengeUsers.get(username);
            if (u != null) {
                if (u.isInRoom() && u.getCurrentRoomId() != null)
                    chRemovePlayerFromRoom(username, u.getCurrentRoomId());
                u.setOnline(false);
                challengeUsers.remove(username);
                challengeWriters.remove(username);
            }
        }
        out.println("CH_UNREGISTER_OK {}");
        chBroadcastUserUpdate();
    }

    private void chHandleGetUsers(PrintWriter out) {
        out.println("CH_USERS_LIST " + chBuildUsersJson());
    }

    private void chHandleGetRooms(PrintWriter out) {
        out.println("CH_ROOMS_LIST " + chBuildRoomsJson());
    }

    private void chHandleCreateRoom(String payload, PrintWriter out) {
        String username  = ExamJsonUtil.parseString(payload, "username");
        String modeStr   = ExamJsonUtil.parseString(payload, "mode");
        String typeStr   = ExamJsonUtil.parseString(payload, "type");
        String password  = ExamJsonUtil.parseString(payload, "password");
        int maxPlayers   = ExamJsonUtil.parseInt(payload, "maxPlayers");

        if (username == null || username.isBlank()) {
            out.println("CH_CREATE_ROOM_FAIL {\"message\":\"Username required\"}"); return;
        }
        ChallengeUser user = challengeUsers.get(username);
        if (user != null && user.isInRoom()) {
            out.println("CH_CREATE_ROOM_FAIL {\"message\":\"Already in a room\"}"); return;
        }

        ChallengeRoom.GameMode mode;
        try { mode = ChallengeRoom.GameMode.valueOf(modeStr); }
        catch (Exception e) { mode = ChallengeRoom.GameMode.SPEED; }

        ChallengeRoom.RoomType type;
        try { type = ChallengeRoom.RoomType.valueOf(typeStr); }
        catch (Exception e) { type = ChallengeRoom.RoomType.PUBLIC; }

        int totalRounds       = ExamJsonUtil.parseInt(payload, "totalRounds");
        int roundTimerSeconds = normalizeSwapTimerSeconds(ExamJsonUtil.parseInt(payload, "roundTimerSeconds"));
        if (totalRounds <= 0) totalRounds = 3;
        int mp = (maxPlayers <= 0) ? mode.getMaxPlayers() : Math.min(maxPlayers, mode.getMaxPlayers());
        String roomId = "ROOM-" + roomCounter.getAndIncrement();
        ChallengeRoom room = new ChallengeRoom(roomId, username, mode, mp, type);
        room.setTotalRounds(totalRounds);
        room.setRoundTimerSeconds(roundTimerSeconds);
        if (type == ChallengeRoom.RoomType.PRIVATE && password != null && !password.isBlank())
            room.setPassword(password);
        challengeRooms.put(roomId, room);

        if (user != null) { user.setInRoom(true); user.setCurrentRoomId(roomId); }
        out.println("CH_CREATE_ROOM_SUCCESS " + chRoomToJson(room));
        chBroadcastRoomsUpdate();
        chBroadcastUserUpdate();
    }

    private void chHandleJoinRoom(String payload, PrintWriter out) {
        String username = ExamJsonUtil.parseString(payload, "username");
        String roomId   = ExamJsonUtil.parseString(payload, "roomId");
        String password = ExamJsonUtil.parseString(payload, "password");

        ChallengeRoom room = challengeRooms.get(roomId);
        if (room == null)       { out.println("CH_JOIN_ROOM_FAIL {\"message\":\"Room not found\"}"); return; }
        if (room.isFull())      { out.println("CH_JOIN_ROOM_FAIL {\"message\":\"Room is full\"}");   return; }
        if (room.getStatus() != ChallengeRoom.RoomStatus.WAITING) {
            out.println("CH_JOIN_ROOM_FAIL {\"message\":\"Game already started\"}"); return; }
        if (room.getType() == ChallengeRoom.RoomType.PRIVATE) {
            String pw = room.getPassword();
            if (pw != null && !pw.isBlank() && !pw.equals(password)) {
                out.println("CH_JOIN_ROOM_FAIL {\"message\":\"Wrong password\"}"); return;
            }
        }
        if (!room.getPlayers().contains(username)) {
            room.getPlayers().add(username);
            room.getReadyStatus().add(false);
        }
        ChallengeUser user = challengeUsers.get(username);
        if (user != null) { user.setInRoom(true); user.setCurrentRoomId(roomId); }

        out.println("CH_JOIN_ROOM_SUCCESS " + chRoomToJson(room));
        chBroadcastToRoom(roomId, "CH_ROOM_UPDATE " + chRoomToJson(room));
        chBroadcastRoomsUpdate();
        chBroadcastUserUpdate();
    }

    private void chHandleLeaveRoom(String payload, PrintWriter out) {
        String username = ExamJsonUtil.parseString(payload, "username");
        String roomId   = ExamJsonUtil.parseString(payload, "roomId");
        chRemovePlayerFromRoom(username, roomId);
        out.println("CH_LEAVE_ROOM_OK {}");
        chBroadcastRoomsUpdate();
        chBroadcastUserUpdate();
    }

    private void chHandleSetReady(String payload, PrintWriter out) {
        String username = ExamJsonUtil.parseString(payload, "username");
        String roomId   = ExamJsonUtil.parseString(payload, "roomId");
        boolean ready   = ExamJsonUtil.parseBool(payload, "ready");

        ChallengeRoom room = challengeRooms.get(roomId);
        if (room == null) { out.println("ERROR {\"message\":\"Room not found\"}"); return; }
        int idx = room.getPlayers().indexOf(username);
        if (idx >= 0 && idx < room.getReadyStatus().size())
            room.getReadyStatus().set(idx, ready);
        out.println("CH_READY_OK {}");
        chBroadcastToRoom(roomId, "CH_ROOM_UPDATE " + chRoomToJson(room));
    }

    private void chHandleInvite(String payload, PrintWriter out) {
        String from   = ExamJsonUtil.parseString(payload, "from");
        String to     = ExamJsonUtil.parseString(payload, "to");
        String roomId = ExamJsonUtil.parseString(payload, "roomId");

        PrintWriter targetWriter = challengeWriters.get(to);
        if (targetWriter == null) { out.println("CH_INVITE_FAIL {\"message\":\"Player not online\"}"); return; }

        ChallengeRoom room = challengeRooms.get(roomId);
        String modeDisplay = (room != null && room.getMode() != null) ? room.getMode().getDisplayName() : "";
        targetWriter.println("CH_INVITE_RECEIVED {\"from\":\"" + jsonEsc(from)
                + "\",\"roomId\":\"" + jsonEsc(roomId)
                + "\",\"mode\":\"" + jsonEsc(modeDisplay) + "\"}");
        out.println("CH_INVITE_SENT {}");
    }

    private void chHandleDeclineInvite(String payload, PrintWriter out) {
        String from    = ExamJsonUtil.parseString(payload, "username");
        String inviter = ExamJsonUtil.parseString(payload, "inviter");
        PrintWriter inviterWriter = challengeWriters.get(inviter);
        if (inviterWriter != null)
            inviterWriter.println("CH_INVITE_DECLINED {\"from\":\"" + jsonEsc(from) + "\"}");
        out.println("CH_DECLINE_OK {}");
    }

    private void chHandleSelectExam(String payload, PrintWriter out) {
        String roomId       = ExamJsonUtil.parseString(payload, "roomId");
        String examId       = ExamJsonUtil.parseString(payload, "examId");
        String examTitle    = ExamJsonUtil.parseString(payload, "examTitle");
        String examPassword = ExamJsonUtil.parseString(payload, "examPassword");

        ChallengeRoom room = challengeRooms.get(roomId);
        if (room == null) { out.println("CH_SELECT_EXAM_FAIL {\"message\":\"Room not found\"}"); return; }

        Exam exam = exams.get(examId);
        if (exam != null && exam.isProtected()) {
            String correctPw = exam.getPassword() != null ? exam.getPassword() : "";
            if (!correctPw.equals(examPassword != null ? examPassword : "")) {
                out.println("CH_SELECT_EXAM_FAIL {\"message\":\"Incorrect exam password.\"}"); return;
            }
        }

        room.setSelectedExamId(examId);
        room.setSelectedExamTitle(examTitle);
        out.println("CH_SELECT_EXAM_OK {}");
        chBroadcastToRoom(roomId, "CH_ROOM_UPDATE " + chRoomToJson(room));
    }

    private void chHandleStartGame(String payload, PrintWriter out) {
        String roomId   = ExamJsonUtil.parseString(payload, "roomId");
        String username = ExamJsonUtil.parseString(payload, "username");

        ChallengeRoom room = challengeRooms.get(roomId);
        if (room == null)                              { out.println("CH_START_FAIL {\"message\":\"Room not found\"}"); return; }
        if (!username.equals(room.getHost()))          { out.println("CH_START_FAIL {\"message\":\"Only host can start\"}"); return; }
        if (room.getStatus() == ChallengeRoom.RoomStatus.PLAYING) { out.println("CH_START_FAIL {\"message\":\"Game already in progress\"}"); return; }
        if (room.getPlayers().size() < 2)              { out.println("CH_START_FAIL {\"message\":\"Need at least 2 players\"}"); return; }
        if (!room.allReady())                          { out.println("CH_START_FAIL {\"message\":\"All players must be ready\"}"); return; }
        if (room.getMode() == ChallengeRoom.GameMode.SPEED) {
            if (room.getSelectedExamId() == null || room.getSelectedExamId().isBlank()) {
                out.println("CH_START_FAIL {\"message\":\"Please select an exam first\"}"); return;
            }
        }
        room.setStatus(ChallengeRoom.RoomStatus.PLAYING);
        for (String p : room.getPlayers()) {
            ChallengeUser u = challengeUsers.get(p);
            if (u != null) u.setInGame(true);
        }
        room.setCurrentRound(1);
        room.setRoundPhase("QUESTION_CREATION");
        room.setRoundTimerSeconds(DEFAULT_SWAP_TIMER_SECONDS);
        String startPayload = "{\"roomId\":\"" + jsonEsc(roomId)
                + "\",\"mode\":\"" + jsonEsc(room.getMode().name())
                + "\",\"modeDisplay\":\"" + jsonEsc(room.getMode().getDisplayName())
                + "\",\"currentRound\":1"
                + ",\"totalRounds\":" + room.getTotalRounds()
                + ",\"roundTimerSeconds\":" + room.getRoundTimerSeconds()
                + ",\"selectedExamId\":\"" + jsonEsc(room.getSelectedExamId() != null ? room.getSelectedExamId() : "")
                + "\",\"selectedExamTitle\":\"" + jsonEsc(room.getSelectedExamTitle() != null ? room.getSelectedExamTitle() : "") + "\"}";
        out.println("CH_GAME_STARTED " + startPayload);
        chBroadcastToRoom(roomId, "CH_GAME_STARTED " + startPayload);
        chBroadcastRoomsUpdate();
        chBroadcastUserUpdate();
    }

    private void chHandleEndGame(String payload, PrintWriter out) {
        String roomId = ExamJsonUtil.parseString(payload, "roomId");
        cleanupRoom(roomId);
        out.println("CH_GAME_ENDED {}");
        chBroadcastRoomsUpdate();
        chBroadcastUserUpdate();
    }

    private void cleanupRoom(String roomId) {
        ChallengeRoom room = challengeRooms.get(roomId);
        if (room != null) {
            room.setStatus(ChallengeRoom.RoomStatus.FINISHED);
            for (String p : new ArrayList<>(room.getPlayers())) {
                ChallengeUser u = challengeUsers.get(p);
                if (u != null) { u.setInGame(false); u.setInRoom(false); u.setCurrentRoomId(null); }
            }
            challengeRooms.remove(roomId);
        }
        swapRoundStates.remove(roomId);
    }

    private void chHandleGetStats(String payload, PrintWriter out) {
        String username = ExamJsonUtil.parseString(payload, "username");
        ChallengeUser user = challengeUsers.get(username);
        if (user == null) { out.println("CH_STATS {\"wins\":0,\"losses\":0,\"score\":0}"); return; }
        out.println("CH_STATS " + chUserToJson(user));
    }

    // ═══════════════════════════════════════════════════════════════════
    // SWAP DUEL HANDLERS
    // ═══════════════════════════════════════════════════════════════════

    private void chHandleSwapUploadQuestion(String payload, PrintWriter out) {
        String username     = ExamJsonUtil.parseString(payload, "username");
        String roomId       = ExamJsonUtil.parseString(payload, "roomId");
        int    round        = ExamJsonUtil.parseInt(payload, "round");
        String questionText = ExamJsonUtil.parseString(payload, "questionText");
        String imageBase64  = ExamJsonUtil.parseString(payload, "imageBase64");

        ChallengeRoom room = challengeRooms.get(roomId);
        if (room == null) { out.println("CH_SWAP_FAIL {\"message\":\"Room not found\"}"); return; }

        SwapRoundState state = swapRoundStates.computeIfAbsent(roomId + ":" + round, k -> new SwapRoundState());
        if (room.getPlayers().indexOf(username) == 0) {
            state.player1Question = questionText;
            state.player1QuestionImage = imageBase64;
            state.player1QuestionUploaded = true;
        } else {
            state.player2Question = questionText;
            state.player2QuestionImage = imageBase64;
            state.player2QuestionUploaded = true;
        }

        // Notify all players in room
        chBroadcastToRoom(roomId, "CH_SWAP_QUESTION_UPLOADED {\"username\":\"" + jsonEsc(username) + "\"}");
        out.println("CH_SWAP_QUESTION_OK {}");

        // If both uploaded, move to exam phase
        if (state.player1QuestionUploaded && state.player2QuestionUploaded) {
            List<String> players = room.getPlayers();
            if (players.size() >= 2) {
                String p1 = players.get(0);
                String p2 = players.get(1);
                room.setRoundPhase("EXAM");

                // p1 gets p2's question, p2 gets p1's question
                PrintWriter pw1 = challengeWriters.get(p1);
                PrintWriter pw2 = challengeWriters.get(p2);
                if (pw1 != null) {
                    pw1.println("CH_SWAP_PHASE_EXAM {\"questionText\":\"" + jsonEsc(state.player2Question)
                            + "\",\"imageBase64\":\"" + jsonEsc(state.player2QuestionImage) + "\"}");
                }
                if (pw2 != null) {
                    pw2.println("CH_SWAP_PHASE_EXAM {\"questionText\":\"" + jsonEsc(state.player1Question)
                            + "\",\"imageBase64\":\"" + jsonEsc(state.player1QuestionImage) + "\"}");
                }
            }
        }
    }

    private void chHandleSwapSubmitAnswer(String payload, PrintWriter out) {
        String username    = ExamJsonUtil.parseString(payload, "username");
        String roomId      = ExamJsonUtil.parseString(payload, "roomId");
        int    round       = ExamJsonUtil.parseInt(payload, "round");
        String answerText  = ExamJsonUtil.parseString(payload, "answerText");
        String imageBase64 = ExamJsonUtil.parseString(payload, "imageBase64");

        ChallengeRoom room = challengeRooms.get(roomId);
        if (room == null) { out.println("CH_SWAP_FAIL {\"message\":\"Room not found\"}"); return; }

        SwapRoundState state = swapRoundStates.computeIfAbsent(roomId + ":" + round, k -> new SwapRoundState());
        if (room.getPlayers().indexOf(username) == 0) {
            state.player1Answer = answerText;
            state.player1AnswerImage = imageBase64;
            state.player1AnswerSubmitted = true;
        } else {
            state.player2Answer = answerText;
            state.player2AnswerImage = imageBase64;
            state.player2AnswerSubmitted = true;
        }

        // Notify room that someone submitted
        chBroadcastToRoom(roomId, "CH_SWAP_ANSWER_SUBMITTED {\"username\":\"" + jsonEsc(username) + "\"}");
        out.println("CH_SWAP_ANSWER_OK {}");

        // If both answered, move to eval phase
        if (state.player1AnswerSubmitted && state.player2AnswerSubmitted) {
            List<String> players = room.getPlayers();
            if (players.size() >= 2) {
                String p1 = players.get(0);
                String p2 = players.get(1);
                room.setRoundPhase("EVALUATION");

                PrintWriter pw1 = challengeWriters.get(p1);
                PrintWriter pw2 = challengeWriters.get(p2);
                // p1 evaluates p2's answer to p1's question (p1's own question + p2's answer)
                if (pw1 != null) {
                    pw1.println("CH_SWAP_PHASE_EVALUATION {\"questionText\":\"" + jsonEsc(state.player1Question)
                            + "\",\"questionImageBase64\":\"" + jsonEsc(state.player1QuestionImage)
                            + "\",\"answerText\":\"" + jsonEsc(state.player2Answer)
                            + "\",\"answerImageBase64\":\"" + jsonEsc(state.player2AnswerImage) + "\"}");
                }
                // p2 evaluates p1's answer to p2's question (p2's own question + p1's answer)
                if (pw2 != null) {
                    pw2.println("CH_SWAP_PHASE_EVALUATION {\"questionText\":\"" + jsonEsc(state.player2Question)
                            + "\",\"questionImageBase64\":\"" + jsonEsc(state.player2QuestionImage)
                            + "\",\"answerText\":\"" + jsonEsc(state.player1Answer)
                            + "\",\"answerImageBase64\":\"" + jsonEsc(state.player1AnswerImage) + "\"}");
                }
            }
        }
    }

    private void chHandleSwapSubmitMarks(String payload, PrintWriter out) {
        String username = ExamJsonUtil.parseString(payload, "username");
        String roomId   = ExamJsonUtil.parseString(payload, "roomId");
        int    round    = ExamJsonUtil.parseInt(payload, "round");
        int    marks    = ExamJsonUtil.parseInt(payload, "marks");

        ChallengeRoom room = challengeRooms.get(roomId);
        if (room == null) { out.println("CH_SWAP_FAIL {\"message\":\"Room not found\"}"); return; }

        SwapRoundState state = swapRoundStates.computeIfAbsent(roomId + ":" + round, k -> new SwapRoundState());
        List<String> players = room.getPlayers();
        boolean isP1 = players.size() > 0 && players.get(0).equals(username);
        if (isP1) {
            // p1 gives marks TO p2 (for p2's answer to p1's question)
            state.marksGivenByP1 = marks;
            state.p1MarksSubmitted = true;
        } else {
            state.marksGivenByP2 = marks;
            state.p2MarksSubmitted = true;
        }

        chBroadcastToRoom(roomId, "CH_SWAP_MARKS_SUBMITTED {\"username\":\"" + jsonEsc(username) + "\"}");
        out.println("CH_SWAP_MARKS_OK {}");

        // Both submitted marks → resolve round
        if (state.p1MarksSubmitted && state.p2MarksSubmitted) {
            // p1 received marksGivenByP2 (p2 gave marks to p1's answer)
            // p2 received marksGivenByP1 (p1 gave marks to p2's answer)
            state.p1TotalMarks += state.marksGivenByP2; // marks p1 earned
            state.p2TotalMarks += state.marksGivenByP1; // marks p2 earned

            // Accumulate overall totals
            state.p1RoundMarks.add(state.marksGivenByP2);
            state.p2RoundMarks.add(state.marksGivenByP1);

            int nextRound = round + 1;
              if (nextRound <= room.getTotalRounds()) {
                  // Advance round
                  room.setCurrentRound(nextRound);
                  room.setRoundPhase("QUESTION_CREATION");
                  room.setRoundTimerSeconds(DEFAULT_SWAP_TIMER_SECONDS);
                  String roundPayload = "{\"nextRound\":" + nextRound
                          + ",\"totalRounds\":" + room.getTotalRounds()
                          + ",\"roundTimerSeconds\":" + room.getRoundTimerSeconds() + "}";
                  chBroadcastToRoom(roomId, "CH_SWAP_ROUND_DONE " + roundPayload);
              } else {
                // Game over
                finalizeSwapGame(roomId, state, players);
            }
        }
    }

    private void finalizeSwapGame(String roomId, SwapRoundState finalState, List<String> players) {
        String p1 = players.size() > 0 ? players.get(0) : "";
        String p2 = players.size() > 1 ? players.get(1) : "";

        // Compute overall totals across all rounds for each player
        int p1Total = 0, p2Total = 0;
        for (SwapRoundState s : getSwapStatesForRoom(roomId, players)) {
            p1Total += s.p1TotalMarks;
            p2Total += s.p2TotalMarks;
        }

        // Use the passed finalState's accumulated round arrays (they hold round-by-round marks)
        String winner = p1Total > p2Total ? p1 : (p2Total > p1Total ? p2 : "");

        // Update win/loss stats
        ChallengeUser u1 = challengeUsers.get(p1);
        ChallengeUser u2 = challengeUsers.get(p2);
        if (!winner.isEmpty()) {
            if (u1 != null) { if (winner.equals(p1)) u1.setWins(u1.getWins()+1); else u1.setLosses(u1.getLosses()+1); }
            if (u2 != null) { if (winner.equals(p2)) u2.setWins(u2.getWins()+1); else u2.setLosses(u2.getLosses()+1); }
        }
        // Add to history
        String date = java.time.LocalDate.now().toString();
        addChallengeHistory(p1, "SWAP_DUEL", p2, winner.equals(p1) ? "WIN" : (winner.isEmpty() ? "DRAW" : "LOSS"), p1Total, 0, date);
        addChallengeHistory(p2, "SWAP_DUEL", p1, winner.equals(p2) ? "WIN" : (winner.isEmpty() ? "DRAW" : "LOSS"), p2Total, 0, date);

        // Collect all round marks per player BEFORE cleanup
        ChallengeRoom currentRoom = challengeRooms.get(roomId);
        int totalRds = currentRoom != null ? currentRoom.getTotalRounds() : 1;
        // Build result payload
        StringBuilder sb = new StringBuilder("{\"winner\":\"" + jsonEsc(winner) + "\",\"players\":[");
        List<Integer> p1Rounds = new ArrayList<>();
        List<Integer> p2Rounds = new ArrayList<>();
        for (int r = 1; r <= totalRds; r++) {
            SwapRoundState rs = swapRoundStates.get(roomId + ":" + r);
            p1Rounds.add(rs != null ? (rs.p1RoundMarks.isEmpty() ? 0 : rs.p1RoundMarks.get(rs.p1RoundMarks.size()-1)) : 0);
            p2Rounds.add(rs != null ? (rs.p2RoundMarks.isEmpty() ? 0 : rs.p2RoundMarks.get(rs.p2RoundMarks.size()-1)) : 0);
        }
        sb.append("{\"username\":\"" + jsonEsc(p1) + "\",\"totalMarks\":" + p1Total + ",\"roundMarks\":[");
        for (int i = 0; i < p1Rounds.size(); i++) { sb.append(p1Rounds.get(i)); if (i<p1Rounds.size()-1) sb.append(","); }
        sb.append("]},");
        sb.append("{\"username\":\"" + jsonEsc(p2) + "\",\"totalMarks\":" + p2Total + ",\"roundMarks\":[");
        for (int i = 0; i < p2Rounds.size(); i++) { sb.append(p2Rounds.get(i)); if (i<p2Rounds.size()-1) sb.append(","); }
        sb.append("]}");
        sb.append("]}");

        chBroadcastToRoom(roomId, "CH_SWAP_GAME_OVER " + sb);
        cleanupRoom(roomId);
        saveState(); // persist updated wins/losses/history
        chBroadcastRoomsUpdate();
        chBroadcastUserUpdate();
    }

    private List<SwapRoundState> getSwapStatesForRoom(String roomId, List<String> players) {
        List<SwapRoundState> result = new ArrayList<>();
        ChallengeRoom room = challengeRooms.get(roomId);
        int total = room != null ? room.getTotalRounds() : 0;
        for (int r = 1; r <= total; r++) {
            SwapRoundState s = swapRoundStates.get(roomId + ":" + r);
            if (s != null) result.add(s);
        }
        return result;
    }

    private void addChallengeHistory(String username, String mode, String opponent, String result, int score, int rank, String date) {
        ChallengeUser u = challengeUsers.get(username);
        if (u == null) return;
        String existing = u.getChallengeHistory();
        String entry = "{\"mode\":\"" + mode + "\",\"opponent\":\"" + jsonEsc(opponent)
                + "\",\"result\":\"" + result + "\",\"score\":\"" + score
                + "\",\"rank\":\"" + rank + "\",\"date\":\"" + date + "\"}";
        if (existing == null || existing.equals("[]")) {
            u.setChallengeHistory("[" + entry + "]");
        } else {
            // Prepend
            u.setChallengeHistory("[" + entry + "," + existing.substring(1));
        }
    }

    private void chHandleSwapChat(String payload, PrintWriter out) {
        String username = ExamJsonUtil.parseString(payload, "username");
        String roomId   = ExamJsonUtil.parseString(payload, "roomId");
        String text     = ExamJsonUtil.parseString(payload, "text");
        chBroadcastToRoom(roomId, "CH_SWAP_CHAT_MSG {\"username\":\"" + jsonEsc(username)
                + "\",\"text\":\"" + jsonEsc(text) + "\"}");
        out.println("CH_SWAP_CHAT_OK {}");
    }

    private void chHandleSwapSetTimer(String payload, PrintWriter out) {
        String username = ExamJsonUtil.parseString(payload, "username");
        String roomId   = ExamJsonUtil.parseString(payload, "roomId");
        int seconds     = normalizeSwapTimerSeconds(ExamJsonUtil.parseInt(payload, "seconds"));
        ChallengeRoom room = challengeRooms.get(roomId);
        if (room == null) { out.println("CH_SWAP_FAIL {\"message\":\"Room not found\"}"); return; }
        if (!username.equals(room.getHost())) { out.println("CH_SWAP_FAIL {\"message\":\"Only host can set timer\"}"); return; }
        room.setRoundTimerSeconds(seconds);
        chBroadcastToRoom(roomId, "CH_SWAP_TIMER_SET {\"seconds\":" + seconds + "}");
        out.println("CH_SWAP_TIMER_OK {}");
    }

    // ═══════════════════════════════════════════════════════════════════
    // SPEED MODE HANDLERS
    // ═══════════════════════════════════════════════════════════════════

    // Speed room state: track submissions per room
    private final Map<String, Map<String, List<Integer>>> speedSubmissions = new ConcurrentHashMap<>();

    private void chHandleSpeedSubmit(String payload, PrintWriter out) {
        String username = ExamJsonUtil.parseString(payload, "username");
        String roomId   = ExamJsonUtil.parseString(payload, "roomId");
        List<Integer> answers = ExamJsonUtil.answersFromJson(payload);

        ChallengeRoom room = challengeRooms.get(roomId);
        if (room == null) { out.println("CH_SPEED_FAIL {\"message\":\"Room not found\"}"); return; }

        Map<String, List<Integer>> submissions = speedSubmissions.computeIfAbsent(roomId, k -> new ConcurrentHashMap<>());
        submissions.put(username, answers);

        chBroadcastToRoom(roomId, "CH_SPEED_SUBMITTED {\"username\":\"" + jsonEsc(username) + "\"}");
        out.println("CH_SPEED_SUBMIT_OK {}");

        // Check if all players submitted
        if (submissions.size() >= room.getPlayers().size()) {
            finalizeSpeedGame(roomId, room, submissions);
        }
    }

    private void finalizeSpeedGame(String roomId, ChallengeRoom room, Map<String, List<Integer>> submissions) {
        Exam exam = exams.get(room.getSelectedExamId());
        if (exam == null) {
            chBroadcastToRoom(roomId, "CH_SPEED_RESULTS {\"players\":[]}");
            cleanupRoom(roomId);
            return;
        }

        // Calculate scores: for each question, first correct answer gets full marks, others get 0
        // Since we don't have real-time ordering here, we give marks to all who got it right
        // (true speed requires real-time first-correct tracking — simplified: full marks if correct)
        List<Question> questions = exam.getQuestions();
        Map<String, Integer> playerScores = new LinkedHashMap<>();
        for (String p : room.getPlayers()) playerScores.put(p, 0);

        for (int i = 0; i < questions.size(); i++) {
            Question q = questions.get(i);
            final int qi = i;
            // Find first player who got it right (by submission order — concurrent so order not guaranteed, simplified)
            for (String p : room.getPlayers()) {
                List<Integer> ans = submissions.get(p);
                if (ans != null && qi < ans.size() && ans.get(qi) == q.getCorrectOptionIndex()) {
                    playerScores.merge(p, q.getMarks(), Integer::sum);
                    break; // Only first correct gets marks
                }
            }
        }

        // Sort by score descending
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(playerScores.entrySet());
        sorted.sort((a, b) -> b.getValue() - a.getValue());

        // Update stats & history
        String date = java.time.LocalDate.now().toString();
        for (int i = 0; i < sorted.size(); i++) {
            String p     = sorted.get(i).getKey();
            int    score = sorted.get(i).getValue();
            int    rank  = i + 1;
            ChallengeUser u = challengeUsers.get(p);
            if (u != null && score > u.getScore()) u.setScore(score);
            addChallengeHistory(p, "SPEED", "", "", score, rank, date);
        }

        // Build result payload
        StringBuilder sb = new StringBuilder("{\"players\":[");
        for (int i = 0; i < sorted.size(); i++) {
            String p     = sorted.get(i).getKey();
            int    score = sorted.get(i).getValue();
            sb.append("{\"username\":\"" + jsonEsc(p) + "\",\"score\":" + score + ",\"rank\":" + (i+1) + "}");
            if (i < sorted.size()-1) sb.append(",");
        }
        sb.append("]}");

        chBroadcastToRoom(roomId, "CH_SPEED_RESULTS " + sb);
        speedSubmissions.remove(roomId);
        cleanupRoom(roomId);
        saveState(); // persist updated score/history
        chBroadcastRoomsUpdate();
        chBroadcastUserUpdate();
    }

    /** Inner class for swap duel round state */
    private static class SwapRoundState {
        String  player1Question = "";
        String  player1QuestionImage = "";
        boolean player1QuestionUploaded = false;
        String  player2Question = "";
        String  player2QuestionImage = "";
        boolean player2QuestionUploaded = false;

        String  player1Answer = "";
        String  player1AnswerImage = "";
        boolean player1AnswerSubmitted = false;
        String  player2Answer = "";
        String  player2AnswerImage = "";
        boolean player2AnswerSubmitted = false;

        int marksGivenByP1 = 0; // p1 gives marks to p2's answer
        int marksGivenByP2 = 0; // p2 gives marks to p1's answer
        boolean p1MarksSubmitted = false;
        boolean p2MarksSubmitted = false;

        // Accumulated across rounds for final result
        int p1TotalMarks = 0;
        int p2TotalMarks = 0;
        List<Integer> p1RoundMarks = new ArrayList<>();
        List<Integer> p2RoundMarks = new ArrayList<>();
    }

    private void chHandleGetChallengeExams(String payload, PrintWriter out) {
        String roomId = ExamJsonUtil.parseString(payload, "roomId");
        ChallengeRoom room = challengeRooms.get(roomId);
        List<String> roomPlayers = (room != null) ? room.getPlayers() : Collections.emptyList();

        StringBuilder sb = new StringBuilder("{\"exams\":[");
        boolean first = true;
        for (Exam exam : exams.values()) {
            if (roomPlayers.contains(exam.getPublisherUsername())) continue;
            if (exam.isExpired()) continue;
            if (!first) sb.append(",");
            first = false;
            sb.append(ExamJsonUtil.examToJson(stripAnswers(exam)));
        }
        sb.append("]}");
        out.println("CH_EXAMS_LIST " + sb);
    }

    private void chHandleKickPlayer(String payload, PrintWriter out) {
        String roomId = ExamJsonUtil.parseString(payload, "roomId");
        String host   = ExamJsonUtil.parseString(payload, "host");
        String target = ExamJsonUtil.parseString(payload, "target");

        ChallengeRoom room = challengeRooms.get(roomId);
        if (room == null || !host.equals(room.getHost())) {
            out.println("ERROR {\"message\":\"Not authorized\"}"); return;
        }
        chRemovePlayerFromRoom(target, roomId);
        PrintWriter targetWriter = challengeWriters.get(target);
        if (targetWriter != null)
            targetWriter.println("CH_KICKED {\"roomId\":\"" + jsonEsc(roomId) + "\"}");
        out.println("CH_KICK_OK {}");
        chBroadcastRoomsUpdate();
    }

    private void chHandleGetRoomState(String payload, PrintWriter out) {
        String roomId = ExamJsonUtil.parseString(payload, "roomId");
        ChallengeRoom room = challengeRooms.get(roomId);
        if (room == null) { out.println("ERROR {\"message\":\"Room not found\"}"); return; }
        out.println("CH_ROOM_STATE " + chRoomToJson(room));
    }

    private static int normalizeSwapTimerSeconds(int seconds) {
        if (seconds <= 0) return DEFAULT_SWAP_TIMER_SECONDS;
        int minutes = (seconds + 59) / 60;
        int normalized = minutes * 60;
        if (normalized < MIN_SWAP_TIMER_SECONDS) normalized = MIN_SWAP_TIMER_SECONDS;
        if (normalized > MAX_SWAP_TIMER_SECONDS) normalized = MAX_SWAP_TIMER_SECONDS;
        return normalized;
    }

    // ═══════════════════════════════════════════════════════════════════
    // CHALLENGE HELPERS
    // ═══════════════════════════════════════════════════════════════════

    private void chRemovePlayerFromRoom(String username, String roomId) {
        ChallengeRoom room = challengeRooms.get(roomId);
        if (room == null) return;
        int idx = room.getPlayers().indexOf(username);
        if (idx >= 0) {
            room.getPlayers().remove(idx);
            if (idx < room.getReadyStatus().size()) room.getReadyStatus().remove(idx);
        }
        ChallengeUser user = challengeUsers.get(username);
        if (user != null) { user.setInRoom(false); user.setInGame(false); user.setCurrentRoomId(null); }

        boolean wasHost = username.equals(room.getHost());
        if (room.getPlayers().isEmpty()) {
            // Last player left — notify and close room
            chBroadcastToRoom(roomId, "CH_ROOM_CLOSED {\"reason\":\"Room is empty\"}");
            challengeRooms.remove(roomId);
            swapRoundStates.entrySet().removeIf(e -> e.getKey().startsWith(roomId + ":"));
        } else if (wasHost) {
            // Host left — always close the room and notify remaining players
            chBroadcastToRoom(roomId, "CH_ROOM_CLOSED {\"reason\":\"Host left the room\"}");
            // Clean up all remaining players
            for (String p : new ArrayList<>(room.getPlayers())) {
                ChallengeUser pu = challengeUsers.get(p);
                if (pu != null) { pu.setInGame(false); pu.setInRoom(false); pu.setCurrentRoomId(null); }
            }
            challengeRooms.remove(roomId);
            swapRoundStates.entrySet().removeIf(e -> e.getKey().startsWith(roomId + ":"));
        } else {
            // Non-host player left — just update the room
            chBroadcastToRoom(roomId, "CH_ROOM_UPDATE " + chRoomToJson(room));
        }
    }

    private void chBroadcastUserUpdate() {
        String msg = "CH_USERS_LIST " + chBuildUsersJson();
        chBroadcastAll(msg);
    }

    private void chBroadcastRoomsUpdate() {
        String msg = "CH_ROOMS_LIST " + chBuildRoomsJson();
        chBroadcastAll(msg);
    }

    private void chBroadcastToRoom(String roomId, String message) {
        ChallengeRoom room = challengeRooms.get(roomId);
        if (room == null) return;
        for (String player : new ArrayList<>(room.getPlayers())) {
            PrintWriter pw = challengeWriters.get(player);
            if (pw != null) { try { pw.println(message); } catch (Exception ignored) {} }
        }
    }

    private void chBroadcastAll(String message) {
        for (PrintWriter pw : challengeWriters.values()) {
            try { pw.println(message); } catch (Exception ignored) {}
        }
    }

    private String chBuildUsersJson() {
        StringBuilder sb = new StringBuilder("{\"users\":[");
        boolean first = true;
        for (ChallengeUser u : challengeUsers.values()) {
            if (!first) sb.append(",");
            first = false;
            sb.append(chUserToJson(u));
        }
        sb.append("]}");
        return sb.toString();
    }

    private String chBuildRoomsJson() {
        StringBuilder sb = new StringBuilder("{\"rooms\":[");
        boolean first = true;
        for (ChallengeRoom r : challengeRooms.values()) {
            if (r.getStatus() == ChallengeRoom.RoomStatus.WAITING) {
                if (!first) sb.append(",");
                first = false;
                sb.append(chRoomToJson(r));
            }
        }
        sb.append("]}");
        return sb.toString();
    }

    private String chUserToJson(ChallengeUser u) {
        StringBuilder sb = new StringBuilder("{");
        appendStr(sb, "username", u.getUsername()); sb.append(",");
        appendStr(sb, "ip", u.getIp() != null ? u.getIp() : ""); sb.append(",");
        appendBool(sb, "online", u.isOnline()); sb.append(",");
        appendBool(sb, "inRoom", u.isInRoom()); sb.append(",");
        appendBool(sb, "inGame", u.isInGame()); sb.append(",");
        appendInt(sb, "wins", u.getWins()); sb.append(",");
        appendInt(sb, "losses", u.getLosses()); sb.append(",");
        appendInt(sb, "score", u.getScore()); sb.append(",");
        appendStr(sb, "currentRoomId", u.getCurrentRoomId() != null ? u.getCurrentRoomId() : ""); sb.append(",");
        // Challenge history stored as raw JSON array string
        String hist = u.getChallengeHistory();
        if (hist == null || hist.isBlank()) hist = "[]";
        sb.append("\"challengeHistory\":").append(hist);
        sb.append("}");
        return sb.toString();
    }

    private String chRoomToJson(ChallengeRoom r) {
        StringBuilder sb = new StringBuilder("{");
        appendStr(sb, "roomId", r.getRoomId()); sb.append(",");
        appendStr(sb, "host", r.getHost()); sb.append(",");
        appendStr(sb, "mode", r.getMode() != null ? r.getMode().name() : "SPEED"); sb.append(",");
        appendStr(sb, "modeDisplay", r.getMode() != null ? r.getMode().getDisplayName() : ""); sb.append(",");
        appendInt(sb, "maxPlayers", r.getMaxPlayers()); sb.append(",");
        appendStr(sb, "status", r.getStatus() != null ? r.getStatus().name() : "WAITING"); sb.append(",");
        appendStr(sb, "type", r.getType() != null ? r.getType().name() : "PUBLIC"); sb.append(",");
        appendStr(sb, "password", r.getPassword() != null ? r.getPassword() : ""); sb.append(",");
        appendStr(sb, "selectedExamId", r.getSelectedExamId() != null ? r.getSelectedExamId() : ""); sb.append(",");
        appendStr(sb, "selectedExamTitle", r.getSelectedExamTitle() != null ? r.getSelectedExamTitle() : ""); sb.append(",");
        appendInt(sb, "totalRounds", r.getTotalRounds()); sb.append(",");
        appendInt(sb, "currentRound", r.getCurrentRound()); sb.append(",");
        appendInt(sb, "roundTimerSeconds", r.getRoundTimerSeconds()); sb.append(",");
        appendStr(sb, "roundPhase", r.getRoundPhase() != null ? r.getRoundPhase() : "WAITING"); sb.append(",");
        sb.append("\"players\":[");
        List<String> players = r.getPlayers();
        for (int i = 0; i < players.size(); i++) {
            sb.append("\"").append(jsonEsc(players.get(i))).append("\"");
            if (i < players.size() - 1) sb.append(",");
        }
        sb.append("],");
        sb.append("\"readyStatus\":[");
        List<Boolean> ready = r.getReadyStatus();
        for (int i = 0; i < ready.size(); i++) {
            sb.append(ready.get(i));
            if (i < ready.size() - 1) sb.append(",");
        }
        sb.append("]}");
        return sb.toString();
    }

    private void appendStr(StringBuilder sb, String key, String val) {
        sb.append("\"").append(key).append("\":\"").append(jsonEsc(val)).append("\"");
    }
    private void appendInt(StringBuilder sb, String key, int val) {
        sb.append("\"").append(key).append("\":").append(val);
    }
    private void appendBool(StringBuilder sb, String key, boolean val) {
        sb.append("\"").append(key).append("\":").append(val);
    }

    // ═══════════════════════════════════════════════════════════════════
    // RESULT CALCULATION
    // ═══════════════════════════════════════════════════════════════════

    private ExamResult calculateResult(Exam exam, String username, List<Integer> answers) {
        ExamResult r = new ExamResult();
        r.setResultId("RES-" + resultCounter.getAndIncrement());
        r.setExamId(exam.getExamId());
        r.setExamTitle(exam.getTitle());
        r.setPublisherUsername(exam.getPublisherUsername());
        r.setStudentUsername(username);
        r.setTotalMarks(exam.getTotalMarks());
        r.setTotalQuestions(exam.getQuestions().size());
        r.setExamType(exam.getExamType());
        r.setAttemptedAt(LocalDateTime.now());
        int score = 0, correct = 0, wrong = 0;
        List<ExamResult.QuestionResult> breakdown = new ArrayList<>();
        List<Question> questions = exam.getQuestions();
        for (int i = 0; i < questions.size(); i++) {
            Question q        = questions.get(i);
            int      selected = (i < answers.size()) ? answers.get(i) : -1;
            boolean  isCorrect = (selected == q.getCorrectOptionIndex());
            if (selected == -1) { /* unanswered */ }
            else if (isCorrect) { score += q.getMarks(); correct++; }
            else { if (exam.isNegativeMarking()) score = Math.max(0, score - 1); wrong++; }
            String selectedText = (selected >= 0 && selected < q.getOptions().size())
                    ? q.getOptions().get(selected) : "Not answered";
            String correctText  = q.getOptions().get(q.getCorrectOptionIndex());
            breakdown.add(new ExamResult.QuestionResult(
                    q.getQuestionText(), selectedText, correctText, isCorrect && selected != -1));
        }
        r.setScore(score);
        r.setCorrectCount(correct);
        r.setWrongCount(wrong);
        r.setQuestionResults(breakdown);
        return r;
    }

    private Exam stripAnswers(Exam original) {
        Exam copy = new Exam(original.getExamId(), original.getTitle(), original.getDescription(),
                original.getDurationMinutes(), original.getTotalMarks(),
                original.isNegativeMarking(), original.isShuffleOptions(),
                original.getExamType(), original.getPublisherUsername());
        copy.setPublic(original.isPublic());
        copy.setProtected(original.isProtected());
        copy.setPassword(original.getPassword());
        copy.setStartTime(original.getStartTime());
        copy.setEndTime(original.getEndTime());
        copy.setAssignedUsers(original.getAssignedUsers());
        List<Question> stripped = new ArrayList<>();
        for (Question q : original.getQuestions()) {
            Question sq = new Question(q.getQuestionId(), q.getQuestionText(),
                    new ArrayList<>(q.getOptions()), -1, q.getMarks(), q.getImageBase64());
            stripped.add(sq);
        }
        copy.setQuestions(stripped);
        return copy;
    }

    // ═══════════════════════════════════════════════════════════════════
    // PERSISTENCE
    // ═══════════════════════════════════════════════════════════════════

    private void ensureDataDir() {
        File dir = new File(DATA_DIR);
        if (!dir.exists()) dir.mkdirs();
    }

    private synchronized void saveState() {
        try (PrintWriter pw = new PrintWriter(new FileWriter(STATE_FILE))) {
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            sb.append("\"examCounter\":").append(examCounter.get()).append(",");
            sb.append("\"resultCounter\":").append(resultCounter.get()).append(",");
            sb.append("\"chatRoomCounter\":").append(chatRoomCounter.get()).append(",");
            sb.append("\"exams\":[");
            List<Exam> examList = new ArrayList<>(exams.values());
            for (int i = 0; i < examList.size(); i++) {
                sb.append(ExamJsonUtil.examToJson(examList.get(i)));
                if (i < examList.size() - 1) sb.append(",");
            }
            sb.append("],");
            sb.append("\"userAssignments\":[");
            List<Map.Entry<String, Set<String>>> aEntries = new ArrayList<>(userAssignments.entrySet());
            for (int i = 0; i < aEntries.size(); i++) {
                Map.Entry<String, Set<String>> e = aEntries.get(i);
                sb.append("{\"username\":\"").append(jsonEsc(e.getKey())).append("\",\"examIds\":[");
                List<String> ids = new ArrayList<>(e.getValue());
                for (int j = 0; j < ids.size(); j++) {
                    sb.append("\"").append(jsonEsc(ids.get(j))).append("\"");
                    if (j < ids.size() - 1) sb.append(",");
                }
                sb.append("]}");
                if (i < aEntries.size() - 1) sb.append(",");
            }
            sb.append("],");
            sb.append("\"userAttempted\":[");
            List<Map.Entry<String, Set<String>>> attEntries = new ArrayList<>(userAttempted.entrySet());
            for (int i = 0; i < attEntries.size(); i++) {
                Map.Entry<String, Set<String>> e = attEntries.get(i);
                sb.append("{\"username\":\"").append(jsonEsc(e.getKey())).append("\",\"examIds\":[");
                List<String> ids = new ArrayList<>(e.getValue());
                for (int j = 0; j < ids.size(); j++) {
                    sb.append("\"").append(jsonEsc(ids.get(j))).append("\"");
                    if (j < ids.size() - 1) sb.append(",");
                }
                sb.append("]}");
                if (i < attEntries.size() - 1) sb.append(",");
            }
            sb.append("],");
            sb.append("\"userResults\":[");
            List<Map.Entry<String, List<ExamResult>>> rEntries = new ArrayList<>(userResults.entrySet());
            for (int i = 0; i < rEntries.size(); i++) {
                Map.Entry<String, List<ExamResult>> e = rEntries.get(i);
                sb.append("{\"username\":\"").append(jsonEsc(e.getKey())).append("\",\"results\":[");
                List<ExamResult> rs = e.getValue();
                for (int j = 0; j < rs.size(); j++) {
                    sb.append(ExamJsonUtil.resultToJson(rs.get(j)));
                    if (j < rs.size() - 1) sb.append(",");
                }
                sb.append("]}");
                if (i < rEntries.size() - 1) sb.append(",");
            }
            sb.append("],");
            sb.append("\"examParticipants\":[");
            List<Map.Entry<String, List<ExamResult>>> pEntries = new ArrayList<>(examParticipants.entrySet());
            for (int i = 0; i < pEntries.size(); i++) {
                Map.Entry<String, List<ExamResult>> e = pEntries.get(i);
                sb.append("{\"examId\":\"").append(jsonEsc(e.getKey())).append("\",\"results\":[");
                List<ExamResult> rs = e.getValue();
                for (int j = 0; j < rs.size(); j++) {
                    sb.append(ExamJsonUtil.resultToJson(rs.get(j)));
                    if (j < rs.size() - 1) sb.append(",");
                }
                sb.append("]}");
                if (i < pEntries.size() - 1) sb.append(",");
            }
            sb.append("],");
            sb.append("\"dmHistory\":[");
            List<Map.Entry<String, java.util.List<String>>> dmEntries = new ArrayList<>(dmHistory.entrySet());
            for (int i = 0; i < dmEntries.size(); i++) {
                Map.Entry<String, java.util.List<String>> e = dmEntries.get(i);
                sb.append("{\"key\":\"").append(jsonEsc(e.getKey())).append("\",\"messages\":[");
                List<String> messages = e.getValue();
                for (int j = 0; j < messages.size(); j++) {
                    sb.append(messages.get(j));
                    if (j < messages.size() - 1) sb.append(",");
                }
                sb.append("]}");
                if (i < dmEntries.size() - 1) sb.append(",");
            }
            sb.append("],");
            sb.append("\"chatRooms\":[");
            List<ChatRoom> rooms = new ArrayList<>(chatRooms.values());
            for (int i = 0; i < rooms.size(); i++) {
                sb.append(chatRoomToStateJson(rooms.get(i)));
                if (i < rooms.size() - 1) sb.append(",");
            }
            sb.append("],");
            // ── Challenge user stats (wins / losses / best-score / history) ──
            sb.append("\"challengeStats\":[");
            List<ChallengeUser> cStatUsers = new ArrayList<>(challengeUsers.values());
            for (int i = 0; i < cStatUsers.size(); i++) {
                ChallengeUser cu = cStatUsers.get(i);
                StringBuilder su = new StringBuilder("{");
                appendStr(su, "username", cu.getUsername()); su.append(",");
                appendInt(su, "wins",     cu.getWins());     su.append(",");
                appendInt(su, "losses",   cu.getLosses());   su.append(",");
                appendInt(su, "score",    cu.getScore());    su.append(",");
                String hist = cu.getChallengeHistory();
                if (hist == null || hist.isBlank()) hist = "[]";
                su.append("\"challengeHistory\":").append(hist);
                su.append("}");
                sb.append(su);
                if (i < cStatUsers.size() - 1) sb.append(",");
            }
            sb.append("]");
            sb.append("}");
            pw.print(sb);
        } catch (IOException e) {
            System.err.println("[ExamServer] Failed to save state: " + e.getMessage());
        }
    }

    private void loadState() {
        File file = new File(STATE_FILE);
        if (!file.exists()) { System.out.println("[ExamServer] No state file – starting fresh."); return; }
        try {
            StringBuilder content = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = br.readLine()) != null) content.append(line);
            }
            String json = content.toString();
            int savedExamCounter   = ExamJsonUtil.parseInt(json, "examCounter");
            int savedResultCounter = ExamJsonUtil.parseInt(json, "resultCounter");
            int savedChatRoomCounter = ExamJsonUtil.parseInt(json, "chatRoomCounter");
            if (savedExamCounter   > 1000) examCounter.set(savedExamCounter);
            if (savedResultCounter > 1)    resultCounter.set(savedResultCounter);
            if (savedChatRoomCounter > 1) chatRoomCounter.set(savedChatRoomCounter);
            String examsArr = ExamJsonUtil.extractArray(json, "exams");
            for (String examJson : ExamJsonUtil.splitObjectArray(examsArr)) {
                Exam exam = ExamJsonUtil.examFromJson(examJson);
                if (exam.getExamId() != null && !exam.getExamId().isBlank()) exams.put(exam.getExamId(), exam);
            }
            String assignArr = ExamJsonUtil.extractArray(json, "userAssignments");
            for (String entry : ExamJsonUtil.splitObjectArray(assignArr)) {
                String username = ExamJsonUtil.parseString(entry, "username");
                List<String> ids = ExamJsonUtil.parseStringArray(ExamJsonUtil.extractArray(entry, "examIds"));
                userAssignments.computeIfAbsent(username, k -> ConcurrentHashMap.newKeySet()).addAll(ids);
            }
            String attemptArr = ExamJsonUtil.extractArray(json, "userAttempted");
            for (String entry : ExamJsonUtil.splitObjectArray(attemptArr)) {
                String username = ExamJsonUtil.parseString(entry, "username");
                List<String> ids = ExamJsonUtil.parseStringArray(ExamJsonUtil.extractArray(entry, "examIds"));
                userAttempted.computeIfAbsent(username, k -> ConcurrentHashMap.newKeySet()).addAll(ids);
            }
            String userResArr = ExamJsonUtil.extractArray(json, "userResults");
            for (String entry : ExamJsonUtil.splitObjectArray(userResArr)) {
                String username = ExamJsonUtil.parseString(entry, "username");
                for (String resJson : ExamJsonUtil.splitObjectArray(ExamJsonUtil.extractArray(entry, "results")))
                    userResults.computeIfAbsent(username, k -> new CopyOnWriteArrayList<>())
                            .add(ExamJsonUtil.resultFromJson(resJson));
            }
            String examPartArr = ExamJsonUtil.extractArray(json, "examParticipants");
            for (String entry : ExamJsonUtil.splitObjectArray(examPartArr)) {
                String examId = ExamJsonUtil.parseString(entry, "examId");
                for (String resJson : ExamJsonUtil.splitObjectArray(ExamJsonUtil.extractArray(entry, "results")))
                    examParticipants.computeIfAbsent(examId, k -> new CopyOnWriteArrayList<>())
                            .add(ExamJsonUtil.resultFromJson(resJson));
            }
            String dmArr = ExamJsonUtil.extractArray(json, "dmHistory");
            for (String entry : ExamJsonUtil.splitObjectArray(dmArr)) {
                String key = ExamJsonUtil.parseString(entry, "key");
                if (key.isBlank()) continue;
                java.util.List<String> messages = new CopyOnWriteArrayList<>();
                messages.addAll(ExamJsonUtil.splitObjectArray(ExamJsonUtil.extractArray(entry, "messages")));
                dmHistory.put(key, messages);
            }
            String roomArr = ExamJsonUtil.extractArray(json, "chatRooms");
            for (String roomJson : ExamJsonUtil.splitObjectArray(roomArr)) {
                String roomId = ExamJsonUtil.parseString(roomJson, "roomId");
                String roomName = ExamJsonUtil.parseString(roomJson, "roomName");
                String host = ExamJsonUtil.parseString(roomJson, "host");
                if (roomId.isBlank() || roomName.isBlank() || host.isBlank()) continue;
                ChatRoom room = new ChatRoom(roomId, roomName, host);
                room.members.clear();
                room.members.addAll(ExamJsonUtil.parseStringArray(ExamJsonUtil.extractArray(roomJson, "members")));
                if (room.members.isEmpty()) room.members.add(host);
                room.messages.clear();
                room.messages.addAll(ExamJsonUtil.splitObjectArray(ExamJsonUtil.extractArray(roomJson, "messages")));
                chatRooms.put(roomId, room);
            }
            // ── Load challenge user stats ──
            String statsArr = ExamJsonUtil.extractArray(json, "challengeStats");
            for (String entry : ExamJsonUtil.splitObjectArray(statsArr)) {
                String uname = ExamJsonUtil.parseString(entry, "username");
                if (uname == null || uname.isBlank()) continue;
                ChallengeUser cu = challengeUsers.computeIfAbsent(uname, k -> new ChallengeUser(k, ""));
                cu.setWins(ExamJsonUtil.parseInt(entry, "wins"));
                cu.setLosses(ExamJsonUtil.parseInt(entry, "losses"));
                cu.setScore(ExamJsonUtil.parseInt(entry, "score"));
                // history is stored as a raw JSON array inside the entry object
                String histRaw = ExamJsonUtil.extractArray(entry, "challengeHistory");
                if (histRaw != null && !histRaw.isBlank() && !histRaw.equals("null"))
                    cu.setChallengeHistory("[" + histRaw + "]");
                cu.setOnline(false); // not online on boot
            }
            System.out.println("[ExamServer] State loaded: " + exams.size() + " exams, "
                    + userResults.values().stream().mapToInt(List::size).sum() + " results, "
                    + dmHistory.values().stream().mapToInt(List::size).sum() + " direct messages, "
                    + chatRooms.size() + " chat rooms, "
                    + challengeUsers.size() + " challenge user stat(s).");
        } catch (Exception e) {
            System.err.println("[ExamServer] Failed to load state: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private String jsonEsc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "");
    }

    // ═══════════════════════════════════════════════════════════════════
    // MESSENGER HANDLERS
    // ═══════════════════════════════════════════════════════════════════

    private void msgHandleRegister(String payload, PrintWriter out) {
        String username = ExamJsonUtil.parseString(payload, "username");
        if (username == null || username.isBlank()) { out.println("MSG_REGISTER_FAIL {}"); return; }
        messengerWriters.put(username, out);
        out.println("MSG_REGISTER_OK {\"username\":\"" + jsonEsc(username) + "\"}");
        msgBroadcastUserList();

        // Push offline history per conversation so the client can map it correctly
        for (Map.Entry<String, List<String>> entry : dmHistory.entrySet()) {
            String key = entry.getKey();
            if (!key.contains(username + "::") && !key.contains("::" + username)) continue;

            String[] parts = key.split("::", 2);
            String userA = parts.length > 0 ? parts[0] : "";
            String userB = parts.length > 1 ? parts[1] : "";

            List<String> history = new ArrayList<>(entry.getValue());
            if (history.isEmpty()) continue;

            history.sort(Comparator.comparing(msg -> {
                try {
                    return LocalDateTime.parse(ExamJsonUtil.parseString(msg, "time"));
                } catch (Exception e) {
                    return LocalDateTime.MIN;
                }
            }));

            StringBuilder sb = new StringBuilder("{\"userA\":\"" + jsonEsc(userA)
                    + "\",\"userB\":\"" + jsonEsc(userB) + "\",\"messages\":[");
            for (int i = 0; i < history.size(); i++) {
                sb.append(history.get(i));
                if (i < history.size() - 1) sb.append(",");
            }
            sb.append("]}");
            out.println("MSG_HISTORY_DATA " + sb);
        }
    }

    private void msgHandleUnregister(String payload, PrintWriter out) {
        String username = ExamJsonUtil.parseString(payload, "username");
        if (username != null) messengerWriters.remove(username);
        out.println("MSG_UNREGISTER_OK {}");
        msgBroadcastUserList();
    }

    private void msgHandleGetUsers(PrintWriter out) {
        out.println("MSG_USERS_LIST " + msgBuildUsersJson());
    }

    private void msgHandleSend(String payload, PrintWriter out) {
        String from = ExamJsonUtil.parseString(payload, "from");
        String to   = ExamJsonUtil.parseString(payload, "to");
        String text = ExamJsonUtil.parseString(payload, "text");
        if (from == null || to == null || text == null || text.isBlank()) {
            out.println("MSG_SEND_FAIL {}"); return;
        }
        String key = dmKey(from, to);
        String now = java.time.LocalDateTime.now().toString();
        String msgJson = "{\"from\":\"" + jsonEsc(from)
                + "\",\"to\":\""  + jsonEsc(to)
                + "\",\"text\":\"" + jsonEsc(text)
                + "\",\"time\":\"" + now
                + "\",\"read\":false}";
        dmHistory.computeIfAbsent(key, k -> new java.util.concurrent.CopyOnWriteArrayList<>()).add(msgJson);
        PrintWriter recipientWriter = messengerWriters.get(to);
        if (recipientWriter != null) {
            recipientWriter.println("MSG_INCOMING " + msgJson);
        }
        saveState();
        out.println("MSG_SENT " + msgJson);
    }

    private void msgHandleHistory(String payload, PrintWriter out) {
        String userA = ExamJsonUtil.parseString(payload, "userA");
        String userB = ExamJsonUtil.parseString(payload, "userB");
        String key = dmKey(userA, userB);
        java.util.List<String> msgs = dmHistory.getOrDefault(key, java.util.Collections.emptyList());
        StringBuilder sb = new StringBuilder("{\"userA\":\"" + jsonEsc(userA)
                + "\",\"userB\":\"" + jsonEsc(userB) + "\",\"messages\":[");
        for (int i = 0; i < msgs.size(); i++) {
            sb.append(msgs.get(i));
            if (i < msgs.size() - 1) sb.append(",");
        }
        sb.append("]}");
        out.println("MSG_HISTORY_DATA " + sb);
    }

    private void msgHandleDelete(String payload, PrintWriter out) {
        String from    = ExamJsonUtil.parseString(payload, "from");
        String to      = ExamJsonUtil.parseString(payload, "to");
        String msgTime = ExamJsonUtil.parseString(payload, "time");
        String key     = dmKey(from, to);
        
        List<String> msgs = dmHistory.get(key);
        if (msgs != null && !from.isBlank()) {
            msgs.removeIf(m -> 
                ExamJsonUtil.parseString(m, "from").equals(from) &&
                ExamJsonUtil.parseString(m, "time").equals(msgTime));
            
            String deletePush = "MSG_DELETED {\"from\":\"" + jsonEsc(from) 
                    + "\",\"to\":\"" + jsonEsc(to) 
                    + "\",\"time\":\"" + jsonEsc(msgTime) + "\"}";
                    
            PrintWriter recipientWriter = messengerWriters.get(to);
            if (recipientWriter != null) {
                recipientWriter.println(deletePush);
            }
            // also confirm to sender if different
            PrintWriter senderWriter = messengerWriters.get(from);
            if (senderWriter != null && senderWriter != out) {
                senderWriter.println(deletePush);
            }
            saveState();
        }
        out.println("MSG_DELETE_OK {}");
    }

    private void msgHandleRead(String payload, PrintWriter out) {
        String reader  = ExamJsonUtil.parseString(payload, "reader");
        String sender  = ExamJsonUtil.parseString(payload, "sender");
        String key     = dmKey(reader, sender);
        java.util.List<String> msgs = dmHistory.get(key);
        if (msgs != null) {
            for (int i = 0; i < msgs.size(); i++) {
                String m = msgs.get(i);
                if (m.contains("\"from\":\"" + sender + "\"") && m.contains("\"read\":false")) {
                    msgs.set(i, m.replace("\"read\":false", "\"read\":true"));
                }
            }
            saveState();
        }
        out.println("MSG_READ_OK {}");
        PrintWriter senderWriter = messengerWriters.get(sender);
        if (senderWriter != null)
            senderWriter.println("MSG_DELIVERED {\"reader\":\"" + jsonEsc(reader) + "\"}");
    }

    private String dmKey(String a, String b) {
        return (a.compareTo(b) <= 0) ? a + "::" + b : b + "::" + a;
    }

    private String msgBuildUsersJson() {
        java.util.Set<String> allUsersSet = new java.util.LinkedHashSet<>(UserFileManager.getAllUsernames());
        allUsersSet.addAll(messengerWriters.keySet());
        allUsersSet.addAll(challengeUsers.keySet());
        java.util.List<String> allUsers = new java.util.ArrayList<>(allUsersSet);
        StringBuilder sb = new StringBuilder("{\"users\":[");
        boolean first = true;
        for (String u : allUsers) {
            if (!first) sb.append(",");
            first = false;
            boolean online = messengerWriters.containsKey(u) || challengeWriters.containsKey(u);
            sb.append("{\"username\":\"" + jsonEsc(u) + "\",\"online\":" + online + "}");
        }
        sb.append("]}");
        return sb.toString();
    }

    private void msgBroadcastUserList() {
        String msg = "MSG_USERS_LIST " + msgBuildUsersJson();
        for (PrintWriter pw : messengerWriters.values()) {
            try { pw.println(msg); } catch (Exception ignored) {}
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // GROUP CHAT HANDLERS
    // ═══════════════════════════════════════════════════════════════════

    private void chatHandleCreateRoom(String payload, PrintWriter out) {
        String creator  = ExamJsonUtil.parseString(payload, "creator");
        String roomName = ExamJsonUtil.parseString(payload, "roomName");
        if (creator == null || roomName == null || roomName.isBlank()) {
            out.println("CHAT_CREATE_FAIL {\"message\":\"Room name required\"}"); return;
        }
        String roomId = "CROOM-" + chatRoomCounter.getAndIncrement();
        ChatRoom room = new ChatRoom(roomId, roomName, creator);
        chatRooms.put(roomId, room);
        saveState();
        out.println("CHAT_CREATE_OK " + chatRoomToJson(room));
        chatBroadcastRoomList();
    }

    private void chatHandleJoinRoom(String payload, PrintWriter out) {
        String username = ExamJsonUtil.parseString(payload, "username");
        String roomId   = ExamJsonUtil.parseString(payload, "roomId");
        ChatRoom room = chatRooms.get(roomId);
        if (room == null) { out.println("CHAT_JOIN_FAIL {\"message\":\"Room not found\"}"); return; }
        if (!room.members.contains(username)) room.members.add(username);
        out.println("CHAT_JOIN_OK " + chatRoomToJson(room));
        String sysMsg = "{\"from\":\"system\",\"roomId\":\"" + jsonEsc(roomId)
                + "\",\"text\":\"" + jsonEsc(username + " joined the room")
                + "\",\"time\":\"" + java.time.LocalDateTime.now()
                + "\",\"read\":true}";
        room.messages.add(sysMsg);
        saveState();
        chatBroadcastToRoom(roomId, "CHAT_MSG " + sysMsg);
        chatBroadcastRoomList();
    }

    private void chatHandleLeaveRoom(String payload, PrintWriter out) {
        String username = ExamJsonUtil.parseString(payload, "username");
        String roomId   = ExamJsonUtil.parseString(payload, "roomId");
        ChatRoom room = chatRooms.get(roomId);
        if (room != null) {
            room.members.remove(username);
            if (room.members.isEmpty()) {
                chatRooms.remove(roomId);
            } else {
                if (username.equals(room.host) && !room.members.isEmpty())
                    room.host = room.members.get(0);
                String sysMsg = "{\"from\":\"system\",\"roomId\":\"" + jsonEsc(roomId)
                        + "\",\"text\":\"" + jsonEsc(username + " left the room")
                        + "\",\"time\":\"" + java.time.LocalDateTime.now()
                        + "\",\"read\":true}";
                room.messages.add(sysMsg);
                chatBroadcastToRoom(roomId, "CHAT_MSG " + sysMsg);
                chatBroadcastToRoom(roomId, "CHAT_ROOM_UPDATE " + chatRoomToJson(room));
            }
            saveState();
        }
        out.println("CHAT_LEAVE_OK {}");
        chatBroadcastRoomList();
    }

    private void chatHandleMessage(String payload, PrintWriter out) {
        String from   = ExamJsonUtil.parseString(payload, "from");
        String roomId = ExamJsonUtil.parseString(payload, "roomId");
        String text   = ExamJsonUtil.parseString(payload, "text");
        ChatRoom room = chatRooms.get(roomId);
        if (room == null) { out.println("CHAT_MSG_FAIL {\"message\":\"Room not found\"}"); return; }
        String msgJson = "{\"from\":\"" + jsonEsc(from)
                + "\",\"roomId\":\"" + jsonEsc(roomId)
                + "\",\"text\":\"" + jsonEsc(text)
                + "\",\"time\":\"" + java.time.LocalDateTime.now()
                + "\",\"read\":true}";
        room.messages.add(msgJson);
        saveState();
        chatBroadcastToRoom(roomId, "CHAT_MSG " + msgJson);
        out.println("CHAT_MSG_OK {}");
    }

    private void chatHandleRoomList(PrintWriter out) {
        out.println("CHAT_ROOMS_LIST " + chatBuildRoomsJson());
    }

    private void chatHandleRoomHistory(String payload, PrintWriter out) {
        String roomId = ExamJsonUtil.parseString(payload, "roomId");
        ChatRoom room = chatRooms.get(roomId);
        StringBuilder sb = new StringBuilder("{\"roomId\":\"" + jsonEsc(roomId) + "\",\"messages\":[");
        if (room != null) {
            java.util.List<String> msgs = room.messages;
            for (int i = 0; i < msgs.size(); i++) {
                sb.append(msgs.get(i));
                if (i < msgs.size() - 1) sb.append(",");
            }
        }
        sb.append("]}");
        out.println("CHAT_ROOM_HISTORY_DATA " + sb);
    }

    /**
     * FIX: Validates that the user being added actually exists in the system.
     * Also broadcasts a system message and room list update.
     */
    private void chatHandleAddMember(String payload, PrintWriter out) {
        String host     = ExamJsonUtil.parseString(payload, "host");
        String roomId   = ExamJsonUtil.parseString(payload, "roomId");
        String member   = ExamJsonUtil.parseString(payload, "member");
        ChatRoom room = chatRooms.get(roomId);
        if (room == null) { out.println("CHAT_ADD_FAIL {\"message\":\"Room not found\"}"); return; }
        if (!host.equals(room.host)) { out.println("CHAT_ADD_FAIL {\"message\":\"Not authorized\"}"); return; }
        java.util.Set<String> allUsersSet = new java.util.LinkedHashSet<>(UserFileManager.getAllUsernames());
        allUsersSet.addAll(messengerWriters.keySet());
        allUsersSet.addAll(challengeUsers.keySet());
        if (!allUsersSet.contains(member)) { out.println("CHAT_ADD_FAIL {\"message\":\"User not found\"}"); return; }
        if (room.members.contains(member)) { out.println("CHAT_ADD_FAIL {\"message\":\"Already a member\"}"); return; }
        room.members.add(member);
        // Push invite notification to member if online
        PrintWriter mw = messengerWriters.get(member);
        if (mw != null) mw.println("CHAT_INVITED " + chatRoomToJson(room));
        // System message
        String sysMsg = "{\"from\":\"system\",\"roomId\":\"" + jsonEsc(roomId)
                + "\",\"text\":\"" + jsonEsc(member + " was added to the group")
                + "\",\"time\":\"" + java.time.LocalDateTime.now()
                + "\",\"read\":true}";
        room.messages.add(sysMsg);
        chatBroadcastToRoom(roomId, "CHAT_MSG " + sysMsg);
        chatBroadcastToRoom(roomId, "CHAT_ROOM_UPDATE " + chatRoomToJson(room));
        chatBroadcastRoomList();
        saveState();
        out.println("CHAT_ADD_OK {}");
    }

    /**
     * FIX: Notifies removed member BEFORE removing from list so push is received.
     * Also broadcasts system message and full room list update.
     */
    private void chatHandleRemoveMember(String payload, PrintWriter out) {
        String host   = ExamJsonUtil.parseString(payload, "host");
        String roomId = ExamJsonUtil.parseString(payload, "roomId");
        String member = ExamJsonUtil.parseString(payload, "member");
        ChatRoom room = chatRooms.get(roomId);
        if (room == null) { out.println("CHAT_REMOVE_FAIL {\"message\":\"Room not found\"}"); return; }
        if (!host.equals(room.host)) { out.println("CHAT_REMOVE_FAIL {\"message\":\"Not authorized\"}"); return; }
        if (!room.members.contains(member)) { out.println("CHAT_REMOVE_FAIL {\"message\":\"Member not in room\"}"); return; }
        // Push to removed member FIRST (while still in writers map)
        PrintWriter mw = messengerWriters.get(member);
        if (mw != null) mw.println("CHAT_REMOVED {\"roomId\":\"" + jsonEsc(roomId) + "\"}");
        // Now remove and broadcast to remaining
        room.members.remove(member);
        String sysMsg = "{\"from\":\"system\",\"roomId\":\"" + jsonEsc(roomId)
                + "\",\"text\":\"" + jsonEsc(member + " was removed from the group")
                + "\",\"time\":\"" + java.time.LocalDateTime.now()
                + "\",\"read\":true}";
        room.messages.add(sysMsg);
        chatBroadcastToRoom(roomId, "CHAT_MSG " + sysMsg);
        chatBroadcastToRoom(roomId, "CHAT_ROOM_UPDATE " + chatRoomToJson(room));
        chatBroadcastRoomList();
        saveState();
        out.println("CHAT_REMOVE_OK {}");
    }

    /**
     * NEW: Delete a specific message from a group chat room history.
     */
    private void chatHandleDeleteMessage(String payload, PrintWriter out) {
        String roomId  = ExamJsonUtil.parseString(payload, "roomId");
        String from    = ExamJsonUtil.parseString(payload, "from");
        String msgTime = ExamJsonUtil.parseString(payload, "time");
        ChatRoom room  = chatRooms.get(roomId);
        if (room != null && !from.isBlank()) {
            room.messages.removeIf(m ->
                ExamJsonUtil.parseString(m, "from").equals(from) &&
                ExamJsonUtil.parseString(m, "time").equals(msgTime));
            chatBroadcastToRoom(roomId, "CHAT_MSG_DELETED {\"roomId\":\"" + jsonEsc(roomId)
                    + "\",\"from\":\"" + jsonEsc(from)
                    + "\",\"time\":\"" + jsonEsc(msgTime) + "\"}");
            saveState();
        }
        out.println("CHAT_DELETE_OK {}");
    }

    /**
     * Delete an entire group chat room. Only the host can do this.
     * Notifies all members they were removed, then deletes the room.
     */
    private void chatHandleDeleteRoom(String payload, PrintWriter out) {
        String host   = ExamJsonUtil.parseString(payload, "host");
        String roomId = ExamJsonUtil.parseString(payload, "roomId");
        ChatRoom room = chatRooms.get(roomId);
        if (room == null) { out.println("CHAT_DELETE_ROOM_FAIL {\"message\":\"Room not found\"}"); return; }
        if (!host.equals(room.host)) { out.println("CHAT_DELETE_ROOM_FAIL {\"message\":\"Not authorized\"}"); return; }
        // Notify all members the room is being deleted
        for (String member : new java.util.ArrayList<>(room.members)) {
            PrintWriter mw = messengerWriters.get(member);
            if (mw != null) mw.println("CHAT_ROOM_DELETED {\"roomId\":\"" + jsonEsc(roomId) + "\"}");
        }
        chatRooms.remove(roomId);
        saveState();
        chatBroadcastRoomList();
        out.println("CHAT_DELETE_ROOM_OK {}");
    }

    private void chatBroadcastToRoom(String roomId, String msg) {
        ChatRoom room = chatRooms.get(roomId);
        if (room == null) return;
        for (String member : new java.util.ArrayList<>(room.members)) {
            PrintWriter pw = messengerWriters.get(member);
            if (pw != null) { try { pw.println(msg); } catch (Exception ignored) {} }
        }
    }

    private void chatBroadcastRoomList() {
        String msg = "CHAT_ROOMS_LIST " + chatBuildRoomsJson();
        for (PrintWriter pw : messengerWriters.values()) {
            try { pw.println(msg); } catch (Exception ignored) {}
        }
    }

    private String chatBuildRoomsJson() {
        StringBuilder sb = new StringBuilder("{\"rooms\":[");
        boolean first = true;
        for (ChatRoom r : chatRooms.values()) {
            if (!first) sb.append(",");
            first = false;
            sb.append(chatRoomToJson(r));
        }
        sb.append("]}");
        return sb.toString();
    }

    private String chatRoomToJson(ChatRoom r) {
        StringBuilder sb = new StringBuilder("{\"roomId\":\"" + jsonEsc(r.roomId)
                + "\",\"roomName\":\"" + jsonEsc(r.roomName)
                + "\",\"host\":\"" + jsonEsc(r.host)
                + "\",\"members\":[");
        for (int i = 0; i < r.members.size(); i++) {
            sb.append("\"").append(jsonEsc(r.members.get(i))).append("\"");
            if (i < r.members.size() - 1) sb.append(",");
        }
        sb.append("]}");
        return sb.toString();
    }

    private String chatRoomToStateJson(ChatRoom r) {
        StringBuilder sb = new StringBuilder(chatRoomToJson(r));
        sb.setLength(sb.length() - 1);
        sb.append(",\"messages\":[");
        for (int i = 0; i < r.messages.size(); i++) {
            sb.append(r.messages.get(i));
            if (i < r.messages.size() - 1) sb.append(",");
        }
        sb.append("]}");
        return sb.toString();
    }

    private static class ChatRoom {
        String roomId, roomName, host;
        java.util.List<String> members  = new java.util.concurrent.CopyOnWriteArrayList<>();
        java.util.List<String> messages = new java.util.concurrent.CopyOnWriteArrayList<>();

        ChatRoom(String roomId, String roomName, String host) {
            this.roomId   = roomId;
            this.roomName = roomName;
            this.host     = host;
            this.members.add(host);
        }
    }
}
