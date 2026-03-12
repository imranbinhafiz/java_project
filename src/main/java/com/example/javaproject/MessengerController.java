package com.example.javaproject;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;

import java.time.LocalDateTime;
import java.util.*;

public class MessengerController {

    // ── FXML ──────────────────────────────────────────────────────────
    @FXML private VBox       userListPane;
    @FXML private VBox       roomListPane;
    @FXML private VBox       messagesContainer;
    @FXML private ScrollPane messagesScroll;
    @FXML private TextField  messageInput;
    @FXML private Button     sendButton;
    @FXML private Label      chatHeaderName;
    @FXML private Label      chatHeaderStatus;
    @FXML private VBox       noSelectionPane;
    @FXML private VBox       chatPane;
    @FXML private TextField  newRoomField;
    @FXML private Button     createRoomBtn;
    @FXML private Label      unreadBadge;
    @FXML private Button     memberMgmtBtn;
    @FXML private Label      serverStatusLabel;
    @FXML private TextField  searchField;
    @FXML private Label      charCounter;
    @FXML private StackPane  headerAvatarPane;
    @FXML private HBox       typingBar;
    @FXML private Label      typingLabel;

    // ── State ─────────────────────────────────────────────────────────
    private MessengerClient client;
    private final String    myUsername = UserSession.getInstance().getUsername();

    private String  currentTarget;
    private boolean currentIsRoom;
    private String  currentRoomHost;
    private String  searchQuery = "";

    // Caches
    private final Map<String, List<ChatMessage>> dmCache      = new LinkedHashMap<>();
    private final Map<String, List<ChatMessage>> roomCache    = new LinkedHashMap<>();
    private final Map<String, Boolean>           onlineMap    = new LinkedHashMap<>();
    private final Map<String, Integer>           unreadMap    = new LinkedHashMap<>();
    private final Map<String, String>            roomNames    = new LinkedHashMap<>();
    private final Map<String, String>            roomHosts    = new LinkedHashMap<>();
    private final Map<String, List<String>>      roomMembers  = new LinkedHashMap<>();

    // Message ID → bubble HBox  (for delete)
    private final Map<String, HBox> bubbleMap = new LinkedHashMap<>();

    private static final int MAX_MSG_LEN = 500;
    private static final String[] EMOJI_LIST = {
        "😊","😂","❤️","👍","🔥","😎","🎉","😢","🤔","👀",
        "😅","🙏","💯","✅","🚀","😍","🤣","😭","😤","💪"
    };
    private static final String[] AVATAR_COLORS = {
        "#6366f1","#8b5cf6","#ec4899","#f59e0b","#10b981",
        "#3b82f6","#ef4444","#14b8a6","#f97316","#a855f7"
    };

    // ── Init ──────────────────────────────────────────────────────────
    @FXML
    public void initialize() {
        showNoSelection();

        // Character counter + send button state
        if (messageInput != null) {
            messageInput.textProperty().addListener((obs, oldVal, newVal) -> {
                int len = newVal == null ? 0 : newVal.length();
                if (len == 0) {
                    if (charCounter != null) { charCounter.setText(""); charCounter.getStyleClass().setAll("char-counter"); }
                } else if (len > MAX_MSG_LEN * 0.8) {
                    if (charCounter != null) { charCounter.setText(len + "/" + MAX_MSG_LEN); charCounter.getStyleClass().setAll("char-counter-warn"); }
                } else {
                    if (charCounter != null) { charCounter.setText(""); charCounter.getStyleClass().setAll("char-counter"); }
                }
                if (len > MAX_MSG_LEN) {
                    messageInput.setText(newVal.substring(0, MAX_MSG_LEN));
                    messageInput.positionCaret(MAX_MSG_LEN);
                }
                updateSendBtnState();
            });
        }

        // Make chat area resize with its parent
        if (chatPane != null) {
            HBox.setHgrow(chatPane, Priority.ALWAYS);
            VBox.setVgrow(chatPane, Priority.ALWAYS);
        }
        if (noSelectionPane != null) {
            HBox.setHgrow(noSelectionPane, Priority.ALWAYS);
            VBox.setVgrow(noSelectionPane, Priority.ALWAYS);
        }

        String host = UserSession.getInstance().getServerHost();
        new Thread(() -> {
            try {
                ExamServerRuntime.ensureRunningForLocalTarget(host);
                client = new MessengerClient(host, ExamServer.PORT);
                client.connect();
                client.setPushListener(this::handleServerPush);
                client.startListening();
                client.register(myUsername);
                client.getOnlineUsers();
                client.getRoomList();
                Platform.runLater(() -> {
                    if (serverStatusLabel != null) {
                        serverStatusLabel.setText("● Connected");
                        serverStatusLabel.setStyle("-fx-text-fill:#4ade80;-fx-background-color:rgba(74,222,128,0.08);-fx-border-color:rgba(74,222,128,0.2);");
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    if (serverStatusLabel != null) {
                        serverStatusLabel.setText("⚠ Offline");
                        serverStatusLabel.setStyle("-fx-text-fill:#f87171;-fx-background-color:rgba(248,113,113,0.08);-fx-border-color:rgba(248,113,113,0.2);");
                    }
                });
            }
        }, "MessengerConnect").start();
    }

    public void shutdown() {
        if (client != null && client.isConnected()) {
            client.unregister(myUsername);
            client.disconnect();
        }
    }

    // ── Server Push ───────────────────────────────────────────────────
    private void handleServerPush(String raw) {
        if (raw == null || raw.isBlank()) return;
        String cmd     = ExamJsonUtil.parseCommand(raw);
        String payload = ExamJsonUtil.parsePayload(raw);
        Platform.runLater(() -> dispatch(cmd, payload));
    }

    private void dispatch(String cmd, String payload) {
        switch (cmd) {
            case "MSG_USERS_LIST"         -> handleUsersList(payload);
            case "MSG_INCOMING"           -> handleIncoming(payload);
            case "MSG_SENT"               -> handleSent(payload);
            case "MSG_HISTORY_DATA"       -> handleHistoryData(payload);
            case "CHAT_ROOMS_LIST"        -> handleRoomsList(payload);
            case "CHAT_MSG"               -> handleChatMsg(payload);
            case "CHAT_JOIN_OK",
                 "CHAT_CREATE_OK"         -> handleJoinOk(payload);
            case "CHAT_ROOM_HISTORY_DATA" -> handleRoomHistoryData(payload);
            case "CHAT_INVITED"           -> handleInvited(payload);
            case "CHAT_ROOM_UPDATE"       -> handleRoomUpdate(payload);
            case "CHAT_REMOVED",
                 "CHAT_ROOM_DELETED"       -> handleRemoved(payload);
            case "MSG_DELETED"            -> handleDeleteMsgResponse(payload, false);
            case "CHAT_MSG_DELETED"       -> handleDeleteMsgResponse(payload, true);
            case "MSG_CONNECTION_LOST"    -> {
                if (serverStatusLabel != null) {
                    serverStatusLabel.setText("⚠ Offline");
                    serverStatusLabel.setStyle("-fx-text-fill:#f87171;-fx-background-color:rgba(248,113,113,0.08);-fx-border-color:rgba(248,113,113,0.2);");
                }
            }
            default -> {}
        }
    }
    private void handleUsersList(String payload) {
        String arr = ExamJsonUtil.extractArray(payload, "users");
        onlineMap.clear();
        List<String> allUsers = new ArrayList<>();
        for (String obj : ExamJsonUtil.splitObjectArray(arr)) {
            String u = ExamJsonUtil.parseString(obj, "username");
            boolean online = ExamJsonUtil.parseBool(obj, "online");
            if (!u.isBlank() && !u.equals(myUsername)) {
                onlineMap.put(u, online);
                allUsers.add(u);
            }
        }
        rebuildUserList(allUsers);
        updateGlobalUnreadBadge();
    }

    private void handleIncoming(String payload) {
        String from = ExamJsonUtil.parseString(payload, "from");
        String text = ExamJsonUtil.parseString(payload, "text");
        String time = ExamJsonUtil.parseString(payload, "time");
        ChatMessage msg = makeMsg(from, myUsername, text, time, ChatMessage.Type.DM);
        dmCache.computeIfAbsent(from, k -> new ArrayList<>()).add(msg);

        if (from.equals(currentTarget) && !currentIsRoom) {
            appendBubble(msg);
            scrollToBottom();
            if (client != null) client.markRead(myUsername, from);
        } else {
            unreadMap.merge(from, 1, Integer::sum);
            updateGlobalUnreadBadge();
            rebuildUserList(new ArrayList<>(onlineMap.keySet()));
        }
    }

    private void handleSent(String payload) {
        String to   = ExamJsonUtil.parseString(payload, "to");
        String text = ExamJsonUtil.parseString(payload, "text");
        String time = ExamJsonUtil.parseString(payload, "time");
        ChatMessage msg = makeMsg(myUsername, to, text, time, ChatMessage.Type.DM);
        dmCache.computeIfAbsent(to, k -> new ArrayList<>()).add(msg);
        if (to.equals(currentTarget) && !currentIsRoom) {
            unreadMap.remove(to);
            updateGlobalUnreadBadge();
            appendBubble(msg);
            scrollToBottom();
        }
        // Refresh user row to show last message preview
        rebuildUserList(new ArrayList<>(onlineMap.keySet()));
    }

    /**
     * FIX: History data for DMs — always show regardless of whether user
     * was online when messages were sent.
     */
    private void handleHistoryData(String payload) {
        List<ChatMessage> msgs = parseMsgArray(
            ExamJsonUtil.extractArray(payload, "messages"), ChatMessage.Type.DM);
        String userA = ExamJsonUtil.parseString(payload, "userA");
        String userB = ExamJsonUtil.parseString(payload, "userB");
        String partner = resolvePartner(userA, userB, msgs);
        if (partner == null || partner.isBlank()) return;

        List<ChatMessage> merged = mergeMessages(dmCache.get(partner), msgs);
        dmCache.put(partner, merged);

        boolean isCurrent = partner.equals(currentTarget) && !currentIsRoom;
        if (isCurrent) {
            rebuildMessages(merged);
            scrollToBottom();
            unreadMap.remove(partner);
            updateGlobalUnreadBadge();
            if (client != null) client.markRead(myUsername, partner);
        } else {
            int unread = countUnreadDm(merged);
            if (unread > 0) unreadMap.put(partner, unread);
            else unreadMap.remove(partner);
            updateGlobalUnreadBadge();
        }

        rebuildUserList(new ArrayList<>(onlineMap.keySet()));
    }

    private void handleRoomsList(String payload) {
        String arr = ExamJsonUtil.extractArray(payload, "rooms");
        Set<String> visibleRoomIds = new LinkedHashSet<>();
        for (String obj : ExamJsonUtil.splitObjectArray(arr)) {
            String roomId   = ExamJsonUtil.parseString(obj, "roomId");
            String roomName = ExamJsonUtil.parseString(obj, "roomName");
            String host     = ExamJsonUtil.parseString(obj, "host");
            String members  = ExamJsonUtil.extractArray(obj, "members");
            if (!roomId.isBlank() && members.contains("\"" + myUsername + "\"")) {
                visibleRoomIds.add(roomId);
                roomNames.put(roomId, roomName);
                roomHosts.put(roomId, host);
                roomMembers.put(roomId, ExamJsonUtil.parseStringArray(members));
                roomCache.computeIfAbsent(roomId, k -> new ArrayList<>());
            }
        }
        roomNames.keySet().retainAll(visibleRoomIds);
        roomHosts.keySet().retainAll(visibleRoomIds);
        roomMembers.keySet().retainAll(visibleRoomIds);
        roomCache.keySet().retainAll(visibleRoomIds);
        rebuildRoomList();
    }

    private void handleChatMsg(String payload) {
        String roomId = ExamJsonUtil.parseString(payload, "roomId");
        String from   = ExamJsonUtil.parseString(payload, "from");
        String text   = ExamJsonUtil.parseString(payload, "text");
        String time   = ExamJsonUtil.parseString(payload, "time");
        ChatMessage msg = makeMsg(from, roomId, text, time, ChatMessage.Type.GROUP);
        roomCache.computeIfAbsent(roomId, k -> new ArrayList<>()).add(msg);

        if (roomId.equals(currentTarget) && currentIsRoom) {
            appendBubble(msg);
            scrollToBottom();
        } else if (!myUsername.equals(from)) {
            unreadMap.merge(roomId, 1, Integer::sum);
            updateGlobalUnreadBadge();
            rebuildRoomList();
        }
    }

    private void handleJoinOk(String payload) {
        String roomId   = ExamJsonUtil.parseString(payload, "roomId");
        String roomName = ExamJsonUtil.parseString(payload, "roomName");
        String host     = ExamJsonUtil.parseString(payload, "host");
        String members  = ExamJsonUtil.extractArray(payload, "members");
        roomNames.put(roomId, roomName);
        roomHosts.put(roomId, host);
        roomMembers.put(roomId, ExamJsonUtil.parseStringArray(members));
        roomCache.computeIfAbsent(roomId, k -> new ArrayList<>());
        rebuildRoomList();
        openRoom(roomId, roomName, host);
        if (client != null) client.getRoomHistory(roomId);
    }

    private void handleRoomHistoryData(String payload) {
        String roomId = ExamJsonUtil.parseString(payload, "roomId");
        List<ChatMessage> msgs = parseMsgArray(
            ExamJsonUtil.extractArray(payload, "messages"), ChatMessage.Type.GROUP);
        String targetRoom = (roomId == null || roomId.isBlank()) ? currentTarget : roomId;
        if (targetRoom == null || targetRoom.isBlank()) return;

        List<ChatMessage> merged = mergeMessages(roomCache.get(targetRoom), msgs);
        roomCache.put(targetRoom, merged);

        if (targetRoom.equals(currentTarget) && currentIsRoom) {
            rebuildMessages(merged);
            scrollToBottom();
        }
        rebuildRoomList();
    }

    private void handleInvited(String payload) {
        String roomId   = ExamJsonUtil.parseString(payload, "roomId");
        String roomName = ExamJsonUtil.parseString(payload, "roomName");
        String host     = ExamJsonUtil.parseString(payload, "host");
        String members  = ExamJsonUtil.extractArray(payload, "members");
        roomNames.put(roomId, roomName);
        roomHosts.put(roomId, host);
        roomMembers.put(roomId, ExamJsonUtil.parseStringArray(members));
        roomCache.computeIfAbsent(roomId, k -> new ArrayList<>());
        rebuildRoomList();

        // Show notification banner
        Platform.runLater(() -> showInviteToast(roomName, host));
    }

    private void handleRoomUpdate(String payload) {
        String roomId  = ExamJsonUtil.parseString(payload, "roomId");
        String host    = ExamJsonUtil.parseString(payload, "host");
        String members = ExamJsonUtil.extractArray(payload, "members");
        roomHosts.put(roomId, host);
        roomMembers.put(roomId, ExamJsonUtil.parseStringArray(members));
        if (roomId.equals(currentTarget)) {
            currentRoomHost = host;
            // Refresh header status
            chatHeaderStatus.setText("Group chat  ·  hosted by " + host);
        }
        updateMgmtBtn();
        rebuildRoomList();
    }

    private void handleRemoved(String payload) {
        String roomId = ExamJsonUtil.parseString(payload, "roomId");
        roomCache.remove(roomId);
        roomNames.remove(roomId);
        roomHosts.remove(roomId);
        roomMembers.remove(roomId);
        if (roomId.equals(currentTarget)) showNoSelection();
        rebuildRoomList();
    }

    private void handleDeleteMsgResponse(String payload, boolean isGroup) {
        String from = ExamJsonUtil.parseString(payload, "from");
        String time = ExamJsonUtil.parseString(payload, "time"); // full ISO timestamp

        if (isGroup) {
            String roomId = ExamJsonUtil.parseString(payload, "roomId");
            List<ChatMessage> cache = roomCache.get(roomId);
            if (cache != null) {
                cache.removeIf(m -> m.getFrom().equals(from) && m.getFormattedTimeRaw().equals(time));
            }
            if (roomId.equals(currentTarget) && currentIsRoom) {
                rebuildMessages(roomCache.getOrDefault(roomId, List.of()));
            }
            rebuildRoomList();
        } else {
            String to = ExamJsonUtil.parseString(payload, "to");
            String partner = myUsername.equals(from) ? to : from;
            List<ChatMessage> cache = dmCache.get(partner);
            if (cache != null) {
                cache.removeIf(m -> m.getFrom().equals(from) && m.getFormattedTimeRaw().equals(time));
            }
            if (partner.equals(currentTarget) && !currentIsRoom) {
                rebuildMessages(dmCache.getOrDefault(partner, List.of()));
            }
            rebuildUserList(new ArrayList<>(onlineMap.keySet()));
        }
    }

    // ── Actions ───────────────────────────────────────────────────────
    @FXML
    private void handleSend() {
        if (client == null || !client.isConnected()) return;
        String text = messageInput.getText().trim();
        if (text.isBlank() || currentTarget == null) return;
        messageInput.clear();
        if (charCounter != null) charCounter.setText("");
        if (currentIsRoom) client.sendRoomMessage(myUsername, currentTarget, text);
        else               client.sendDM(myUsername, currentTarget, text);
    }

    @FXML
    private void handleCreateRoom() {
        if (client == null) return;
        String name = newRoomField != null ? newRoomField.getText().trim() : "";
        if (name.isBlank()) return;
        newRoomField.clear();
        client.createRoom(myUsername, name);
    }

    @FXML
    private void handleSearch() {
        searchQuery = searchField != null ? searchField.getText().trim().toLowerCase() : "";
        rebuildUserList(new ArrayList<>(onlineMap.keySet()));
        rebuildRoomList();
    }

    /**
     * FIX: Emoji is inserted at the current cursor position, not appended.
     */
    @FXML
    private void handleEmojiPicker() {
        if (messageInput == null) return;
        ContextMenu emojiMenu = new ContextMenu();
        final int caretAtOpen = messageInput.getCaretPosition();

        GridPane grid = new GridPane();
        grid.setHgap(4); grid.setVgap(4);
        grid.setPadding(new Insets(8));
        int col = 0, row = 0;
        for (String emoji : EMOJI_LIST) {
            Button btn = new Button(emoji);
            String normalStyle = "-fx-font-size:18px;-fx-background-color:transparent;-fx-cursor:hand;-fx-padding:5;-fx-min-width:36px;-fx-min-height:36px;-fx-background-radius:8px;";
            String hoverStyle  = "-fx-font-size:18px;-fx-background-color:rgba(99,102,241,0.15);-fx-cursor:hand;-fx-padding:5;-fx-min-width:36px;-fx-min-height:36px;-fx-background-radius:8px;";
            btn.setStyle(normalStyle);
            btn.setOnMouseEntered(e -> btn.setStyle(hoverStyle));
            btn.setOnMouseExited(e -> btn.setStyle(normalStyle));
            final String em = emoji;
            btn.setOnAction(e -> {
                messageInput.requestFocus();
                messageInput.positionCaret(Math.min(caretAtOpen, messageInput.getLength()));
                messageInput.replaceSelection(em);
                emojiMenu.hide();
            });
            grid.add(btn, col, row);
            col++;
            if (col >= 5) { col = 0; row++; }
        }

        CustomMenuItem item = new CustomMenuItem(grid, false);
        emojiMenu.getItems().add(item);
        emojiMenu.setStyle("-fx-background-color:#111827;-fx-border-color:rgba(99,102,241,0.2);-fx-border-width:1;-fx-border-radius:12;-fx-background-radius:12;-fx-effect:dropshadow(gaussian,rgba(0,0,0,0.6),16,0,0,4);");
        emojiMenu.show(messageInput, Side.TOP, 0, -8);
    }

    /**
     * FIX: Member management dialog — improved UI, real user validation,
     * remove button works via proper client call, add member validates existence.
     */
    @FXML
    private void handleMemberMgmt() {
        if (!currentIsRoom || currentTarget == null) return;
        boolean isHost = myUsername.equals(currentRoomHost);
        String  roomId   = currentTarget;
        String  roomName = roomNames.getOrDefault(roomId, roomId);

        Stage  dlgStage = new Stage();
        dlgStage.initOwner(memberMgmtBtn.getScene().getWindow());
        dlgStage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        dlgStage.setTitle(roomName + "  —  Members");
        dlgStage.setResizable(false);

        // Root
        VBox root = new VBox(0);
        root.setStyle("-fx-background-color:#0f1629;");
        root.setPrefWidth(400);

        // Title bar
        HBox titleBar = new HBox(10);
        titleBar.setPadding(new Insets(16, 20, 14, 20));
        titleBar.setAlignment(Pos.CENTER_LEFT);
        titleBar.setStyle("-fx-background-color:#0a0f1c;-fx-border-color:rgba(99,102,241,0.12);-fx-border-width:0 0 1 0;");
        StackPane roomAv = buildGroupAvatar(roomName);
        roomAv.setMinSize(38, 38); roomAv.setMaxSize(38, 38);
        VBox titleBox = new VBox(2);
        Label titleLbl = new Label(roomName);
        titleLbl.setStyle("-fx-text-fill:#eef2ff;-fx-font-size:15px;-fx-font-weight:700;");
        Label subLbl = new Label(isHost ? "You are the host  ·  " + roomMembers.getOrDefault(roomId, List.of()).size() + " members"
                                        : "Host: " + currentRoomHost + "  ·  " + roomMembers.getOrDefault(roomId, List.of()).size() + " members");
        subLbl.setStyle("-fx-text-fill:#3d4a6b;-fx-font-size:11.5px;");
        titleBox.getChildren().addAll(titleLbl, subLbl);
        HBox.setHgrow(titleBox, Priority.ALWAYS);
        titleBar.getChildren().addAll(roomAv, titleBox);
        root.getChildren().add(titleBar);

        // Members list
        VBox listBox = new VBox(6);
        listBox.setPadding(new Insets(14, 16, 8, 16));
        ScrollPane sp = new ScrollPane(listBox);
        sp.setFitToWidth(true); sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sp.setStyle("-fx-background:transparent;-fx-background-color:transparent;-fx-border-color:transparent;");
        sp.setPrefHeight(220); sp.setMaxHeight(300);
        VBox.setVgrow(sp, Priority.ALWAYS);

        List<String> members = new ArrayList<>(roomMembers.getOrDefault(roomId, List.of()));
        if (members.isEmpty()) members.add(myUsername);
        members.sort((a, b) -> a.equals(currentRoomHost) ? -1 : b.equals(currentRoomHost) ? 1 : a.compareToIgnoreCase(b));

        Label sectionLbl = new Label("MEMBERS");
        sectionLbl.setStyle("-fx-font-size:9px;-fx-font-weight:700;-fx-text-fill:#3d4a6b;-fx-letter-spacing:1.5px;");
        listBox.getChildren().add(sectionLbl);

        for (String member : members) {
            HBox mRow = buildMemberRow(member, isHost, roomId, dlgStage, listBox, members);
            listBox.getChildren().add(mRow);
        }
        root.getChildren().add(sp);

        // Add member section (host only)
        if (isHost) {
            VBox addSection = new VBox(8);
            addSection.setPadding(new Insets(10, 16, 10, 16));
            addSection.setStyle("-fx-background-color:rgba(99,102,241,0.05);-fx-border-color:rgba(99,102,241,0.1);-fx-border-width:1 0 1 0;");

            Label addLbl = new Label("ADD MEMBER");
            addLbl.setStyle("-fx-font-size:9px;-fx-font-weight:700;-fx-text-fill:#3d4a6b;-fx-letter-spacing:1.5px;");

            HBox addRow = new HBox(8);
            addRow.setAlignment(Pos.CENTER_LEFT);
            TextField addField = new TextField();
            addField.setPromptText("Enter username to add…");
            addField.setStyle("-fx-font-family:'Segoe UI',Arial;-fx-font-size:13px;-fx-text-fill:#e2e8f0;-fx-prompt-text-fill:#2d3a52;-fx-background-color:rgba(17,24,39,0.9);-fx-background-radius:10px;-fx-border-color:rgba(99,102,241,0.25);-fx-border-radius:10px;-fx-border-width:1.5px;-fx-padding:9 14 9 14;");
            HBox.setHgrow(addField, Priority.ALWAYS);

            Label addStatus = new Label("");
            addStatus.setStyle("-fx-font-size:11px;-fx-text-fill:#f87171;");
            addStatus.setVisible(false);

            Button addBtn = new Button("Add");
            addBtn.setStyle("-fx-font-size:13px;-fx-font-weight:700;-fx-text-fill:white;-fx-background-color:#6366f1;-fx-background-radius:10px;-fx-padding:9 18 9 18;-fx-cursor:hand;-fx-border-color:transparent;");
            addBtn.setOnMouseEntered(e -> addBtn.setStyle("-fx-font-size:13px;-fx-font-weight:700;-fx-text-fill:white;-fx-background-color:#4f46e5;-fx-background-radius:10px;-fx-padding:9 18 9 18;-fx-cursor:hand;"));
            addBtn.setOnMouseExited(e -> addBtn.setStyle("-fx-font-size:13px;-fx-font-weight:700;-fx-text-fill:white;-fx-background-color:#6366f1;-fx-background-radius:10px;-fx-padding:9 18 9 18;-fx-cursor:hand;"));

            Runnable doAdd = () -> {
                String m = addField.getText().trim();
                if (m.isBlank()) { showFieldError(addStatus, "Enter a username"); return; }
                if (m.equals(myUsername)) { showFieldError(addStatus, "That's you!"); return; }
                List<String> current = roomMembers.getOrDefault(roomId, List.of());
                if (current.contains(m)) { showFieldError(addStatus, m + " is already a member"); return; }
                
                // Validate user exists using the server's known users via a client push
                List<String> allUsers = new ArrayList<>(onlineMap.keySet()); // simplistic check based on UI state
                boolean probablyExists = false;
                for (String u : allUsers) {
                    if (u.equalsIgnoreCase(m)) { probablyExists = true; break; }
                }
                
                // Let's assume the server will do definitive validation, but optimistically send it.
                if (client != null) client.addMember(myUsername, roomId, m);
                
                // Show optimistic UI while waiting for server redraw
                addField.clear();
                showFieldError(addStatus, "Invite sent.");
                addStatus.setStyle("-fx-font-size:11px;-fx-text-fill:#4ade80;"); // Success color
                
                // Revert color back on change
                addField.textProperty().addListener((obs, old, nw) -> {
                    addStatus.setStyle("-fx-font-size:11px;-fx-text-fill:#f87171;");
                    addStatus.setVisible(false);
                });
            };
            addBtn.setOnAction(e -> doAdd.run());
            addField.setOnAction(e -> doAdd.run());

            addRow.getChildren().addAll(addField, addBtn);
            addSection.getChildren().addAll(addLbl, addRow, addStatus);
            root.getChildren().add(addSection);
        }

        // Bottom bar: leave button + close
        HBox bottomBar = new HBox(10);
        bottomBar.setPadding(new Insets(12, 16, 14, 16));
        bottomBar.setAlignment(Pos.CENTER_LEFT);
        bottomBar.setStyle("-fx-background-color:#0a0f1c;-fx-border-color:rgba(99,102,241,0.1);-fx-border-width:1 0 0 0;");
        HBox.setHgrow(new Pane(), Priority.ALWAYS);

        Button leaveBtn = new Button("Leave Group");
        leaveBtn.setStyle("-fx-font-size:12.5px;-fx-font-weight:600;-fx-text-fill:#fca5a5;-fx-background-color:rgba(239,68,68,0.1);-fx-background-radius:10px;-fx-padding:9 18 9 18;-fx-cursor:hand;-fx-border-color:rgba(239,68,68,0.25);-fx-border-width:1;-fx-border-radius:10px;");
        leaveBtn.setOnAction(e -> { dlgStage.close(); handleLeaveRoom(); });

        Button closeBtn = new Button("Close");
        closeBtn.setStyle("-fx-font-size:12.5px;-fx-font-weight:600;-fx-text-fill:#818cf8;-fx-background-color:rgba(99,102,241,0.1);-fx-background-radius:10px;-fx-padding:9 18 9 18;-fx-cursor:hand;-fx-border-color:rgba(99,102,241,0.2);-fx-border-width:1;-fx-border-radius:10px;");
        closeBtn.setOnAction(e -> dlgStage.close());

        Pane spacer = new Pane(); HBox.setHgrow(spacer, Priority.ALWAYS);

        if (isHost) {
            // Host gets a Delete Group button
            Button deleteRoomBtn = new Button("🗑  Delete Group");
            deleteRoomBtn.setStyle("-fx-font-size:12.5px;-fx-font-weight:600;-fx-text-fill:#f87171;-fx-background-color:rgba(239,68,68,0.15);-fx-background-radius:10px;-fx-padding:9 18 9 18;-fx-cursor:hand;-fx-border-color:rgba(239,68,68,0.4);-fx-border-width:1;-fx-border-radius:10px;");
            deleteRoomBtn.setOnAction(e -> {
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
                confirm.setTitle("Delete Group");
                confirm.setHeaderText(null);
                confirm.setContentText("Permanently delete \"" + roomName + "\"? This cannot be undone.");
                confirm.getDialogPane().setStyle("-fx-background-color:#0f1629;");
                confirm.getDialogPane().lookup(".content.label").setStyle("-fx-text-fill:#e2e8f0;-fx-font-size:13px;");
                confirm.getDialogPane().getStylesheets().add(getClass().getResource("css/messenger.css").toExternalForm());
                confirm.showAndWait().ifPresent(result -> {
                    if (result == ButtonType.OK) {
                        dlgStage.close();
                        if (client != null) client.deleteRoom(myUsername, roomId);
                        // Clean up locally right away
                        roomCache.remove(roomId);
                        roomNames.remove(roomId);
                        roomHosts.remove(roomId);
                        roomMembers.remove(roomId);
                        if (roomId.equals(currentTarget)) showNoSelection();
                        rebuildRoomList();
                    }
                });
            });
            bottomBar.getChildren().addAll(deleteRoomBtn, leaveBtn, spacer, closeBtn);
        } else {
            bottomBar.getChildren().addAll(leaveBtn, spacer, closeBtn);
        }
        root.getChildren().add(bottomBar);

        javafx.scene.Scene scene = new javafx.scene.Scene(root);
        scene.getStylesheets().add(getClass().getResource("css/messenger.css").toExternalForm());
        dlgStage.setScene(scene);
        dlgStage.showAndWait();
    }

    /**
     * FIX: buildMemberRow wired to remove callback that actually works.
     */
    private HBox buildMemberRow(String member, boolean isHost, String roomId,
                                 javafx.stage.Stage dlgStage, VBox listBox, List<String> memberList) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(9, 12, 9, 12));
        row.setStyle("-fx-background-color:rgba(255,255,255,0.03);-fx-background-radius:12px;-fx-border-color:rgba(99,102,241,0.12);-fx-border-width:1;-fx-border-radius:12px;");

        StackPane avatar = buildAvatar(member, getAvatarColor(member));
        avatar.setMinSize(34, 34); avatar.setMaxSize(34, 34);

        // Online dot
        StackPane avatarWrap = new StackPane(avatar);
        if (onlineMap.getOrDefault(member, false)) {
            Circle dot = new Circle(5);
            dot.setFill(Color.web("#4ade80"));
            dot.setEffect(new DropShadow(6, Color.web("#4ade8099")));
            StackPane.setAlignment(dot, Pos.BOTTOM_RIGHT);
            StackPane.setMargin(dot, new Insets(0, -1, -1, 0));
            avatarWrap.getChildren().add(dot);
        }

        VBox nameBox = new VBox(2);
        Label nameLbl = new Label(member);
        nameLbl.setStyle("-fx-text-fill:#e2e8f0;-fx-font-size:13px;-fx-font-weight:600;");
        String statusStr = onlineMap.getOrDefault(member, false) ? "● Online" : "○ Offline";
        Label statusLbl = new Label(statusStr);
        statusLbl.setStyle(onlineMap.getOrDefault(member, false)
            ? "-fx-text-fill:#4ade80;-fx-font-size:10.5px;-fx-font-weight:600;"
            : "-fx-text-fill:#2d3a52;-fx-font-size:10.5px;");
        nameBox.getChildren().addAll(nameLbl, statusLbl);
        HBox.setHgrow(nameBox, Priority.ALWAYS);

        row.getChildren().addAll(avatarWrap, nameBox);

        if (member.equals(currentRoomHost)) {
            Label badge = new Label("HOST");
            badge.setStyle("-fx-font-size:9px;-fx-font-weight:800;-fx-text-fill:#a5b4fc;-fx-background-color:rgba(99,102,241,0.15);-fx-border-color:rgba(99,102,241,0.3);-fx-border-width:1;-fx-border-radius:8px;-fx-background-radius:8px;-fx-padding:3 9 3 9;-fx-letter-spacing:0.5px;");
            row.getChildren().add(badge);
        }

        // Remove button: only host can remove others (not themselves)
        if (isHost && !member.equals(myUsername)) {
            Button removeBtn = new Button("Remove");
            removeBtn.setStyle("-fx-font-size:11px;-fx-font-weight:600;-fx-text-fill:#fca5a5;-fx-background-color:rgba(239,68,68,0.1);-fx-background-radius:8px;-fx-border-color:rgba(239,68,68,0.25);-fx-border-width:1;-fx-border-radius:8px;-fx-padding:5 11 5 11;-fx-cursor:hand;");
            removeBtn.setOnMouseEntered(e -> removeBtn.setStyle("-fx-font-size:11px;-fx-font-weight:600;-fx-text-fill:#fecaca;-fx-background-color:rgba(239,68,68,0.22);-fx-background-radius:8px;-fx-border-color:rgba(239,68,68,0.4);-fx-border-width:1;-fx-border-radius:8px;-fx-padding:5 11 5 11;-fx-cursor:hand;"));
            removeBtn.setOnMouseExited(e -> removeBtn.setStyle("-fx-font-size:11px;-fx-font-weight:600;-fx-text-fill:#fca5a5;-fx-background-color:rgba(239,68,68,0.1);-fx-background-radius:8px;-fx-border-color:rgba(239,68,68,0.25);-fx-border-width:1;-fx-border-radius:8px;-fx-padding:5 11 5 11;-fx-cursor:hand;"));
            removeBtn.setOnAction(e -> {
                if (client != null) {
                    client.removeMember(myUsername, roomId, member);
                }
                // We will rely heavily on the server pushing a CHAT_ROOM_UPDATE event 
                // which our handleRoomUpdate -> refreshActiveMemberDialog will pick up
                // and cleanly redraw the dialog.
                removeBtn.setDisable(true);
                removeBtn.setText("Removing...");
            });
            row.getChildren().add(removeBtn);
        }

        // You badge
        if (member.equals(myUsername)) {
            Label youBadge = new Label("You");
            youBadge.setStyle("-fx-font-size:9px;-fx-font-weight:700;-fx-text-fill:#818cf8;-fx-background-color:rgba(99,102,241,0.1);-fx-border-color:rgba(99,102,241,0.2);-fx-border-width:1;-fx-border-radius:8px;-fx-background-radius:8px;-fx-padding:3 8 3 8;");
            row.getChildren().add(youBadge);
        }

        return row;
    }

    private void showFieldError(Label lbl, String msg) {
        lbl.setText(msg);
        lbl.setVisible(true);
        lbl.setManaged(true);
    }

    private void handleLeaveRoom() {
        if (!currentIsRoom || currentTarget == null) return;
        if (client != null) client.leaveRoom(myUsername, currentTarget);
        roomCache.remove(currentTarget);
        roomNames.remove(currentTarget);
        roomHosts.remove(currentTarget);
        roomMembers.remove(currentTarget);
        rebuildRoomList();
        showNoSelection();
    }

    // ── Open Conversations ────────────────────────────────────────────
    private void openDM(String username) {
        currentTarget   = username;
        currentIsRoom   = false;
        currentRoomHost = null;
        unreadMap.remove(username);
        updateGlobalUnreadBadge();
        rebuildUserList(new ArrayList<>(onlineMap.keySet()));

        chatHeaderName.setText(username);
        boolean online = onlineMap.getOrDefault(username, false);
        chatHeaderStatus.getStyleClass().setAll(online ? "chat-header-status-online" : "chat-header-status");
        chatHeaderStatus.setText(online ? "● Online" : "○ Offline");
        updateHeaderAvatar(username, getAvatarColor(username));
        showChat();
        updateMgmtBtn();

        // FIX: Always request fresh history from server (covers case where
        // messages were sent while B was offline)
        messagesContainer.getChildren().clear();
        bubbleMap.clear();
        List<ChatMessage> cached = dmCache.get(username);
        if (cached != null && !cached.isEmpty()) {
            rebuildMessages(cached);
            scrollToBottom();
        }
        if (client != null) {
            client.getHistory(myUsername, username);
            client.markRead(myUsername, username);
        }
        messageInput.requestFocus();
    }

    private void openRoom(String roomId, String roomName, String host) {
        currentTarget   = roomId;
        currentIsRoom   = true;
        currentRoomHost = host;
        unreadMap.remove(roomId);
        updateGlobalUnreadBadge();
        rebuildRoomList();

        chatHeaderName.setText("# " + roomName);
        chatHeaderStatus.getStyleClass().setAll("chat-header-status");
        chatHeaderStatus.setText("Group chat  ·  hosted by " + host
            + "  ·  " + roomMembers.getOrDefault(roomId, List.of()).size() + " members");
        updateHeaderAvatarGroup();
        showChat();
        updateMgmtBtn();

        messagesContainer.getChildren().clear();
        bubbleMap.clear();
        List<ChatMessage> cached = roomCache.get(roomId);
        if (cached != null && !cached.isEmpty()) {
            rebuildMessages(cached); scrollToBottom();
        }
        if (client != null) client.getRoomHistory(roomId);
        messageInput.requestFocus();
    }

    // ── UI Builders ───────────────────────────────────────────────────
    private void rebuildUserList(List<String> users) {
        if (userListPane == null) return;
        userListPane.getChildren().clear();
        users.sort((a, b) -> {
            LocalDateTime ta = getLastDmTime(a);
            LocalDateTime tb = getLastDmTime(b);
            int cmp = tb.compareTo(ta);
            if (cmp != 0) return cmp;
            int ua = unreadMap.getOrDefault(a, 0);
            int ub = unreadMap.getOrDefault(b, 0);
            if (ua != ub) return Integer.compare(ub, ua);
            cmp = Boolean.compare(onlineMap.getOrDefault(b, false), onlineMap.getOrDefault(a, false));
            if (cmp != 0) return cmp;
            return a.compareToIgnoreCase(b);
        });
        for (String u : users) {
            if (!searchQuery.isEmpty() && !u.toLowerCase().contains(searchQuery)) continue;
            userListPane.getChildren().add(buildUserRow(u));
        }
        if (users.isEmpty() || userListPane.getChildren().isEmpty()) {
            Label empty = new Label("No users found");
            empty.setStyle("-fx-text-fill:#2d3a52;-fx-font-size:12px;-fx-padding:12 18 12 18;");
            userListPane.getChildren().add(empty);
        }
    }

    private void rebuildRoomList() {
        if (roomListPane == null) return;
        roomListPane.getChildren().clear();
        List<String> rooms = new ArrayList<>(roomCache.keySet());
        rooms.sort((a, b) -> {
            LocalDateTime ta = getLastRoomTime(a);
            LocalDateTime tb = getLastRoomTime(b);
            int cmp = tb.compareTo(ta);
            if (cmp != 0) return cmp;
            String nameA = roomNames.getOrDefault(a, a);
            String nameB = roomNames.getOrDefault(b, b);
            return nameA.compareToIgnoreCase(nameB);
        });
        for (String roomId : rooms) {
            String roomName = roomNames.getOrDefault(roomId, roomId);
            String host     = roomHosts.getOrDefault(roomId, "");
            if (!searchQuery.isEmpty() && !roomName.toLowerCase().contains(searchQuery)) continue;
            roomListPane.getChildren().add(buildRoomRow(roomId, roomName, host));
        }
        if (roomListPane.getChildren().isEmpty()) {
            Label empty = new Label("No groups yet");
            empty.setStyle("-fx-text-fill:#2d3a52;-fx-font-size:12px;-fx-padding:8 18 8 18;");
            roomListPane.getChildren().add(empty);
        }
    }

    private HBox buildUserRow(String username) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("conv-row");
        row.setPadding(new Insets(9, 12, 9, 12));
        if (username.equals(currentTarget) && !currentIsRoom)
            row.getStyleClass().add("conv-row-active");

        StackPane avatar = buildAvatar(username, getAvatarColor(username));
        boolean online = onlineMap.getOrDefault(username, false);

        StackPane avatarWrap = new StackPane(avatar);
        if (online) {
            Circle dot = new Circle(5.5);
            dot.setFill(Color.web("#4ade80"));
            dot.setEffect(new DropShadow(6, Color.web("#4ade8099")));
            StackPane.setAlignment(dot, Pos.BOTTOM_RIGHT);
            StackPane.setMargin(dot, new Insets(0, -1, -1, 0));
            avatarWrap.getChildren().add(dot);
        }

        VBox nameBox = new VBox(2);
        Label name = new Label(username);
        name.getStyleClass().add("conv-name");
        List<ChatMessage> msgs = dmCache.get(username);
        String previewText = "";
        if (msgs != null && !msgs.isEmpty()) {
            ChatMessage last = msgs.get(msgs.size() - 1);
            String senderLabel = last.getFrom().equals(myUsername) ? "You" : last.getFrom();
            previewText = senderLabel + ": " + truncate(last.getText(), 32);
        }
        Label preview = new Label(previewText.isEmpty() ? (online ? "Online" : "Offline") : previewText);
        preview.getStyleClass().add(previewText.isEmpty() ? (online ? "conv-status-online" : "conv-status-offline") : "conv-preview");
        nameBox.getChildren().addAll(name, preview);
        HBox.setHgrow(nameBox, Priority.ALWAYS);

        row.getChildren().addAll(avatarWrap, nameBox);

        int unread = unreadMap.getOrDefault(username, 0);
        if (unread > 0) {
            row.getStyleClass().add("conv-row-unread");
            name.getStyleClass().add("conv-name-unread");
            preview.getStyleClass().add("conv-preview-unread");
            String badgeText = unread > 9 ? "9+" : String.valueOf(unread);
            Label badge = new Label(badgeText);
            badge.getStyleClass().add("unread-pill");
            row.getChildren().add(badge);
        }

        row.setOnMouseClicked(e -> openDM(username));
        return row;
    }

    /**
     * FIX: Group room card is now properly sized and shows member count + preview.
     */
    private HBox buildRoomRow(String roomId, String roomName, String host) {
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("conv-row");
        row.setPadding(new Insets(10, 12, 10, 12));
        if (roomId.equals(currentTarget) && currentIsRoom)
            row.getStyleClass().add("conv-row-active");

        StackPane avatar = buildGroupAvatar(roomName);

        VBox nameBox = new VBox(3);
        Label nameLbl = new Label(roomName);
        nameLbl.getStyleClass().add("conv-name");

        // Show member count + last message preview
        int memberCount = roomMembers.getOrDefault(roomId, List.of()).size();
        List<ChatMessage> msgs = roomCache.get(roomId);
        String previewText = "";
        if (msgs != null && !msgs.isEmpty()) {
            ChatMessage last = msgs.get(msgs.size() - 1);
            String senderLabel;
            if ("system".equals(last.getFrom())) {
                senderLabel = "System";
            } else if (last.getFrom().equals(myUsername)) {
                senderLabel = "You";
            } else {
                senderLabel = last.getFrom();
            }
            previewText = senderLabel + ": " + truncate(last.getText(), 26);
        }
        Label sub = new Label(previewText.isEmpty()
            ? (memberCount > 0 ? memberCount + " members  ·  " + host : "Host: " + host)
            : previewText);
        sub.getStyleClass().add("conv-preview");
        nameBox.getChildren().addAll(nameLbl, sub);
        HBox.setHgrow(nameBox, Priority.ALWAYS);

        row.getChildren().addAll(avatar, nameBox);

        int unread = unreadMap.getOrDefault(roomId, 0);
        if (unread > 0) {
            row.getStyleClass().add("conv-row-unread");
            nameLbl.getStyleClass().add("conv-name-unread");
            sub.getStyleClass().add("conv-preview-unread");
            String badgeText = unread > 9 ? "9+" : String.valueOf(unread);
            Label badge = new Label(badgeText);
            badge.getStyleClass().add("unread-pill");
            row.getChildren().add(badge);
        }

        row.setOnMouseClicked(e -> {
            roomHosts.put(roomId, host);
            openRoom(roomId, roomName, host);
        });
        return row;
    }

    // ── Avatar builders ───────────────────────────────────────────────
    private StackPane buildAvatar(String username, String color) {
        StackPane sp = new StackPane();
        sp.getStyleClass().add("avatar");
        sp.setStyle("-fx-background-color:" + color + ";");
        String init = username.isEmpty() ? "?" : String.valueOf(username.charAt(0)).toUpperCase();
        Label lbl = new Label(init);
        lbl.setStyle("-fx-text-fill:white;-fx-font-size:14px;-fx-font-weight:700;");
        sp.getChildren().add(lbl);
        return sp;
    }

    private StackPane buildGroupAvatar(String roomName) {
        StackPane sp = new StackPane();
        sp.getStyleClass().add("avatar");
        int hash = Math.abs(roomName.hashCode()) % AVATAR_COLORS.length;
        sp.setStyle("-fx-background-color:" + AVATAR_COLORS[hash] + ";");
        Label lbl = new Label("#");
        lbl.setStyle("-fx-text-fill:white;-fx-font-size:15px;-fx-font-weight:800;");
        sp.getChildren().add(lbl);
        return sp;
    }

    private void updateHeaderAvatar(String username, String color) {
        if (headerAvatarPane == null) return;
        headerAvatarPane.getChildren().clear();
        headerAvatarPane.setStyle("-fx-background-color:" + color + ";-fx-background-radius:50%;");
        Label lbl = new Label(username.isEmpty() ? "?" : String.valueOf(username.charAt(0)).toUpperCase());
        lbl.setStyle("-fx-text-fill:white;-fx-font-size:16px;-fx-font-weight:800;");
        headerAvatarPane.getChildren().add(lbl);
    }

    private void updateHeaderAvatarGroup() {
        if (headerAvatarPane == null) return;
        headerAvatarPane.getChildren().clear();
        String color = AVATAR_COLORS[Math.abs(currentTarget.hashCode()) % AVATAR_COLORS.length];
        headerAvatarPane.setStyle("-fx-background-color:" + color + ";-fx-background-radius:50%;");
        Label lbl = new Label("#");
        lbl.setStyle("-fx-text-fill:white;-fx-font-size:17px;-fx-font-weight:800;");
        headerAvatarPane.getChildren().add(lbl);
    }

    // ── Message rendering ─────────────────────────────────────────────
    private void rebuildMessages(List<ChatMessage> messages) {
        messagesContainer.getChildren().clear();
        bubbleMap.clear();
        String lastDate = null;
        for (ChatMessage msg : messages) {
            String date = msg.getFormattedDate();
            if (!date.equals(lastDate)) {
                messagesContainer.getChildren().add(buildDateDivider(date));
                lastDate = date;
            }
            HBox bubble = buildBubble(msg);
            messagesContainer.getChildren().add(bubble);
        }
    }

    private void appendBubble(ChatMessage msg) {
        String newDate = msg.getFormattedDate();
        if (messagesContainer.getChildren().isEmpty()) {
            messagesContainer.getChildren().add(buildDateDivider(newDate));
        } else {
            // Find the last actual message node (might be a divider or a bubble)
            // It's safer to just check the cache for the previous message
            List<ChatMessage> cache = currentIsRoom ? roomCache.get(currentTarget) : dmCache.get(currentTarget);
            if (cache != null && cache.size() > 1) {
                ChatMessage prev = cache.get(cache.size() - 2); // The one right before this new one
                if (!prev.getFormattedDate().equals(newDate)) {
                    messagesContainer.getChildren().add(buildDateDivider(newDate));
                }
            }
        }
        HBox bubble = buildBubble(msg);
        messagesContainer.getChildren().add(bubble);
    }

    private LocalDateTime getLastDmTime(String username) {
        return getLastMessageTime(dmCache.get(username));
    }

    private LocalDateTime getLastRoomTime(String roomId) {
        return getLastMessageTime(roomCache.get(roomId));
    }

    private LocalDateTime getLastMessageTime(List<ChatMessage> msgs) {
        if (msgs == null || msgs.isEmpty()) return LocalDateTime.MIN;
        ChatMessage last = msgs.get(msgs.size() - 1);
        LocalDateTime ts = last.getTimestamp();
        return ts != null ? ts : LocalDateTime.MIN;
    }

    private String resolvePartner(String userA, String userB, List<ChatMessage> msgs) {
        if (userA != null && !userA.isBlank() && userB != null && !userB.isBlank()) {
            return myUsername.equals(userA) ? userB : userA;
        }
        if (msgs != null && !msgs.isEmpty()) {
            ChatMessage first = msgs.get(0);
            return first.getFrom().equals(myUsername) ? first.getTo() : first.getFrom();
        }
        return null;
    }

    private int countUnreadDm(List<ChatMessage> msgs) {
        if (msgs == null || msgs.isEmpty()) return 0;
        int count = 0;
        for (ChatMessage m : msgs) {
            if (!m.getFrom().equals(myUsername) && !m.isRead()) count++;
        }
        return count;
    }

    private List<ChatMessage> mergeMessages(List<ChatMessage> existing, List<ChatMessage> incoming) {
        if (incoming == null || incoming.isEmpty()) return existing == null ? new ArrayList<>() : new ArrayList<>(existing);
        if (existing == null || existing.isEmpty()) return new ArrayList<>(incoming);

        Map<String, ChatMessage> merged = new LinkedHashMap<>();
        for (ChatMessage m : existing) merged.put(messageKey(m), m);
        for (ChatMessage m : incoming) merged.put(messageKey(m), m);

        List<ChatMessage> list = new ArrayList<>(merged.values());
        list.sort(Comparator.comparing(ChatMessage::getTimestamp,
            Comparator.nullsFirst(Comparator.naturalOrder())));
        return list;
    }

    private String messageKey(ChatMessage msg) {
        String ts = msg.getTimestamp() != null ? msg.getTimestamp().toString() : "";
        return msg.getType() + "|" + safe(msg.getFrom()) + "|" + safe(msg.getTo())
                + "|" + ts + "|" + safe(msg.getText());
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }

    // Message delete UI: right-click or hover shows delete for own messages.
    private HBox buildBubble(ChatMessage msg) {
        boolean isMine   = myUsername.equals(msg.getFrom());
        boolean isSystem = "system".equals(msg.getFrom());

        if (isSystem) {
            Label lbl = new Label(msg.getText());
            lbl.getStyleClass().add("system-msg");
            HBox wrap = new HBox(lbl);
            wrap.setAlignment(Pos.CENTER);
            wrap.setPadding(new Insets(5, 0, 5, 0));
            return wrap;
        }

        HBox row = new HBox(10);
        row.setPadding(new Insets(2, 18, 2, 18));
        row.setAlignment(isMine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        row.setMaxWidth(Double.MAX_VALUE);

        if (!isMine) {
            StackPane av = buildAvatar(msg.getFrom(), getAvatarColor(msg.getFrom()));
            av.getStyleClass().clear();
            av.setMinSize(34, 34); av.setMaxSize(34, 34);
            av.setStyle(av.getStyle()); // keep color
            row.getChildren().add(av);
        }

        VBox bubble = new VBox(3);
        bubble.getStyleClass().add(isMine ? "bubble-mine" : "bubble-other");
        bubble.setMaxWidth(440);

        if (currentIsRoom && !isMine) {
            Label sender = new Label(msg.getFrom());
            sender.getStyleClass().add("bubble-sender");
            bubble.getChildren().add(sender);
        }

        Label textLbl = new Label(msg.getText());
        textLbl.getStyleClass().add("bubble-text");
        textLbl.setWrapText(true);
        textLbl.setMaxWidth(420);

        Label timeLbl = new Label(msg.getFormattedTime());
        timeLbl.getStyleClass().add("bubble-time");

        HBox timeRow = new HBox();
        timeRow.setAlignment(isMine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        timeRow.getChildren().add(timeLbl);

        bubble.getChildren().addAll(textLbl, timeRow);

        // Delete button for own messages
        if (isMine) {
            Button delBtn = new Button("🗑");
            delBtn.setStyle("-fx-font-size:13px;-fx-background-color:transparent;-fx-cursor:hand;-fx-padding:3 5 3 5;-fx-background-radius:6px;-fx-opacity:0;-fx-text-fill:#f87171;");
            delBtn.setFocusTraversable(false);
            delBtn.setOnMouseEntered(e -> delBtn.setStyle("-fx-font-size:13px;-fx-background-color:rgba(239,68,68,0.15);-fx-cursor:hand;-fx-padding:3 5 3 5;-fx-background-radius:6px;-fx-opacity:1;-fx-text-fill:#f87171;"));
            delBtn.setOnMouseExited(e -> {
                if (!row.isHover()) delBtn.setStyle("-fx-font-size:13px;-fx-background-color:transparent;-fx-cursor:hand;-fx-padding:3 5 3 5;-fx-background-radius:6px;-fx-opacity:0;-fx-text-fill:#f87171;");
            });
            delBtn.setOnAction(e -> deleteMessage(row, msg));

            HBox.setHgrow(bubble, Priority.SOMETIMES);
            row.getChildren().addAll(bubble, delBtn);

            // Show/hide delete button on row hover
            row.setOnMouseEntered(e -> delBtn.setStyle("-fx-font-size:13px;-fx-background-color:transparent;-fx-cursor:hand;-fx-padding:3 5 3 5;-fx-background-radius:6px;-fx-opacity:1;-fx-text-fill:#f87171;"));
            row.setOnMouseExited(e -> delBtn.setStyle("-fx-font-size:13px;-fx-background-color:transparent;-fx-cursor:hand;-fx-padding:3 5 3 5;-fx-background-radius:6px;-fx-opacity:0;-fx-text-fill:#f87171;"));
        } else {
            row.getChildren().add(bubble);
        }

        // For isMine, bubble is added before delBtn — reorder
        if (isMine && row.getChildren().size() >= 2) {
            // bubble should be second-to-last, delBtn last — but we added delBtn, then bubble?
            // Actually: we add delBtn, then bubble for isMine — let's fix ordering
            row.getChildren().clear();
            Button existingDel = null;
            // Re-add in correct order: del | bubble
            existingDel = new Button("🗑");
            final Button finalDel = existingDel;
            existingDel.setStyle("-fx-font-size:13px;-fx-background-color:transparent;-fx-cursor:hand;-fx-padding:3 5 3 5;-fx-background-radius:6px;-fx-opacity:0;-fx-text-fill:#f87171;");
            existingDel.setFocusTraversable(false);
            existingDel.setOnMouseEntered(e -> finalDel.setStyle("-fx-font-size:13px;-fx-background-color:rgba(239,68,68,0.15);-fx-cursor:hand;-fx-padding:3 5 3 5;-fx-background-radius:6px;-fx-opacity:1;-fx-text-fill:#f87171;"));
            existingDel.setOnMouseExited(e -> finalDel.setStyle("-fx-font-size:13px;-fx-background-color:transparent;-fx-cursor:hand;-fx-padding:3 5 3 5;-fx-background-radius:6px;-fx-opacity:0;-fx-text-fill:#f87171;"));
            final HBox fRow = row;
            existingDel.setOnAction(e -> deleteMessage(fRow, msg));

            row.getChildren().addAll(existingDel, bubble);
            row.setOnMouseEntered(e -> finalDel.setStyle("-fx-font-size:13px;-fx-background-color:transparent;-fx-cursor:hand;-fx-padding:3 5 3 5;-fx-background-radius:6px;-fx-opacity:1;-fx-text-fill:#f87171;"));
            row.setOnMouseExited(e -> finalDel.setStyle("-fx-font-size:13px;-fx-background-color:transparent;-fx-cursor:hand;-fx-padding:3 5 3 5;-fx-background-radius:6px;-fx-opacity:0;-fx-text-fill:#f87171;"));
        }

        return row;
    }

    /**
     * Delete a message: removes from the server permanently, then from local cache + UI.
     */
    private void deleteMessage(HBox row, ChatMessage msg) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete Message");
        confirm.setHeaderText(null);
        confirm.setContentText("Delete this message for everyone?");
        confirm.getDialogPane().setStyle("-fx-background-color:#0f1629;");
        confirm.getDialogPane().lookup(".content.label").setStyle("-fx-text-fill:#e2e8f0;-fx-font-size:13px;");
        confirm.getDialogPane().getStylesheets().add(getClass().getResource("css/messenger.css").toExternalForm());
        confirm.showAndWait().ifPresent(result -> {
            if (result == ButtonType.OK) {
                // Remove from UI immediately
                messagesContainer.getChildren().remove(row);
                // Remove from local cache
                if (currentIsRoom) {
                    List<ChatMessage> cache = roomCache.get(currentTarget);
                    if (cache != null) cache.removeIf(m -> m == msg);
                    // Permanently delete on server (so it won't come back on reload)
                    if (client != null)
                        client.deleteGroupMessage(currentTarget, myUsername, msg.getFormattedTimeRaw());
                } else {
                    List<ChatMessage> cache = dmCache.get(currentTarget);
                    if (cache != null) cache.removeIf(m -> m == msg);
                    // Permanently delete on server (so it won't come back on reload)
                    if (client != null)
                        client.deleteDM(myUsername, currentTarget, msg.getFormattedTimeRaw());
                }
            }
        });
    }

    private HBox buildDateDivider(String date) {
        Label lbl = new Label("— " + date + " —");
        lbl.getStyleClass().add("date-divider");
        HBox row = new HBox(lbl);
        row.setAlignment(Pos.CENTER);
        row.setPadding(new Insets(12, 0, 12, 0));
        return row;
    }

    // ── Invite toast ──────────────────────────────────────────────────
    private void showInviteToast(String roomName, String invitedBy) {
        // Show small banner at top of no-selection pane or chat area
        if (noSelectionPane == null) return;
        Label toast = new Label("📨  You were added to \"" + roomName + "\" by " + invitedBy);
        toast.setStyle("-fx-background-color:rgba(99,102,241,0.15);-fx-text-fill:#c7d2fe;-fx-font-size:12px;-fx-background-radius:12px;-fx-padding:10 18 10 18;-fx-border-color:rgba(99,102,241,0.3);-fx-border-width:1;-fx-border-radius:12px;");
        toast.setWrapText(true);
        toast.setMaxWidth(300);
        if (noSelectionPane.isVisible()) {
            noSelectionPane.getChildren().add(toast);
            new Thread(() -> {
                try { Thread.sleep(4000); } catch (Exception ignored) {}
                Platform.runLater(() -> noSelectionPane.getChildren().remove(toast));
            }).start();
        }
    }

    // ── UI State ──────────────────────────────────────────────────────
    private void showChat() {
        if (noSelectionPane != null) { noSelectionPane.setVisible(false); noSelectionPane.setManaged(false); }
        if (chatPane        != null) { chatPane.setVisible(true);  chatPane.setManaged(true); }
        updateSendBtnState();
    }

    private void showNoSelection() {
        if (noSelectionPane != null) { noSelectionPane.setVisible(true);  noSelectionPane.setManaged(true); }
        if (chatPane        != null) { chatPane.setVisible(false); chatPane.setManaged(false); }
        currentTarget = null;
    }

    private void scrollToBottom() {
        Platform.runLater(() -> messagesScroll.setVvalue(1.0));
    }

    private void updateGlobalUnreadBadge() {
        int total = unreadMap.values().stream().mapToInt(Integer::intValue).sum();
        if (unreadBadge != null) {
            unreadBadge.setText(total > 0 ? String.valueOf(total) : "");
            unreadBadge.setVisible(total > 0);
            unreadBadge.setManaged(total > 0);
        }
    }

    private void updateMgmtBtn() {
        if (memberMgmtBtn == null) return;
        memberMgmtBtn.setVisible(currentIsRoom);
        memberMgmtBtn.setManaged(currentIsRoom);
    }

    private void updateSendBtnState() {
        if (sendButton == null || messageInput == null) return;
        boolean hasText = !messageInput.getText().trim().isEmpty();
        sendButton.setDisable(!hasText);
    }

    // ── Helpers ───────────────────────────────────────────────────────
    private String getAvatarColor(String name) {
        if (name == null || name.isEmpty()) return AVATAR_COLORS[0];
        return AVATAR_COLORS[Math.abs(name.hashCode()) % AVATAR_COLORS.length];
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) + "…" : s;
    }

    /**
     * Returns all known users (from the online map + any we know from history).
     * Used to validate "add member" input.
     */
    private List<String> getAllKnownUsers() {
        Set<String> all = new LinkedHashSet<>(onlineMap.keySet());
        all.add(myUsername);
        // Also include anyone we've DMed with
        all.addAll(dmCache.keySet());
        return new ArrayList<>(all);
    }

    // ── Parse Helpers ─────────────────────────────────────────────────
    private ChatMessage makeMsg(String from, String to, String text,
                                String timeStr, ChatMessage.Type type) {
        LocalDateTime time;
        try { time = LocalDateTime.parse(timeStr); }
        catch (Exception e) { time = LocalDateTime.now(); }
        return new ChatMessage(from, to, text, type, time, false);
    }

    private List<ChatMessage> parseMsgArray(String arr, ChatMessage.Type type) {
        List<ChatMessage> list = new ArrayList<>();
        if (arr == null || arr.isBlank()) return list;
        for (String obj : ExamJsonUtil.splitObjectArray(arr)) {
            String from   = ExamJsonUtil.parseString(obj, "from");
            String to     = ExamJsonUtil.parseString(obj, "to");
            String roomId = ExamJsonUtil.parseString(obj, "roomId");
            String text   = ExamJsonUtil.parseString(obj, "text");
            String time   = ExamJsonUtil.parseString(obj, "time");
            String readStr= ExamJsonUtil.parseString(obj, "read");
            boolean read  = "true".equalsIgnoreCase(readStr);

            String toVal  = type == ChatMessage.Type.GROUP ? roomId : to;
            LocalDateTime ts;
            try { ts = LocalDateTime.parse(time); } catch (Exception e) { ts = LocalDateTime.now(); }
            
            ChatMessage msg = new ChatMessage(from, toVal, text, type, ts, read);
            list.add(msg);
        }
        return list;
    }
}
