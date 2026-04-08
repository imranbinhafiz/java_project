package com.example.javaproject;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.io.IOException;
import java.util.List;

/**
 * Challenge Lobby controller.
 * Shows three action cards (Create Room, Room List, My Stats) + online players.
 * Each button navigates to its own scene.
 * Invite popups use the styled overlay.
 */
public class ChallengeLobbyController {

    @FXML private Label     serverStatusLabel;
    @FXML private Label     onlineCountLabel;
    @FXML private VBox      onlineUsersBox;
    @FXML private StackPane inviteOverlay;
    @FXML private Label     inviteMessageLabel;

    private BorderPane      rootPane;
    private ChallengeClient client;
    private String          username;

    private String pendingInviteFrom;
    private String pendingInviteRoomId;

    // ── Setup ─────────────────────────────────────────────────────────

    public void setRootPane(BorderPane rootPane) {
        this.rootPane = rootPane;
    }

    @FXML
    public void initialize() {
        username = UserSession.getInstance().getUsername();
        connectToServer();
    }

    private void connectToServer() {
        String host = UserSession.getInstance().getServerHost();
        client = new ChallengeClient(host, ExamServer.PORT);
        client.setPushListener(this::handlePush);

        new Thread(() -> {
            try {
                client.connect();
                client.register(username);
                client.startListening();
                client.getUsers();
                Platform.runLater(() ->
                    serverStatusLabel.setText("Connected  ·  " + host + ":" + ExamServer.PORT));
            } catch (Exception e) {
                Platform.runLater(() ->
                    serverStatusLabel.setText("⚠  Server offline — " + e.getMessage()));
            }
        }, "ChallengeConnect").start();
    }

    // ── Push Handler ──────────────────────────────────────────────────

    private void handlePush(String msg) {
        String cmd     = ExamJsonUtil.parseCommand(msg);
        String payload = ExamJsonUtil.parsePayload(msg);
        Platform.runLater(() -> {
            switch (cmd) {
                case "CH_REGISTER_OK"   -> updateMyStats(payload);
                case "CH_USERS_LIST"    -> refreshUsers(payload);
                case "CH_INVITE_RECEIVED" -> showInvitePopup(payload);
                case "CH_JOIN_ROOM_SUCCESS",
                     "CH_CREATE_ROOM_SUCCESS" -> openRoom(payload);
                case "CH_CONNECTION_LOST" ->
                    serverStatusLabel.setText("⚠  Connection lost.");
            }
        });
    }

    private void updateMyStats(String payload) {
        // Stats are shown on the Stats scene; nothing to update here
    }

    // ── Online Users ──────────────────────────────────────────────────

    private void refreshUsers(String payload) {
        List<ChallengeUser> users = ChallengeClient.parseUsers(payload);
        onlineUsersBox.getChildren().clear();
        long onlineCount = users.stream().filter(ChallengeUser::isOnline).count();
        onlineCountLabel.setText(onlineCount + " online");

        for (ChallengeUser u : users) {
            onlineUsersBox.getChildren().add(buildUserRow(u));
        }
    }

    private HBox buildUserRow(ChallengeUser u) {
        HBox row = new HBox(10);
        row.getStyleClass().add("ch-list-row");
        row.setAlignment(Pos.CENTER_LEFT);

        Pane dot = new Pane();
        if (u.isInGame())       dot.getStyleClass().add("ch-dot-ingame");
        else if (u.isInRoom())  dot.getStyleClass().add("ch-dot-inroom");
        else if (u.isOnline())  dot.getStyleClass().add("ch-dot-online");
        else                    dot.getStyleClass().add("ch-dot-offline");

        Label name = new Label(u.getUsername()); name.getStyleClass().add("ch-user-name");
        Pane spacer = new Pane(); HBox.setHgrow(spacer, Priority.ALWAYS);

        Label badge;
        if (u.isInGame())      { badge = new Label("In Game");  badge.getStyleClass().add("ch-badge-ingame"); }
        else if (u.isInRoom()) { badge = new Label("In Room");  badge.getStyleClass().add("ch-badge-inroom"); }
        else if (u.isOnline()) { badge = new Label("Online");   badge.getStyleClass().add("ch-badge-online"); }
        else                   { badge = new Label("Offline");  badge.getStyleClass().add("ch-badge-offline"); }

        row.getChildren().addAll(dot, name, spacer, badge);
        return row;
    }

    // ── Invite Popup ──────────────────────────────────────────────────

    private void showInvitePopup(String payload) {
        pendingInviteFrom   = ChallengeClient.parseStr(payload, "from");
        pendingInviteRoomId = ChallengeClient.parseStr(payload, "roomId");
        String mode         = ChallengeClient.parseStr(payload, "mode");
        inviteMessageLabel.setText(pendingInviteFrom + " invited you to join room "
                + pendingInviteRoomId + "  [" + mode + "]");
        inviteOverlay.setVisible(true);
        AnimationUtil.fadeIn(inviteOverlay);
    }

    @FXML private void handleAcceptInvite() {
        inviteOverlay.setVisible(false);
        if (pendingInviteRoomId != null)
            client.joinRoom(username, pendingInviteRoomId, "");
    }

    @FXML private void handleDeclineInvite() {
        inviteOverlay.setVisible(false);
        if (pendingInviteFrom != null && pendingInviteRoomId != null)
            client.declineInvite(username, pendingInviteFrom, pendingInviteRoomId);
    }

    // ── Navigation ────────────────────────────────────────────────────

    @FXML
    private void handleCreateRoom() {
        navigate("fxml files/challenge_create_room.fxml", loader -> {
            ChallengeCreateRoomController ctrl = loader.getController();
            ctrl.init(client, username, rootPane, this);
        });
    }

    @FXML
    private void handleRoomList() {
        navigate("fxml files/challenge_room_list.fxml", loader -> {
            ChallengeRoomListController ctrl = loader.getController();
            ctrl.init(client, username, rootPane);
        });
    }

    @FXML
    private void handleMyStats() {
        navigate("fxml files/challenge_stats.fxml", loader -> {
            ChallengeStatsController ctrl = loader.getController();
            ctrl.init(client, username, rootPane);
        });
    }

    @FXML
    private void handleRefresh() {
        client.getUsers();
    }

    private void openRoom(String roomJson) {
        navigate("fxml files/challenge_room.fxml", loader -> {
            ChallengeRoomController ctrl = loader.getController();
            List<ChallengeRoom> rooms = ChallengeClient.parseRooms("{\"rooms\":[" + roomJson + "]}");
            ChallengeRoom room = rooms.isEmpty() ? new ChallengeRoom() : rooms.get(0);
            if (room.getRoomId() == null || room.getRoomId().isBlank())
                room.setRoomId(ChallengeClient.parseStr(roomJson, "roomId"));
            ctrl.init(room, client, username, rootPane);
        });
    }

    // ── Utility ───────────────────────────────────────────────────────

    @FunctionalInterface
    interface ControllerInit { void apply(FXMLLoader loader); }

    private void navigate(String fxmlPath, ControllerInit init) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Node node = loader.load();
            if (init != null) init.apply(loader);
            if (rootPane != null) {
                Node old = rootPane.getCenter();
                AnimationUtil.contentTransition(old, node, null);
                rootPane.setCenter(node);
            }
        } catch (IOException e) {
            System.err.println("[ChallengeLobby] Navigate error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public ChallengeClient getClient() { return client; }
}
