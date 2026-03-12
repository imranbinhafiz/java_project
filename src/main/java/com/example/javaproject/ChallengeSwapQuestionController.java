package com.example.javaproject;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;

import java.io.*;
import java.util.Base64;
import java.util.List;
import java.io.IOException;

/**
 * Question Creation phase for Swap Duel.
 * Each player writes a descriptive question and optionally attaches an image.
 * There is a chat panel and the host can set the exam timer.
 */
public class ChallengeSwapQuestionController {

    // ── FXML ──────────────────────────────────────────────────────────
    @FXML private Label  roundLabel;
    @FXML private Label  phaseLabel;
    @FXML private Label  myStatusLabel;
    @FXML private Label  opponentStatusLabel;

    @FXML private TextArea questionTextArea;
    @FXML private ImageView questionImageView;
    @FXML private VBox      imagePreviewBox;
    @FXML private Button    uploadImageBtn;
    @FXML private Button    removeImageBtn;
    @FXML private Button    submitQuestionBtn;

    // Host-only timer control
    @FXML private HBox   timerControlRow;
    @FXML private Spinner<Integer> timerSpinner;
    @FXML private Button setTimerBtn;

    // Chat
    @FXML private VBox      chatBox;
    @FXML private ScrollPane chatScroll;
    @FXML private TextField chatInput;

    @FXML private StackPane popupOverlay;
    @FXML private VBox      popupContent;

    // ── State ─────────────────────────────────────────────────────────
    private ChallengeClient client;
    private String          username;
    private String          roomId;
    private int             round;
    private int             totalRounds;
    private int             roundTimerSeconds;
    private BorderPane      rootPane;

    private static final int DEFAULT_TIMER_SECONDS = 120;
    private static final int MIN_TIMER_MINUTES = 1;
    private static final int MAX_TIMER_MINUTES = 10;

    private String selectedImageBase64 = "";
    private boolean questionUploaded = false;

    public void init(ChallengeClient client, String username, String roomId,
                     int round, int totalRounds, int roundTimerSeconds, BorderPane rootPane) {
        this.client           = client;
        this.username         = username;
        this.roomId           = roomId;
        this.round            = round;
        this.totalRounds      = totalRounds;
        this.roundTimerSeconds = normalizeTimerSeconds(roundTimerSeconds);
        this.rootPane         = rootPane;

        roundLabel.setText("Round " + round + " / " + totalRounds);
        updatePhaseLabel();

        // Timer spinner
        if (timerSpinner != null) {
            int minutes = secondsToMinutes(this.roundTimerSeconds);
            timerSpinner.setValueFactory(
                    new SpinnerValueFactory.IntegerSpinnerValueFactory(MIN_TIMER_MINUTES, MAX_TIMER_MINUTES, minutes, 1));
        }
        // Host-only timer control
        // We detect host by checking via server state; but we can track by a flag set from room data.
        // For simplicity, the room host username is tracked in a separate field set from caller.
        // We'll request room state to check
        client.setPushListener(this::handlePush);
        client.getRoomState(roomId);
    }

    @FXML public void initialize() {}

    // ── Push Handler ──────────────────────────────────────────────────

    private void handlePush(String msg) {
        String cmd     = ExamJsonUtil.parseCommand(msg);
        String payload = ExamJsonUtil.parsePayload(msg);
        Platform.runLater(() -> {
            switch (cmd) {
                case "CH_ROOM_STATE", "CH_ROOM_UPDATE" -> {
                    List<ChallengeRoom> rooms = ChallengeClient.parseRooms("{\"rooms\":[" + payload + "]}");
                    if (!rooms.isEmpty()) updateRoomState(rooms.get(0));
                }
                case "CH_SWAP_QUESTION_UPLOADED" -> handleQuestionUploaded(payload);
                case "CH_SWAP_PHASE_EXAM"        -> navigateToExamPhase(payload);
                case "CH_SWAP_CHAT_MSG"          -> appendChatMsg(ChallengeClient.parseStr(payload, "username"),
                        ChallengeClient.parseStr(payload, "text"));
                case "CH_ROOM_CLOSED"            -> handleRoomClosed();
                case "CH_SWAP_TIMER_SET"         -> applyTimerSeconds(ChallengeClient.parseInt(payload, "seconds"));
            }
        });
    }

    private void updateRoomState(ChallengeRoom r) {
        boolean isHost = username.equals(r.getHost());
        if (timerControlRow != null) {
            timerControlRow.setVisible(isHost);
            timerControlRow.setManaged(isHost);
        }
        applyTimerSeconds(r.getRoundTimerSeconds());
    }

    private void handleQuestionUploaded(String payload) {
        String who = ChallengeClient.parseStr(payload, "username");
        if (who.equals(username)) {
            myStatusLabel.setText("✓ Your question uploaded");
            myStatusLabel.setStyle("-fx-text-fill:#86efac; -fx-font-weight:bold;");
            submitQuestionBtn.setDisable(true);
        } else {
            opponentStatusLabel.setText("✓ Opponent question ready");
            opponentStatusLabel.setStyle("-fx-text-fill:#86efac; -fx-font-weight:bold;");
        }
    }

    private void navigateToExamPhase(String payload) {
        // payload contains opponentQuestion + opponentImage
        String opponentQuestion = ChallengeClient.parseStr(payload, "questionText");
        String opponentImage    = ChallengeClient.parseStr(payload, "imageBase64");
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("fxml files/challenge_swap_exam.fxml"));
            Node node = loader.load();
            ChallengeSwapExamController ctrl = loader.getController();
            ctrl.init(client, username, roomId, round, totalRounds, roundTimerSeconds,
                    opponentQuestion, opponentImage, rootPane);
            if (rootPane != null) {
                AnimationUtil.contentTransition(rootPane.getCenter(), node, null);
                rootPane.setCenter(node);
            }
        } catch (IOException e) { e.printStackTrace(); }
    }

    // ── Image Upload ──────────────────────────────────────────────────

    @FXML private void handleUploadImage() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Select Question Image");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.gif"));
        File file = fc.showOpenDialog(uploadImageBtn.getScene().getWindow());
        if (file != null) {
            try {
                byte[] bytes = java.nio.file.Files.readAllBytes(file.toPath());
                selectedImageBase64 = Base64.getEncoder().encodeToString(bytes);
                Image img = new Image(new FileInputStream(file));
                questionImageView.setImage(img);
                imagePreviewBox.setVisible(true);
                imagePreviewBox.setManaged(true);
            } catch (IOException e) {
                selectedImageBase64 = "";
            }
        }
    }

    @FXML private void handleRemoveImage() {
        selectedImageBase64 = "";
        questionImageView.setImage(null);
        imagePreviewBox.setVisible(false);
        imagePreviewBox.setManaged(false);
    }

    // ── Submit Question ───────────────────────────────────────────────

    @FXML private void handleSubmitQuestion() {
        String text = questionTextArea.getText().trim();
        if (text.isEmpty()) {
            showStyledPopup("Missing Question", "Please write your question before submitting.", "fas-exclamation-circle");
            return;
        }
        client.swapUploadQuestion(username, roomId, round, text, selectedImageBase64);
        submitQuestionBtn.setDisable(true);
        submitQuestionBtn.setText("Uploaded ✓");
    }

    // ── Timer (host only) ─────────────────────────────────────────────

    @FXML private void handleSetTimer() {
        int minutes = timerSpinner != null ? timerSpinner.getValue() : (DEFAULT_TIMER_SECONDS / 60);
        minutes = clampMinutes(minutes);
        int seconds = minutes * 60;
        client.swapSetTimer(username, roomId, seconds);
    }

    private int normalizeTimerSeconds(int seconds) {
        if (seconds <= 0) return DEFAULT_TIMER_SECONDS;
        int minutes = (seconds + 59) / 60;
        minutes = clampMinutes(minutes);
        return minutes * 60;
    }

    private int secondsToMinutes(int seconds) {
        return normalizeTimerSeconds(seconds) / 60;
    }

    private int clampMinutes(int minutes) {
        if (minutes < MIN_TIMER_MINUTES) return MIN_TIMER_MINUTES;
        if (minutes > MAX_TIMER_MINUTES) return MAX_TIMER_MINUTES;
        return minutes;
    }

    private void applyTimerSeconds(int seconds) {
        this.roundTimerSeconds = normalizeTimerSeconds(seconds);
        updatePhaseLabel();
        if (timerSpinner != null && timerSpinner.getValueFactory() != null) {
            timerSpinner.getValueFactory().setValue(secondsToMinutes(this.roundTimerSeconds));
        }
    }

    private void updatePhaseLabel() {
        if (phaseLabel == null) return;
        int minutes = secondsToMinutes(roundTimerSeconds);
        phaseLabel.setText("Question Creation - Exam timer: " + minutes + " min");
    }

    // ── Chat ──────────────────────────────────────────────────────────

    @FXML private void handleSendChat() {
        if (chatInput == null) return;
        String text = chatInput.getText().trim();
        if (text.isEmpty()) return;
        client.swapChatMessage(username, roomId, text);
        chatInput.clear();
    }

    private void appendChatMsg(String from, String text) {
        if (chatBox == null) return;
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);
        Label nameLbl = new Label(from + ":");
        nameLbl.setStyle("-fx-font-weight:bold; -fx-text-fill:" + (from.equals(username) ? "#818cf8" : "#94a3b8") + ";");
        Label textLbl = new Label(text);
        textLbl.setStyle("-fx-text-fill:#e2e8f0;");
        textLbl.setWrapText(true);
        row.getChildren().addAll(nameLbl, textLbl);
        chatBox.getChildren().add(row);
        if (chatScroll != null) chatScroll.setVvalue(1.0);
    }

    // ── Room Closed ───────────────────────────────────────────────────

    private void handleRoomClosed() {
        showStyledPopup("Room Closed", "The host has left. The room has been closed.", "fas-door-closed", this::returnToLobby);
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

    // ── Popup ─────────────────────────────────────────────────────────

    private void showStyledPopup(String title, String message, String icon) {
        showStyledPopup(title, message, icon, null);
    }

    private void showStyledPopup(String title, String message, String icon, Runnable onClose) {
        if (popupOverlay == null || popupContent == null) return;
        popupContent.getChildren().clear();

        HBox header = new HBox(10); header.getStyleClass().add("ch-popup-header"); header.setAlignment(Pos.CENTER_LEFT);
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
}
