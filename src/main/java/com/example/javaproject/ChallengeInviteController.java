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
 * Invite screen — shows all online users, host can send invites.
 */
public class ChallengeInviteController {

    @FXML private VBox      usersBox;
    @FXML private TextField searchField;
    @FXML private Label     statusLabel;

    private ChallengeClient         client;
    private String                  username;
    private String                  roomId;
    private BorderPane              rootPane;
    private ChallengeRoomController roomCtrl;
    private List<ChallengeUser>     allUsers;

    // ── Init ──────────────────────────────────────────────────────────

    public void init(ChallengeClient client, String username, String roomId,
                     BorderPane rootPane, ChallengeRoomController roomCtrl) {
        this.client   = client;
        this.username = username;
        this.roomId   = roomId;
        this.rootPane = rootPane;
        this.roomCtrl = roomCtrl;

        client.setPushListener(msg -> {
            String cmd     = ExamJsonUtil.parseCommand(msg);
            String payload = ExamJsonUtil.parsePayload(msg);
            Platform.runLater(() -> {
                switch (cmd) {
                    case "CH_USERS_LIST"  -> { allUsers = ChallengeClient.parseUsers(payload);
                                               filterAndDisplay(searchField.getText()); }
                    case "CH_INVITE_SENT" -> statusLabel.setText("✓  Invite sent!");
                    case "CH_INVITE_FAIL" ->
                        statusLabel.setText("⚠  " + ChallengeClient.parseStr(payload, "message"));
                }
            });
        });
        client.getUsers();
    }

    @FXML public void initialize() {}

    @FXML private void handleSearch() {
        if (allUsers != null) filterAndDisplay(searchField.getText());
    }

    private void filterAndDisplay(String query) {
        usersBox.getChildren().clear();
        String q = (query == null) ? "" : query.toLowerCase().trim();
        boolean any = false;
        if (allUsers != null) {
            for (ChallengeUser u : allUsers) {
                if (u.getUsername().equals(username)) continue;
                if (!q.isEmpty() && !u.getUsername().toLowerCase().contains(q)) continue;
                usersBox.getChildren().add(buildUserRow(u));
                any = true;
            }
        }
        if (!any) {
            Label lbl = new Label("No users found.");
            lbl.getStyleClass().add("ch-meta-lbl");
            usersBox.getChildren().add(lbl);
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
        if (u.isInGame())       { badge = new Label("In Game");  badge.getStyleClass().add("ch-badge-ingame"); }
        else if (u.isInRoom())  { badge = new Label("In Room");  badge.getStyleClass().add("ch-badge-inroom"); }
        else if (u.isOnline())  { badge = new Label("Online");   badge.getStyleClass().add("ch-badge-online"); }
        else                    { badge = new Label("Offline");  badge.getStyleClass().add("ch-badge-offline"); }

        Button inviteBtn = new Button("Invite");
        inviteBtn.getStyleClass().add(u.isOnline() ? "ch-btn-action" : "ch-btn-secondary");
        inviteBtn.setStyle("-fx-padding:6 16 6 16; -fx-font-size:12px;");
        inviteBtn.setDisable(!u.isOnline() || u.isInGame());
        inviteBtn.setOnAction(e -> {
            client.invite(username, u.getUsername(), roomId);
            inviteBtn.setText("Sent ✓");
            inviteBtn.setDisable(true);
        });

        row.getChildren().addAll(dot, name, spacer, badge, inviteBtn);
        return row;
    }

    @FXML private void handleBack() {
        roomCtrl.restoreRoomView();
    }
}
