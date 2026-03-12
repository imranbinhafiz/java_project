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
 * Room List scene — shows all active waiting rooms.
 */
public class ChallengeRoomListController {

    @FXML private VBox  roomsBox;
    @FXML private Label roomCountLabel;
    @FXML private Label emptyLabel;

    private ChallengeClient client;
    private String          username;
    private BorderPane      rootPane;

    // ── Init ──────────────────────────────────────────────────────────

    public void init(ChallengeClient client, String username, BorderPane rootPane) {
        this.client   = client;
        this.username = username;
        this.rootPane = rootPane;

        client.setPushListener(msg -> {
            String cmd     = ExamJsonUtil.parseCommand(msg);
            String payload = ExamJsonUtil.parsePayload(msg);
            Platform.runLater(() -> {
                switch (cmd) {
                    case "CH_ROOMS_LIST"                    -> refreshRooms(payload);
                    case "CH_JOIN_ROOM_SUCCESS",
                         "CH_CREATE_ROOM_SUCCESS"           -> openRoom(payload);
                    case "CH_JOIN_ROOM_FAIL"                ->
                        showAlert("Cannot Join", ChallengeClient.parseStr(payload, "message"));
                    case "CH_INVITE_RECEIVED"               -> handleIncomingInvite(payload);
                }
            });
        });
        client.getRooms();
    }

    @FXML public void initialize() {}

    // ── Refresh ───────────────────────────────────────────────────────

    private void refreshRooms(String payload) {
        List<ChallengeRoom> rooms = ChallengeClient.parseRooms(payload);
        roomsBox.getChildren().clear();

        if (emptyLabel != null) emptyLabel.setVisible(false);

        if (rooms.isEmpty()) {
            Label lbl = new Label("No active rooms — create one to get started!");
            lbl.getStyleClass().add("ch-meta-lbl");
            lbl.setStyle("-fx-padding:16 0 0 4;");
            roomsBox.getChildren().add(lbl);
            roomCountLabel.setText("0 rooms");
            return;
        }
        roomCountLabel.setText(rooms.size() + " room" + (rooms.size() == 1 ? "" : "s"));
        for (ChallengeRoom r : rooms) roomsBox.getChildren().add(buildRow(r));
    }

    private HBox buildRow(ChallengeRoom r) {
        HBox row = new HBox(12);
        row.getStyleClass().add("ch-list-row");
        row.setAlignment(Pos.CENTER_LEFT);

        Label id   = new Label(r.getRoomId());   id.getStyleClass().add("ch-room-id"); id.setPrefWidth(100);
        Label mode = new Label(r.getMode() != null ? r.getMode().getDisplayName() : "—");
        mode.getStyleClass().add("ch-room-mode"); mode.setPrefWidth(160);
        Label cnt  = new Label(r.getPlayerCount() + "/" + r.getMaxPlayers());
        cnt.getStyleClass().add("ch-room-count"); cnt.setPrefWidth(80);
        Label host = new Label(r.getHost()); host.getStyleClass().add("ch-room-host"); host.setPrefWidth(140);

        Label status = new Label(r.getStatus() != null ? r.getStatus().name() : "WAITING");
        status.getStyleClass().add("ch-badge-waiting"); status.setPrefWidth(90);

        Pane spacer = new Pane(); HBox.setHgrow(spacer, Priority.ALWAYS);

        Button joinBtn = new Button("Join");
        joinBtn.getStyleClass().add("ch-btn-primary");
        joinBtn.setStyle("-fx-font-size:12px; -fx-padding:7 18 7 18;");
        boolean full = r.isFull() || r.getStatus() != ChallengeRoom.RoomStatus.WAITING;
        joinBtn.setDisable(full);
        joinBtn.setOnAction(e -> joinRoom(r));

        row.getChildren().addAll(id, mode, cnt, host, status, spacer, joinBtn);
        return row;
    }

    private void joinRoom(ChallengeRoom r) {
        if (r.getType() == ChallengeRoom.RoomType.PRIVATE) {
            TextInputDialog dlg = new TextInputDialog();
            dlg.setTitle("Password Required");
            dlg.setHeaderText("Room " + r.getRoomId() + " is password-protected.");
            dlg.setContentText("Enter password:");
            // Apply alert css
            try {
                dlg.getDialogPane().getStylesheets().add(
                        getClass().getResource("css/alert.css").toExternalForm());
            } catch (Exception ignored) {}
            dlg.showAndWait().ifPresent(pw -> client.joinRoom(username, r.getRoomId(), pw));
        } else {
            client.joinRoom(username, r.getRoomId(), "");
        }
    }

    private void openRoom(String roomJson) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("fxml files/challenge_room.fxml"));
            Node node = loader.load();
            ChallengeRoomController ctrl = loader.getController();
            List<ChallengeRoom> rooms = ChallengeClient.parseRooms("{\"rooms\":[" + roomJson + "]}");
            ChallengeRoom room = rooms.isEmpty() ? new ChallengeRoom() : rooms.get(0);
            if (room.getRoomId() == null || room.getRoomId().isBlank())
                room.setRoomId(ChallengeClient.parseStr(roomJson, "roomId"));
            ctrl.init(room, client, username, rootPane);
            if (rootPane != null) {
                Node old = rootPane.getCenter();
                AnimationUtil.contentTransition(old, node, null);
                rootPane.setCenter(node);
            }
        } catch (IOException e) { e.printStackTrace(); }
    }

    private void handleIncomingInvite(String payload) {
        String from   = ChallengeClient.parseStr(payload, "from");
        String roomId = ChallengeClient.parseStr(payload, "roomId");
        String mode   = ChallengeClient.parseStr(payload, "mode");
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                from + " invited you to room " + roomId + " [" + mode + "]",
                new ButtonType("Accept", ButtonBar.ButtonData.OK_DONE),
                new ButtonType("Decline", ButtonBar.ButtonData.CANCEL_CLOSE));
        alert.setTitle("Challenge Received!");
        alert.setHeaderText(null);
        applyAlertCss(alert);
        alert.showAndWait().ifPresent(bt -> {
            if (bt.getButtonData() == ButtonBar.ButtonData.OK_DONE)
                client.joinRoom(username, roomId, "");
            else
                client.declineInvite(username, from, roomId);
        });
    }

    private void showAlert(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        a.setTitle(title); a.setHeaderText(null);
        applyAlertCss(a);
        a.showAndWait();
    }

    private void applyAlertCss(Alert a) {
        try {
            a.getDialogPane().getStylesheets().add(
                    getClass().getResource("css/alert.css").toExternalForm());
        } catch (Exception ignored) {}
    }

    @FXML
    private void handleRefresh() { client.getRooms(); }

    @FXML
    private void handleBack() {
        navigateToLobby();
    }

    private void navigateToLobby() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("fxml files/challenge_lobby.fxml"));
            Node node = loader.load();
            ChallengeLobbyController ctrl = loader.getController();
            ctrl.setRootPane(rootPane);
            if (rootPane != null) {
                Node old = rootPane.getCenter();
                AnimationUtil.contentTransition(old, node, null);
                rootPane.setCenter(node);
            }
        } catch (IOException e) { e.printStackTrace(); }
    }
}
