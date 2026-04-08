package com.example.javaproject;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.io.IOException;
import java.util.List;

/**
 * Controller for the Create Room scene.
 * Only two modes: SWAP_DUEL and SPEED.
 */
public class ChallengeCreateRoomController {

    @FXML private VBox   swapDuelCard;
    @FXML private VBox   speedCard;
    @FXML private ComboBox<String> roomTypeCombo;
    @FXML private VBox   passwordBox;
    @FXML private TextField passwordField;
    @FXML private VBox   swapSettingsBox;
    @FXML private ComboBox<String> roundsCombo;
    @FXML private ComboBox<String> maxPlayersCombo;
    @FXML private Label  errorLabel;

    private ChallengeClient client;
    private String          username;
    private BorderPane      rootPane;
    private ChallengeLobbyController lobbyCtrl;

    private ChallengeRoom.GameMode selectedMode = ChallengeRoom.GameMode.SWAP_DUEL;
    private VBox currentModeCard;

    public void init(ChallengeClient client, String username,
                     BorderPane rootPane, ChallengeLobbyController lobby) {
        this.client    = client;
        this.username  = username;
        this.rootPane  = rootPane;
        this.lobbyCtrl = lobby;
    }

    @FXML
    public void initialize() {
        roomTypeCombo.getItems().addAll("PUBLIC", "PRIVATE");
        roomTypeCombo.setValue("PUBLIC");

        roundsCombo.getItems().addAll("1", "2", "3", "4", "5", "6", "7", "8", "9", "10");
        roundsCombo.setValue("3");

        maxPlayersCombo.getItems().addAll("2", "3", "4", "5", "6", "7", "8");
        maxPlayersCombo.setValue("2");

        selectSwapDuel();
    }

    @FXML private void selectSwapDuel() {
        selectedMode = ChallengeRoom.GameMode.SWAP_DUEL;
        if (currentModeCard != null) currentModeCard.getStyleClass().remove("ch-mode-card-sel");
        currentModeCard = swapDuelCard;
        swapDuelCard.getStyleClass().add("ch-mode-card-sel");
        // Swap duel is always 2 players
        maxPlayersCombo.setValue("2");
        maxPlayersCombo.setDisable(true);
        // Show swap settings
        if (swapSettingsBox != null) { swapSettingsBox.setVisible(true); swapSettingsBox.setManaged(true); }
    }

    @FXML private void selectSpeed() {
        selectedMode = ChallengeRoom.GameMode.SPEED;
        if (currentModeCard != null) currentModeCard.getStyleClass().remove("ch-mode-card-sel");
        currentModeCard = speedCard;
        speedCard.getStyleClass().add("ch-mode-card-sel");
        maxPlayersCombo.setValue("4");
        maxPlayersCombo.setDisable(false);
        // Hide swap settings
        if (swapSettingsBox != null) { swapSettingsBox.setVisible(false); swapSettingsBox.setManaged(false); }
    }

    @FXML
    private void onRoomTypeChange() {
        boolean isPrivate = "PRIVATE".equals(roomTypeCombo.getValue());
        passwordBox.setVisible(isPrivate);
        passwordBox.setManaged(isPrivate);
    }

    @FXML
    private void handleCreate() {
        if (selectedMode == null) { showError("Please select a game mode."); return; }

        ChallengeRoom.RoomType type = ChallengeRoom.RoomType.valueOf(
                roomTypeCombo.getValue() != null ? roomTypeCombo.getValue() : "PUBLIC");
        String password = passwordField != null ? passwordField.getText().trim() : "";
        if (type == ChallengeRoom.RoomType.PRIVATE && password.isEmpty()) {
            showError("Enter a password for the private room."); return;
        }

        int maxPlayers;
        try { maxPlayers = Integer.parseInt(maxPlayersCombo.getValue()); }
        catch (Exception e) { maxPlayers = selectedMode.getMaxPlayers(); }

        int totalRounds = 3;
        try { totalRounds = Integer.parseInt(roundsCombo.getValue()); } catch (Exception ignored) {}

        final int finalRounds = totalRounds;
        final int timerSecs   = 120;

        client.setPushListener(msg -> {
            String cmd = ExamJsonUtil.parseCommand(msg);
            String payload = ExamJsonUtil.parsePayload(msg);
            Platform.runLater(() -> {
                switch (cmd) {
                    case "CH_CREATE_ROOM_SUCCESS" -> openRoom(payload);
                    case "CH_CREATE_ROOM_FAIL"    -> showError(ChallengeClient.parseStr(payload, "message"));
                }
            });
        });
        client.createRoom(username, selectedMode, type, password, maxPlayers, finalRounds, timerSecs);
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
        } catch (IOException e) {
            showError("Failed to open room: " + e.getMessage());
        }
    }

    private void showError(String msg) {
        if (errorLabel != null) {
            errorLabel.setText("⚠  " + msg);
            errorLabel.setVisible(true);
            errorLabel.setManaged(true);
        }
    }

    @FXML
    private void handleBack() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("fxml files/challenge_lobby.fxml"));
            Node node = loader.load();
            ChallengeLobbyController ctrl = loader.getController();
            ctrl.setRootPane(rootPane);
            if (rootPane != null) {
                AnimationUtil.contentTransition(rootPane.getCenter(), node, null);
                rootPane.setCenter(node);
            }
        } catch (IOException e) { e.printStackTrace(); }
    }
}
