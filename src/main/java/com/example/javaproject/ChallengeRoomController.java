package com.example.javaproject;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.IOException;
import java.util.List;

/**
 * Room Scene — shows players, ready status, host controls.
 * Supports both SWAP_DUEL and SPEED modes.
 */
public class ChallengeRoomController {

    @FXML private Label roomIdLabel;
    @FXML private Label roomModeLabel;
    @FXML private Label hostLabel;
    @FXML private Label examLabel;
    @FXML private VBox  examRow;
    @FXML private Label statusLabel;
    @FXML private Label roundsInfoLabel;
    @FXML private VBox  playersBox;

    @FXML private Button selectExamBtn;
    @FXML private Button startBtn;
    @FXML private Button readyBtn;
    @FXML private Button inviteBtn;
    @FXML private Button leaveBtn;

    @FXML private StackPane popupOverlay;
    @FXML private VBox      popupContent;
    @FXML private StackPane inviteOverlay;
    @FXML private VBox      inviteUsersBox;
    @FXML private TextField inviteSearchField;
    @FXML private Label     inviteStatusLabel;

    private ChallengeRoom   room;
    private ChallengeClient client;
    private String          username;
    private BorderPane      rootPane;

    private boolean isReady = false;
    private List<ChallengeUser> cachedUsers;

    public void init(ChallengeRoom room, ChallengeClient client,
                     String username, BorderPane rootPane) {
        this.room     = room;
        this.client   = client;
        this.username = username;
        this.rootPane = rootPane;

        client.setPushListener(this::handlePush);
        if (room.getRoomId() != null) client.getRoomState(room.getRoomId());
        refreshUI(room);
    }

    @FXML public void initialize() {}

    private void handlePush(String msg) {
        String cmd     = ExamJsonUtil.parseCommand(msg);
        String payload = ExamJsonUtil.parsePayload(msg);
        Platform.runLater(() -> {
            switch (cmd) {
                case "CH_ROOM_UPDATE", "CH_ROOM_STATE" -> {
                    List<ChallengeRoom> rooms = ChallengeClient.parseRooms("{\"rooms\":[" + payload + "]}");
                    if (!rooms.isEmpty()) { room = rooms.get(0); refreshUI(room); }
                }
                case "CH_GAME_STARTED"     -> handleGameStarted(payload);
                case "CH_KICKED"           -> handleKicked();
                case "CH_ROOM_CLOSED"      -> handleRoomClosed();
                case "CH_LEAVE_ROOM_OK"    -> returnToLobby();
                case "CH_INVITE_RECEIVED"  -> showInviteToast(payload);
                case "CH_USERS_LIST"       -> {
                    cachedUsers = ChallengeClient.parseUsers(payload);
                    if (inviteOverlay != null && inviteOverlay.isVisible())
                        filterAndDisplayInviteUsers(inviteSearchField != null ? inviteSearchField.getText() : "");
                }
                case "CH_INVITE_SENT"      -> { if (inviteStatusLabel != null) inviteStatusLabel.setText("✓  Invite sent!"); }
                case "CH_INVITE_FAIL"      -> { if (inviteStatusLabel != null) inviteStatusLabel.setText("⚠  " + ChallengeClient.parseStr(payload, "message")); }
                case "CH_START_FAIL"       -> showStyledPopup("⚠ Cannot Start", ChallengeClient.parseStr(payload, "message"), "fas-exclamation-triangle");
            }
        });
    }

    private void refreshUI(ChallengeRoom r) {
        if (r == null) return;
        roomIdLabel.setText("Room  " + r.getRoomId());
        roomModeLabel.setText(r.getMode() != null ? r.getMode().getDisplayName() : "Unknown");
        hostLabel.setText("Host: " + r.getHost());
        statusLabel.setText(r.getStatus() != null ? r.getStatus().name() : "WAITING");

        boolean isHost = username.equals(r.getHost());
        boolean isSpeed = r.getMode() == ChallengeRoom.GameMode.SPEED;
        boolean isSwap  = r.getMode() == ChallengeRoom.GameMode.SWAP_DUEL;

        // Exam label row — only for SPEED
        if (examLabel != null) {
            String examInfo = (r.getSelectedExamTitle() != null && !r.getSelectedExamTitle().isBlank())
                    ? "📋  " + r.getSelectedExamTitle() : "No exam selected";
            examLabel.setText(examInfo);
            examLabel.getStyleClass().removeAll("ch-exam-ok", "ch-exam-none");
            examLabel.getStyleClass().add(
                    (r.getSelectedExamId() != null && !r.getSelectedExamId().isBlank())
                            ? "ch-exam-ok" : "ch-exam-none");
        }
        if (examRow != null) { examRow.setVisible(isSpeed); examRow.setManaged(isSpeed); }
        if (selectExamBtn != null) {
            selectExamBtn.setVisible(isHost && isSpeed);
            selectExamBtn.setManaged(isHost && isSpeed);
        }

        // Rounds info — only for SWAP_DUEL
        if (roundsInfoLabel != null) {
            roundsInfoLabel.setVisible(isSwap);
            roundsInfoLabel.setManaged(isSwap);
            if (isSwap) roundsInfoLabel.setText("Rounds: " + r.getTotalRounds()
                    + "   |   Exam timer: " + formatMinutes(r.getRoundTimerSeconds()) + " (set each round)");
        }

        if (startBtn != null) { startBtn.setVisible(isHost); startBtn.setManaged(isHost); }
        buildPlayersList(r);
    }

    private String formatMinutes(int seconds) {
        int minutes = Math.max(1, (seconds + 59) / 60);
        return minutes + " min";
    }

    private void buildPlayersList(ChallengeRoom r) {
        playersBox.getChildren().clear();
        List<String>  players = r.getPlayers();
        List<Boolean> ready   = r.getReadyStatus();
        for (int i = 0; i < players.size(); i++) {
            String  p      = players.get(i);
            boolean rdy    = (i < ready.size()) && ready.get(i);
            boolean isHost = p.equals(r.getHost());
            boolean isMe   = p.equals(username);

            HBox card = new HBox(10);
            card.getStyleClass().add("ch-player-card");
            if (isHost) card.getStyleClass().add("ch-player-card-host");
            card.setAlignment(Pos.CENTER_LEFT);

            Pane dot = new Pane();
            dot.getStyleClass().add(rdy ? "ch-dot-online" : "ch-dot-offline");

            Label name = new Label(p);
            name.getStyleClass().add("ch-player-name");

            if (isHost) {
                Label crown = new Label("👑 HOST");
                crown.getStyleClass().add("ch-host-badge");
                card.getChildren().addAll(dot, name, crown);
            } else {
                card.getChildren().addAll(dot, name);
            }
            Pane spacer = new Pane(); HBox.setHgrow(spacer, Priority.ALWAYS);
            card.getChildren().add(spacer);

            Label rdyLbl = new Label(rdy ? "✓ READY" : "NOT READY");
            rdyLbl.getStyleClass().add(rdy ? "ch-ready-lbl" : "ch-notready-lbl");
            card.getChildren().add(rdyLbl);

            if (username.equals(r.getHost()) && !isMe) {
                Button kick = new Button("Kick");
                kick.getStyleClass().add("ch-btn-red");
                kick.setStyle("-fx-padding:4 10 4 10; -fx-font-size:11px;");
                kick.setOnAction(e -> client.kickPlayer(username, r.getRoomId(), p));
                card.getChildren().add(kick);
            }
            playersBox.getChildren().add(card);
        }
    }

    // ── Button Handlers ───────────────────────────────────────────────

    @FXML private void handleReady() {
        isReady = !isReady;
        readyBtn.setText(isReady ? "✓  Ready!" : "Mark as Ready");
        client.setReady(username, room.getRoomId(), isReady);
    }

    @FXML private void handleInvite() {
        if (inviteOverlay == null) return;
        inviteOverlay.setVisible(true);
        AnimationUtil.fadeIn(inviteOverlay);
        if (inviteStatusLabel != null) inviteStatusLabel.setText("");
        if (inviteSearchField != null) inviteSearchField.clear();
        client.getUsers();
    }

    @FXML private void handleInviteSearch() {
        if (cachedUsers != null)
            filterAndDisplayInviteUsers(inviteSearchField != null ? inviteSearchField.getText() : "");
    }

    @FXML private void handleCloseInvite() {
        if (inviteOverlay != null) AnimationUtil.fadeOut(inviteOverlay, () -> inviteOverlay.setVisible(false));
    }

    private void filterAndDisplayInviteUsers(String query) {
        if (inviteUsersBox == null) return;
        inviteUsersBox.getChildren().clear();
        String q = (query == null) ? "" : query.toLowerCase().trim();
        boolean any = false;
        if (cachedUsers != null) {
            for (ChallengeUser u : cachedUsers) {
                if (u.getUsername().equals(username)) continue;
                if (!q.isEmpty() && !u.getUsername().toLowerCase().contains(q)) continue;
                inviteUsersBox.getChildren().add(buildInviteUserRow(u));
                any = true;
            }
        }
        if (!any) {
            Label lbl = new Label("No users found."); lbl.getStyleClass().add("ch-meta-lbl");
            inviteUsersBox.getChildren().add(lbl);
        }
    }

    private HBox buildInviteUserRow(ChallengeUser u) {
        HBox row = new HBox(10);
        row.getStyleClass().add("ch-list-row");
        row.setAlignment(Pos.CENTER_LEFT);

        Pane dot = new Pane();
        if (u.isInGame())      dot.getStyleClass().add("ch-dot-ingame");
        else if (u.isInRoom()) dot.getStyleClass().add("ch-dot-inroom");
        else if (u.isOnline()) dot.getStyleClass().add("ch-dot-online");
        else                   dot.getStyleClass().add("ch-dot-offline");

        Label name = new Label(u.getUsername()); name.getStyleClass().add("ch-user-name");
        Pane spacer = new Pane(); HBox.setHgrow(spacer, Priority.ALWAYS);

        Label badge;
        if (u.isInGame())      { badge = new Label("In Game");  badge.getStyleClass().add("ch-badge-ingame"); }
        else if (u.isInRoom()) { badge = new Label("In Room");  badge.getStyleClass().add("ch-badge-inroom"); }
        else if (u.isOnline()) { badge = new Label("Online");   badge.getStyleClass().add("ch-badge-online"); }
        else                   { badge = new Label("Offline");  badge.getStyleClass().add("ch-badge-offline"); }

        Button invBtn = new Button("Invite");
        invBtn.getStyleClass().add(u.isOnline() ? "ch-btn-action" : "ch-btn-secondary");
        invBtn.setStyle("-fx-padding:6 16 6 16; -fx-font-size:12px;");
        invBtn.setDisable(!u.isOnline() || u.isInGame());
        invBtn.setOnAction(e -> {
            client.invite(username, u.getUsername(), room.getRoomId());
            invBtn.setText("Sent ✓"); invBtn.setDisable(true);
        });
        row.getChildren().addAll(dot, name, spacer, badge, invBtn);
        return row;
    }

    @FXML private void handleSelectExam() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("fxml files/challenge_select_exam.fxml"));
            Node node = loader.load();
            ChallengeSelectExamController ctrl = loader.getController();
            ctrl.init(client, username, room.getRoomId(), rootPane, this);
            if (rootPane != null) { AnimationUtil.contentTransition(rootPane.getCenter(), node, null); rootPane.setCenter(node); }
        } catch (IOException e) { e.printStackTrace(); }
    }

    @FXML private void handleStart() { client.startGame(username, room.getRoomId()); }

    @FXML private void handleLeave() { client.leaveRoom(username, room.getRoomId()); }

    // ── Game Start ────────────────────────────────────────────────────

    private void handleGameStarted(String payload) {
        String mode   = ChallengeClient.parseStr(payload, "mode");
        String roomId = ChallengeClient.parseStr(payload, "roomId");
        int    round  = ChallengeClient.parseInt(payload, "currentRound");

        if ("SWAP_DUEL".equals(mode)) {
            navigateToRoundIntro(roomId, round, ChallengeClient.parseInt(payload, "totalRounds"));
        } else {
            // SPEED — go to speed exam
            navigateToSpeedExam(payload);
        }
    }

    private void handleKicked() {
        showStyledPopup("Kicked", "You were kicked from the room.", "fas-user-slash", this::returnToLobby);
    }

    private void handleRoomClosed() {
        showStyledPopup("Room Closed", "The host has left. The room has been closed.", "fas-door-closed", this::returnToLobby);
    }

    private void showInviteToast(String payload) {
        String from = ChallengeClient.parseStr(payload, "from");
        showStyledPopup("📩 Invite Received", from + " invited you to another room.", "fas-envelope");
    }

    // ── Navigation ────────────────────────────────────────────────────

    private void navigateToRoundIntro(String roomId, int round, int totalRounds) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("fxml files/challenge_round_intro.fxml"));
            Node node = loader.load();
            ChallengeRoundIntroController ctrl = loader.getController();
            ctrl.init(client, username, roomId, round, totalRounds, room.getRoundTimerSeconds(), rootPane);
            if (rootPane != null) { AnimationUtil.contentTransition(rootPane.getCenter(), node, null); rootPane.setCenter(node); }
        } catch (IOException e) { e.printStackTrace(); }
    }

    private void navigateToSpeedExam(String payload) {
        String examId    = ChallengeClient.parseStr(payload, "selectedExamId");
        String roomId    = ChallengeClient.parseStr(payload, "roomId");
        String examTitle = ChallengeClient.parseStr(payload, "selectedExamTitle");
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("fxml files/challenge_speed_exam.fxml"));
            Node node = loader.load();
            ChallengeSpeedExamController ctrl = loader.getController();
            ctrl.init(client, username, roomId, examId, examTitle, rootPane);
            if (rootPane != null) { AnimationUtil.contentTransition(rootPane.getCenter(), node, null); rootPane.setCenter(node); }
        } catch (IOException e) { e.printStackTrace(); }
    }

    private void returnToLobby() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("fxml files/challenge_lobby.fxml"));
            Node node = loader.load();
            ChallengeLobbyController ctrl = loader.getController();
            ctrl.setRootPane(rootPane);
            if (rootPane != null) { AnimationUtil.contentTransition(rootPane.getCenter(), node, null); rootPane.setCenter(node); }
        } catch (IOException e) { e.printStackTrace(); }
    }

    public void restoreRoomView() {
        client.setPushListener(this::handlePush);
        client.getRoomState(room.getRoomId());
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("fxml files/challenge_room.fxml"));
            Node node = loader.load();
            ChallengeRoomController ctrl = loader.getController();
            ctrl.init(room, client, username, rootPane);
            if (rootPane != null) { AnimationUtil.contentTransition(rootPane.getCenter(), node, null); rootPane.setCenter(node); }
        } catch (IOException e) { e.printStackTrace(); }
    }

    // ── Styled Popup ──────────────────────────────────────────────────

    private void showStyledPopup(String title, String message, String icon) {
        showStyledPopup(title, message, icon, null);
    }

    private void showStyledPopup(String title, String message, String icon, Runnable onClose) {
        if (popupOverlay == null || popupContent == null) return;
        popupContent.getChildren().clear();

        HBox header = new HBox(10); header.getStyleClass().add("ch-popup-header"); header.setAlignment(Pos.CENTER_LEFT);
        if (icon != null && !icon.isEmpty()) {
            FontIcon fi = new FontIcon(icon); fi.setIconSize(18); fi.setIconColor(javafx.scene.paint.Color.WHITE);
            header.getChildren().add(fi);
        }
        Label titleLbl = new Label(title); titleLbl.getStyleClass().add("ch-popup-title");
        header.getChildren().add(titleLbl);

        VBox body = new VBox(6); body.getStyleClass().add("ch-popup-body");
        Label msgLbl = new Label(message); msgLbl.getStyleClass().add("ch-popup-msg"); msgLbl.setWrapText(true);
        body.getChildren().add(msgLbl);

        HBox footer = new HBox(10); footer.getStyleClass().add("ch-popup-footer"); footer.setAlignment(Pos.CENTER_RIGHT);
        Button okBtn = new Button("OK"); okBtn.getStyleClass().add("ch-btn-primary"); okBtn.setStyle("-fx-padding:8 24 8 24;");
        okBtn.setOnAction(e -> {
            AnimationUtil.fadeOut(popupOverlay, () -> popupOverlay.setVisible(false));
            if (onClose != null) onClose.run();
        });
        footer.getChildren().add(okBtn);

        popupContent.getChildren().addAll(header, body, footer);
        popupOverlay.setVisible(true);
        AnimationUtil.fadeIn(popupOverlay);
    }

    public ChallengeRoom getRoom()     { return room; }
    public String        getUsername() { return username; }
}
