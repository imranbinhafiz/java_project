package com.example.javaproject;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.*;
import java.util.Base64;

public class MessengerController {

    // ── FXML ──────────────────────────────────────────────────────────
    @FXML private VBox       userListPane;
    @FXML private VBox       roomListPane;
    @FXML private VBox       messagesContainer;
    @FXML private ScrollPane messagesScroll;
    @FXML private TextField  messageInput;
    @FXML private Button     sendButton;
    @FXML private Button     attachButton;
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
    @FXML private HBox       pendingAttachmentBar;
    @FXML private Label      pendingAttachmentLabel;
    @FXML private Button     clearAttachmentBtn;

    // ── State ─────────────────────────────────────────────────────────
    private MessengerClient client;
    private final String    myUsername = UserSession.getInstance().getUsername();

    private String  currentTarget;
    private boolean currentIsRoom;
    private String  currentRoomHost;
    private String  searchQuery = "";
    private PendingAttachment pendingAttachment;

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

    // ── File / Image sending constants ────────────────────────────────
    /** Prefix that marks a message payload as a Base64-encoded image. */
    private static final String IMAGE_PREFIX = "[IMG:]";
    /** Prefix that marks a message payload as a Base64-encoded generic file. */
    private static final String FILE_PREFIX  = "[FILE:]";
    /** Prefix that marks a message payload as a staged attachment with optional text. */
    private static final String ATTACHMENT_PREFIX = "[ATTACH:]";
    /** Separator between the filename and the Base64 data within a file payload. */
    private static final String META_SEP     = "[:META:]";
    private static final String ATTACHMENT_IMAGE_KIND = "IMG";
    private static final String ATTACHMENT_FILE_KIND  = "FILE";
    /** Maximum file size allowed for sending (5 MB). */
    private static final long   MAX_FILE_BYTES = 5L * 1024 * 1024;
    /** Maximum image width/height displayed inline (px). */
    private static final double MAX_IMAGE_DISPLAY_SIZE = 280.0;

    private static final Set<String> IMAGE_EXTENSIONS = new HashSet<>(Arrays.asList(
        "png", "jpg", "jpeg", "gif", "bmp", "webp"
    ));

    private static final String[] AVATAR_COLORS = {
        "#6366f1","#8b5cf6","#ec4899","#f59e0b","#10b981",
        "#3b82f6","#ef4444","#14b8a6","#f97316","#a855f7"
    };

    private static final class PendingAttachment {
        private final String fileName;
        private final String dataBase64;
        private final boolean image;

        private PendingAttachment(String fileName, String dataBase64, boolean image) {
            this.fileName = fileName;
            this.dataBase64 = dataBase64;
            this.image = image;
        }
    }

    private static final class ParsedAttachment {
        private final String fileName;
        private final String caption;
        private final String dataBase64;
        private final boolean image;

        private ParsedAttachment(String fileName, String caption, String dataBase64, boolean image) {
            this.fileName = fileName;
            this.caption = caption;
            this.dataBase64 = dataBase64;
            this.image = image;
        }
    }

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
        rebuildUserList(new ArrayList<>(onlineMap.keySet()));
    }

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
        String time = ExamJsonUtil.parseString(payload, "time");

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
        if (currentTarget == null) return;

        String text = messageInput == null ? "" : messageInput.getText().trim();
        PendingAttachment attachment = pendingAttachment;
        if (text.isBlank() && attachment == null) return;

        String payload = attachment == null ? text : buildAttachmentPayload(attachment, text);

        if (messageInput != null) {
            messageInput.clear();
        }
        if (charCounter != null) charCounter.setText("");
        if (currentIsRoom) client.sendRoomMessage(myUsername, currentTarget, payload);
        else               client.sendDM(myUsername, currentTarget, payload);
        clearPendingAttachment();
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
        String rawQuery = searchField == null ? "" : searchField.getText();
        searchQuery = rawQuery == null ? "" : rawQuery.trim().toLowerCase(Locale.ROOT);
        rebuildUserList(getAllKnownUsers());
        rebuildRoomList();
    }

    @FXML
    private void handleClearAttachment() {
        clearPendingAttachment();
    }

    // ── File / Image Attachment ───────────────────────────────────────

    /**
     * Opens a FileChooser so the user can pick an image or any file to send.
     * Images are detected by extension and sent with IMAGE_PREFIX so the
     * receiver renders them inline.  All other files are sent with FILE_PREFIX
     * together with their original filename so the receiver can offer a save.
     *
     * The binary content is Base64-encoded and packed into the normal text
     * message field — no server-side changes are needed.
     */
    @FXML
    private void handleAttachFile() {
        if (currentTarget == null) {
            showToast("Select a conversation first.", false);
            return;
        }
        if (client == null || !client.isConnected()) {
            showToast("Not connected to server.", false);
            return;
        }

        Stage owner = (attachButton != null && attachButton.getScene() != null)
                ? (Stage) attachButton.getScene().getWindow()
                : null;

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select a file to send");
        chooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp", "*.webp"),
            new FileChooser.ExtensionFilter("All Files", "*.*")
        );

        File selected = chooser.showOpenDialog(owner);
        if (selected == null) return; // user cancelled

        // Size guard
        if (selected.length() > MAX_FILE_BYTES) {
            showToast("File too large (max 5 MB).", false);
            return;
        }

        // Run encoding off the FX thread so UI stays responsive
        new Thread(() -> {
            try {
                byte[] bytes = Files.readAllBytes(selected.toPath());
                String b64   = Base64.getEncoder().encodeToString(bytes);
                String ext   = getExtension(selected.getName()).toLowerCase(Locale.ROOT);
                boolean isImage = IMAGE_EXTENSIONS.contains(ext);
                PendingAttachment attachment = new PendingAttachment(selected.getName(), b64, isImage);

                Platform.runLater(() -> {
                    setPendingAttachment(attachment);
                    if (messageInput != null) {
                        messageInput.requestFocus();
                    }
                });

            } catch (Exception ex) {
                Platform.runLater(() -> showToast("Failed to attach file: " + ex.getMessage(), false));
            }
        }, "FileAttachmentLoader").start();
    }

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

        VBox root = new VBox(0);
        root.setStyle("-fx-background-color:#0f1629;");
        root.setPrefWidth(400);

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
                if (client != null) client.addMember(myUsername, roomId, m);
                addField.clear();
                showFieldError(addStatus, "Invite sent.");
                addStatus.setStyle("-fx-font-size:11px;-fx-text-fill:#4ade80;");
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

    private HBox buildMemberRow(String member, boolean isHost, String roomId,
                                 javafx.stage.Stage dlgStage, VBox listBox, List<String> memberList) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(9, 12, 9, 12));
        row.setStyle("-fx-background-color:rgba(255,255,255,0.03);-fx-background-radius:12px;-fx-border-color:rgba(99,102,241,0.12);-fx-border-width:1;-fx-border-radius:12px;");

        StackPane avatar = buildAvatar(member, getAvatarColor(member));
        avatar.setMinSize(34, 34); avatar.setMaxSize(34, 34);

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

        if (isHost && !member.equals(myUsername)) {
            Button removeBtn = new Button("Remove");
            removeBtn.setStyle("-fx-font-size:11px;-fx-font-weight:600;-fx-text-fill:#fca5a5;-fx-background-color:rgba(239,68,68,0.1);-fx-background-radius:8px;-fx-border-color:rgba(239,68,68,0.25);-fx-border-width:1;-fx-border-radius:8px;-fx-padding:5 11 5 11;-fx-cursor:hand;");
            removeBtn.setOnMouseEntered(e -> removeBtn.setStyle("-fx-font-size:11px;-fx-font-weight:600;-fx-text-fill:#fecaca;-fx-background-color:rgba(239,68,68,0.22);-fx-background-radius:8px;-fx-border-color:rgba(239,68,68,0.4);-fx-border-width:1;-fx-border-radius:8px;-fx-padding:5 11 5 11;-fx-cursor:hand;"));
            removeBtn.setOnMouseExited(e -> removeBtn.setStyle("-fx-font-size:11px;-fx-font-weight:600;-fx-text-fill:#fca5a5;-fx-background-color:rgba(239,68,68,0.1);-fx-background-radius:8px;-fx-border-color:rgba(239,68,68,0.25);-fx-border-width:1;-fx-border-radius:8px;-fx-padding:5 11 5 11;-fx-cursor:hand;"));
            removeBtn.setOnAction(e -> {
                if (client != null) client.removeMember(myUsername, roomId, member);
                removeBtn.setDisable(true);
                removeBtn.setText("Removing...");
            });
            row.getChildren().add(removeBtn);
        }

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
        clearPendingAttachment();
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
        clearPendingAttachment();
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
            String txt = last.getText();
            previewText = buildConversationPreview(senderLabel, txt, 32);
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
            String txt = last.getText();
            previewText = buildConversationPreview(senderLabel, txt, 26);
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
            List<ChatMessage> cache = currentIsRoom ? roomCache.get(currentTarget) : dmCache.get(currentTarget);
            if (cache != null && cache.size() > 1) {
                ChatMessage prev = cache.get(cache.size() - 2);
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

    // ── Bubble builder ────────────────────────────────────────────────
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

        String text = msg.getText();
        ParsedAttachment parsedAttachment = parseAttachmentPayload(text);

        // ── Image message ──────────────────────────────────────────
        if (parsedAttachment != null) {
            if (parsedAttachment.caption != null && !parsedAttachment.caption.isBlank()) {
                Label textLbl = new Label(parsedAttachment.caption);
                textLbl.getStyleClass().add("bubble-text");
                textLbl.setWrapText(true);
                textLbl.setMaxWidth(420);
                bubble.getChildren().add(textLbl);
            }
            if (parsedAttachment.image) {
                bubble.getChildren().add(buildImageNode(parsedAttachment.dataBase64, isMine));
            } else {
                bubble.getChildren().add(buildFileNode(parsedAttachment.fileName, parsedAttachment.dataBase64));
            }
        } else if (text != null && text.startsWith(IMAGE_PREFIX)) {
            Node imageNode = buildImageNode(text.substring(IMAGE_PREFIX.length()), isMine);
            bubble.getChildren().add(imageNode);

        // ── File message ───────────────────────────────────────────
        } else if (text != null && text.startsWith(FILE_PREFIX)) {
            Node fileNode = buildFileNode(text, isMine);
            bubble.getChildren().add(fileNode);

        // ── Plain text message ─────────────────────────────────────
        } else {
            Label textLbl = new Label(text);
            textLbl.getStyleClass().add("bubble-text");
            textLbl.setWrapText(true);
            textLbl.setMaxWidth(420);
            bubble.getChildren().add(textLbl);
        }

        Label timeLbl = new Label(msg.getFormattedTime());
        timeLbl.getStyleClass().add("bubble-time");
        HBox timeRow = new HBox();
        timeRow.setAlignment(isMine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        timeRow.getChildren().add(timeLbl);
        bubble.getChildren().add(timeRow);

        // Delete button (own messages only)
        Button delBtn = new Button("🗑");
        delBtn.setStyle("-fx-font-size:13px;-fx-background-color:transparent;-fx-cursor:hand;-fx-padding:3 5 3 5;-fx-background-radius:6px;-fx-opacity:0;-fx-text-fill:#f87171;");
        delBtn.setFocusTraversable(false);
        final HBox fRow = row;
        delBtn.setOnAction(e -> deleteMessage(fRow, msg));
        delBtn.setOnMouseEntered(e -> delBtn.setStyle("-fx-font-size:13px;-fx-background-color:rgba(239,68,68,0.15);-fx-cursor:hand;-fx-padding:3 5 3 5;-fx-background-radius:6px;-fx-opacity:1;-fx-text-fill:#f87171;"));
        delBtn.setOnMouseExited(e -> {
            if (!fRow.isHover()) delBtn.setStyle("-fx-font-size:13px;-fx-background-color:transparent;-fx-cursor:hand;-fx-padding:3 5 3 5;-fx-background-radius:6px;-fx-opacity:0;-fx-text-fill:#f87171;");
        });

        if (isMine) {
            row.getChildren().addAll(delBtn, bubble);
            row.setOnMouseEntered(e -> delBtn.setStyle("-fx-font-size:13px;-fx-background-color:transparent;-fx-cursor:hand;-fx-padding:3 5 3 5;-fx-background-radius:6px;-fx-opacity:1;-fx-text-fill:#f87171;"));
            row.setOnMouseExited(e -> delBtn.setStyle("-fx-font-size:13px;-fx-background-color:transparent;-fx-cursor:hand;-fx-padding:3 5 3 5;-fx-background-radius:6px;-fx-opacity:0;-fx-text-fill:#f87171;"));
        } else {
            row.getChildren().add(bubble);
        }

        return row;
    }

    /**
     * Builds an inline image view node from a Base64-encoded image string.
     * Clicking the image opens a full-size view in a dialog.
     */
    private Node buildImageNode(String base64Data, boolean isMine) {
        try {
            byte[] imgBytes = Base64.getDecoder().decode(base64Data);
            Image image = new Image(new ByteArrayInputStream(imgBytes));
            ImageView iv = new ImageView(image);
            iv.setPreserveRatio(true);
            // Scale down to fit bubble width while keeping aspect ratio
            double w = image.getWidth();
            double h = image.getHeight();
            if (w > MAX_IMAGE_DISPLAY_SIZE || h > MAX_IMAGE_DISPLAY_SIZE) {
                if (w >= h) {
                    iv.setFitWidth(MAX_IMAGE_DISPLAY_SIZE);
                } else {
                    iv.setFitHeight(MAX_IMAGE_DISPLAY_SIZE);
                }
            }
            iv.getStyleClass().add("bubble-image");
            iv.setStyle("-fx-background-radius:12px;-fx-cursor:hand;");

            // Tooltip hint
            Tooltip.install(iv, new Tooltip("Click to view full size"));

            // Click → open full-size dialog
            iv.setOnMouseClicked(e -> showImageDialog(imgBytes, image));

            return iv;
        } catch (Exception ex) {
            // Fallback if decoding fails
            Label err = new Label("⚠ Could not load image");
            err.setStyle("-fx-text-fill:#f87171;-fx-font-size:12px;");
            return err;
        }
    }

    /**
     * Opens a pop-up dialog showing the image at full (or window-capped) size.
     */
    private void showImageDialog(byte[] imgBytes, Image image) {
        Stage dialog = new Stage();
        dialog.setTitle("Image");
        dialog.initOwner(messagesScroll.getScene().getWindow());
        dialog.initModality(javafx.stage.Modality.NONE);

        ImageView fullIv = new ImageView(image);
        fullIv.setPreserveRatio(true);
        double maxW = Math.min(image.getWidth(),  900);
        double maxH = Math.min(image.getHeight(), 700);
        fullIv.setFitWidth(maxW);
        fullIv.setFitHeight(maxH);

        // Save button
        Button saveBtn = new Button("💾  Save Image");
        saveBtn.setStyle("-fx-font-size:13px;-fx-font-weight:600;-fx-text-fill:white;-fx-background-color:#6366f1;-fx-background-radius:10px;-fx-padding:9 20 9 20;-fx-cursor:hand;");
        saveBtn.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("Save Image");
            fc.setInitialFileName("image.png");
            fc.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("PNG Image", "*.png"),
                new FileChooser.ExtensionFilter("All Files", "*.*")
            );
            File dest = fc.showSaveDialog(dialog);
            if (dest != null) {
                try {
                    Files.write(dest.toPath(), imgBytes);
                    showToast("Image saved!", true);
                } catch (Exception ex) {
                    showToast("Failed to save: " + ex.getMessage(), false);
                }
            }
        });

        VBox root = new VBox(12, fullIv, saveBtn);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(16));
        root.setStyle("-fx-background-color:#0a0f1c;");

        javafx.scene.Scene scene = new javafx.scene.Scene(root);
        dialog.setScene(scene);
        dialog.setResizable(true);
        dialog.show();
    }

    /**
     * Builds a file pill widget showing the file name and a download/save button.
     * The payload format is:  [FILE:]<filename>[:META:]<base64>
     */
    private Node buildFileNode(String payload, boolean isMine) {
        String inner   = payload.substring(FILE_PREFIX.length());
        int    sep     = inner.indexOf(META_SEP);
        String filename = sep >= 0 ? inner.substring(0, sep) : "file";
        String b64Data  = sep >= 0 ? inner.substring(sep + META_SEP.length()) : "";
        return buildFileNode(filename, b64Data);
    }

    private Node buildFileNode(String filename, String b64Data) {
        VBox pill = new VBox(4);
        pill.getStyleClass().add("bubble-file-pill");
        pill.setMaxWidth(260);

        HBox iconRow = new HBox(8);
        iconRow.setAlignment(Pos.CENTER_LEFT);
        Label icon = new Label(getFileIcon(filename));
        icon.setStyle("-fx-font-size:22px;");
        VBox nameBox = new VBox(2);
        Label nameLbl = new Label(filename);
        nameLbl.getStyleClass().add("bubble-file-name");
        // Estimate size from base64 length
        long approxBytes = (long)(b64Data.length() * 0.75);
        Label sizeLbl = new Label(formatFileSize(approxBytes));
        sizeLbl.getStyleClass().add("bubble-file-size");
        nameBox.getChildren().addAll(nameLbl, sizeLbl);
        iconRow.getChildren().addAll(icon, nameBox);
        pill.getChildren().add(iconRow);

        Button saveBtn = new Button("⬇  Save file");
        saveBtn.setStyle("-fx-font-size:11.5px;-fx-font-weight:600;-fx-text-fill:#818cf8;-fx-background-color:rgba(99,102,241,0.1);-fx-background-radius:8px;-fx-padding:6 14 6 14;-fx-cursor:hand;-fx-border-color:rgba(99,102,241,0.25);-fx-border-width:1;-fx-border-radius:8px;");
        saveBtn.setOnMouseEntered(ev -> saveBtn.setStyle("-fx-font-size:11.5px;-fx-font-weight:600;-fx-text-fill:#c7d2fe;-fx-background-color:rgba(99,102,241,0.2);-fx-background-radius:8px;-fx-padding:6 14 6 14;-fx-cursor:hand;-fx-border-color:rgba(99,102,241,0.4);-fx-border-width:1;-fx-border-radius:8px;"));
        saveBtn.setOnMouseExited(ev -> saveBtn.setStyle("-fx-font-size:11.5px;-fx-font-weight:600;-fx-text-fill:#818cf8;-fx-background-color:rgba(99,102,241,0.1);-fx-background-radius:8px;-fx-padding:6 14 6 14;-fx-cursor:hand;-fx-border-color:rgba(99,102,241,0.25);-fx-border-width:1;-fx-border-radius:8px;"));

        final String finalB64  = b64Data;
        final String finalName = filename;
        saveBtn.setOnAction(ev -> saveFileFromBase64(finalB64, finalName));
        pill.getChildren().add(saveBtn);

        return pill;
    }

    /**
     * Decodes base64 data and saves it via a FileChooser dialog.
     */
    private void saveFileFromBase64(String b64Data, String suggestedName) {
        if (b64Data == null || b64Data.isBlank()) {
            showToast("No file data to save.", false);
            return;
        }
        Stage owner = (Stage) messagesScroll.getScene().getWindow();
        FileChooser fc = new FileChooser();
        fc.setTitle("Save File");
        fc.setInitialFileName(suggestedName);
        File dest = fc.showSaveDialog(owner);
        if (dest == null) return;
        new Thread(() -> {
            try {
                byte[] data = Base64.getDecoder().decode(b64Data);
                Files.write(dest.toPath(), data);
                Platform.runLater(() -> showToast("Saved: " + dest.getName(), true));
            } catch (Exception ex) {
                Platform.runLater(() -> showToast("Save failed: " + ex.getMessage(), false));
            }
        }, "FileSaver").start();
    }

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
                messagesContainer.getChildren().remove(row);
                if (currentIsRoom) {
                    List<ChatMessage> cache = roomCache.get(currentTarget);
                    if (cache != null) cache.removeIf(m -> m == msg);
                    if (client != null)
                        client.deleteGroupMessage(currentTarget, myUsername, msg.getFormattedTimeRaw());
                } else {
                    List<ChatMessage> cache = dmCache.get(currentTarget);
                    if (cache != null) cache.removeIf(m -> m == msg);
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

    // ── Toast notification ────────────────────────────────────────────
    /**
     * Shows a small non-blocking toast label inside the chat area (or no-sel pane).
     * @param success true = green success style, false = red error style
     */
    private void showToast(String message, boolean success) {
        String color  = success ? "#4ade80" : "#f87171";
        String bgColor = success ? "rgba(74,222,128,0.1)" : "rgba(248,113,113,0.1)";
        String border  = success ? "rgba(74,222,128,0.3)" : "rgba(248,113,113,0.3)";
        Label toast = new Label(message);
        toast.setStyle("-fx-background-color:" + bgColor + ";-fx-text-fill:" + color
                + ";-fx-font-size:12px;-fx-background-radius:12px;-fx-padding:9 18 9 18;"
                + "-fx-border-color:" + border + ";-fx-border-width:1;-fx-border-radius:12px;");
        toast.setWrapText(true);
        toast.setMaxWidth(320);

        VBox target = chatPane != null && chatPane.isVisible() ? chatPane : noSelectionPane;
        if (target == null) return;
        target.getChildren().add(toast);
        new Thread(() -> {
            try { Thread.sleep(3000); } catch (Exception ignored) {}
            Platform.runLater(() -> target.getChildren().remove(toast));
        }, "ToastRemover").start();
    }

    // ── Invite toast ──────────────────────────────────────────────────
    private void showInviteToast(String roomName, String invitedBy) {
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
        clearPendingAttachment();
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
        sendButton.setDisable(currentTarget == null || (!hasText && pendingAttachment == null));
    }

    // ── File helpers ──────────────────────────────────────────────────
    private String getExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return (dot >= 0 && dot < filename.length() - 1) ? filename.substring(dot + 1) : "";
    }

    private String extractFileName(String payload) {
        String inner = payload.substring(FILE_PREFIX.length());
        int sep = inner.indexOf(META_SEP);
        return sep >= 0 ? inner.substring(0, sep) : "file";
    }

    private void setPendingAttachment(PendingAttachment attachment) {
        pendingAttachment = attachment;
        refreshPendingAttachmentUi();
        updateSendBtnState();
    }

    private void clearPendingAttachment() {
        pendingAttachment = null;
        refreshPendingAttachmentUi();
        updateSendBtnState();
    }

    private void refreshPendingAttachmentUi() {
        boolean hasAttachment = pendingAttachment != null;
        if (pendingAttachmentBar != null) {
            pendingAttachmentBar.setVisible(hasAttachment);
            pendingAttachmentBar.setManaged(hasAttachment);
        }
        if (pendingAttachmentLabel != null) {
            pendingAttachmentLabel.setText(hasAttachment
                ? (pendingAttachment.image ? "Image attached: " : "File attached: ") + pendingAttachment.fileName
                : "");
        }
        if (attachButton != null) {
            if (hasAttachment) {
                if (!attachButton.getStyleClass().contains("attach-btn-active")) {
                    attachButton.getStyleClass().add("attach-btn-active");
                }
            } else {
                attachButton.getStyleClass().remove("attach-btn-active");
            }
        }
    }

    private String buildAttachmentPayload(PendingAttachment attachment, String caption) {
        String kind = attachment.image ? ATTACHMENT_IMAGE_KIND : ATTACHMENT_FILE_KIND;
        return ATTACHMENT_PREFIX
            + kind + META_SEP
            + encodeAttachmentMeta(attachment.fileName)
            + META_SEP
            + encodeAttachmentMeta(caption == null ? "" : caption)
            + META_SEP
            + attachment.dataBase64;
    }

    private ParsedAttachment parseAttachmentPayload(String payload) {
        if (payload == null || !payload.startsWith(ATTACHMENT_PREFIX)) return null;
        List<String> parts = splitAttachmentParts(payload.substring(ATTACHMENT_PREFIX.length()), 4);
        if (parts.size() != 4) return null;

        boolean isImage;
        if (ATTACHMENT_IMAGE_KIND.equals(parts.get(0))) {
            isImage = true;
        } else if (ATTACHMENT_FILE_KIND.equals(parts.get(0))) {
            isImage = false;
        } else {
            return null;
        }

        try {
            String fileName = decodeAttachmentMeta(parts.get(1));
            String caption = decodeAttachmentMeta(parts.get(2));
            return new ParsedAttachment(fileName, caption, parts.get(3), isImage);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private List<String> splitAttachmentParts(String value, int expectedParts) {
        if (value == null) return List.of();
        List<String> parts = new ArrayList<>(expectedParts);
        int start = 0;
        for (int i = 0; i < expectedParts - 1; i++) {
            int idx = value.indexOf(META_SEP, start);
            if (idx < 0) return List.of();
            parts.add(value.substring(start, idx));
            start = idx + META_SEP.length();
        }
        parts.add(value.substring(start));
        return parts;
    }

    private String encodeAttachmentMeta(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String decodeAttachmentMeta(String encoded) {
        return new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
    }

    private String buildConversationPreview(String senderLabel, String text, int maxLen) {
        if (text == null) return "";

        ParsedAttachment attachment = parseAttachmentPayload(text);
        if (attachment != null) {
            return senderLabel + ": " + truncate(buildAttachmentPreviewBody(attachment), maxLen);
        }
        if (text.startsWith(IMAGE_PREFIX)) {
            return senderLabel + ": 🖼 Image";
        }
        if (text.startsWith(FILE_PREFIX)) {
            return senderLabel + ": 📎 " + extractFileName(text);
        }
        return senderLabel + ": " + truncate(text, maxLen);
    }

    private String buildAttachmentPreviewBody(ParsedAttachment attachment) {
        String lead = attachment.image ? "🖼 Image" : "📎 " + attachment.fileName;
        if (attachment.caption == null || attachment.caption.isBlank()) {
            return lead;
        }
        return lead + " · " + attachment.caption;
    }

    private String getFileIcon(String filename) {
        String ext = getExtension(filename).toLowerCase();
        return switch (ext) {
            case "pdf"             -> "📄";
            case "doc", "docx"     -> "📝";
            case "xls", "xlsx"     -> "📊";
            case "ppt", "pptx"     -> "📋";
            case "zip", "rar", "7z"-> "🗜";
            case "mp3", "wav", "ogg"-> "🎵";
            case "mp4", "avi", "mov"-> "🎬";
            case "txt"             -> "📃";
            default                -> "📎";
        };
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024)       return bytes + " B";
        if (bytes < 1024*1024)  return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }

    // ── General helpers ───────────────────────────────────────────────
    private String getAvatarColor(String name) {
        if (name == null || name.isEmpty()) return AVATAR_COLORS[0];
        return AVATAR_COLORS[Math.abs(name.hashCode()) % AVATAR_COLORS.length];
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) + "…" : s;
    }

    private List<String> getAllKnownUsers() {
        Set<String> all = new LinkedHashSet<>(onlineMap.keySet());
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
