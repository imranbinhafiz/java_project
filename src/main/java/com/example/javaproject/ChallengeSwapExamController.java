package com.example.javaproject;

import javafx.animation.*;
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
import javafx.util.Duration;

import java.io.*;
import java.util.Base64;

/**
 * Exam Phase for Swap Duel.
 * Each player sees their opponent's question and writes/uploads their answer.
 */
public class ChallengeSwapExamController {

    @FXML private Label   roundLabel;
    @FXML private Label   timerLabel;
    @FXML private Label   opponentStatusLabel;
    @FXML private Label   questionText;
    @FXML private ImageView questionImageView;
    @FXML private VBox    questionImageBox;

    @FXML private TextArea answerTextArea;
    @FXML private ImageView answerImageView;
    @FXML private VBox     answerImageBox;
    @FXML private Button   uploadAnswerImageBtn;
    @FXML private Button   removeAnswerImageBtn;
    @FXML private Button   submitAnswerBtn;

    @FXML private StackPane popupOverlay;
    @FXML private VBox      popupContent;

    private ChallengeClient client;
    private String          username;
    private String          roomId;
    private int             round;
    private int             totalRounds;
    private int             roundTimerSeconds;
    private BorderPane      rootPane;

    private String selectedAnswerImageBase64 = "";
    private boolean answerSubmitted = false;
    private Timeline timerTimeline;
    private int secondsLeft;

    public void init(ChallengeClient client, String username, String roomId,
                     int round, int totalRounds, int roundTimerSeconds,
                     String opponentQuestion, String opponentImageBase64,
                     BorderPane rootPane) {
        this.client           = client;
        this.username         = username;
        this.roomId           = roomId;
        this.round            = round;
        this.totalRounds      = totalRounds;
        this.roundTimerSeconds = roundTimerSeconds;
        this.rootPane         = rootPane;
        this.secondsLeft      = roundTimerSeconds;

        roundLabel.setText("Round " + round + " / " + totalRounds + "  —  Exam Phase");
        questionText.setText(opponentQuestion);
        questionText.setWrapText(true);

        // Show opponent's image if provided
        if (opponentImageBase64 != null && !opponentImageBase64.isEmpty()) {
            try {
                byte[] bytes = Base64.getDecoder().decode(opponentImageBase64);
                Image img = new Image(new ByteArrayInputStream(bytes));
                questionImageView.setImage(img);
                questionImageBox.setVisible(true);
                questionImageBox.setManaged(true);
            } catch (Exception ignored) {}
        }

        client.setPushListener(this::handlePush);
        startTimer();
    }

    @FXML public void initialize() {}

    private void startTimer() {
        updateTimerLabel();
        timerTimeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            secondsLeft--;
            updateTimerLabel();
            if (secondsLeft <= 0) {
                timerTimeline.stop();
                if (!answerSubmitted) autoSubmitAnswer();
            }
        }));
        timerTimeline.setCycleCount(Animation.INDEFINITE);
        timerTimeline.play();
    }

    private void updateTimerLabel() {
        int m = secondsLeft / 60, s = secondsLeft % 60;
        timerLabel.setText(String.format("%02d:%02d", m, s));
        if (secondsLeft <= 30) timerLabel.setStyle("-fx-text-fill:#fca5a5; -fx-font-weight:bold; -fx-font-size:20px;");
        else timerLabel.setStyle("-fx-text-fill:#86efac; -fx-font-weight:bold; -fx-font-size:20px;");
    }

    private void autoSubmitAnswer() {
        String text = answerTextArea != null ? answerTextArea.getText().trim() : "";
        client.swapSubmitAnswer(username, roomId, round, text, selectedAnswerImageBase64);
        answerSubmitted = true;
        if (submitAnswerBtn != null) { submitAnswerBtn.setDisable(true); submitAnswerBtn.setText("Submitted ✓"); }
    }

    private void handlePush(String msg) {
        String cmd     = ExamJsonUtil.parseCommand(msg);
        String payload = ExamJsonUtil.parsePayload(msg);
        Platform.runLater(() -> {
            switch (cmd) {
                case "CH_SWAP_ANSWER_SUBMITTED" -> {
                    String who = ChallengeClient.parseStr(payload, "username");
                    if (!who.equals(username)) {
                        opponentStatusLabel.setText("✓ Opponent submitted");
                        opponentStatusLabel.setStyle("-fx-text-fill:#86efac;");
                    }
                }
                case "CH_SWAP_PHASE_EVALUATION" -> navigateToEvaluation(payload);
                case "CH_ROOM_CLOSED"           -> handleRoomClosed();
            }
        });
    }

    @FXML private void handleUploadAnswerImage() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Select Answer Image");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.gif"));
        File file = fc.showOpenDialog(uploadAnswerImageBtn.getScene().getWindow());
        if (file != null) {
            try {
                byte[] bytes = java.nio.file.Files.readAllBytes(file.toPath());
                selectedAnswerImageBase64 = Base64.getEncoder().encodeToString(bytes);
                Image img = new Image(new FileInputStream(file));
                answerImageView.setImage(img);
                answerImageBox.setVisible(true); answerImageBox.setManaged(true);
            } catch (IOException e) { selectedAnswerImageBase64 = ""; }
        }
    }

    @FXML private void handleRemoveAnswerImage() {
        selectedAnswerImageBase64 = "";
        answerImageView.setImage(null);
        answerImageBox.setVisible(false); answerImageBox.setManaged(false);
    }

    @FXML private void handleSubmitAnswer() {
        if (answerSubmitted) return;
        String text = answerTextArea.getText().trim();
        client.swapSubmitAnswer(username, roomId, round, text, selectedAnswerImageBase64);
        answerSubmitted = true;
        submitAnswerBtn.setDisable(true);
        submitAnswerBtn.setText("Submitted ✓");
        if (timerTimeline != null) timerTimeline.stop();
    }

    private void navigateToEvaluation(String payload) {
        if (timerTimeline != null) timerTimeline.stop();
        String myQuestion          = ChallengeClient.parseStr(payload, "questionText");
        String myQuestionImage     = ChallengeClient.parseStr(payload, "questionImageBase64");
        String opponentAnswer      = ChallengeClient.parseStr(payload, "answerText");
        String opponentAnswerImage = ChallengeClient.parseStr(payload, "answerImageBase64");
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("fxml files/challenge_swap_eval.fxml"));
            Node node = loader.load();
            ChallengeSwapEvalController ctrl = loader.getController();
            ctrl.init(client, username, roomId, round, totalRounds,
                    myQuestion, myQuestionImage, opponentAnswer, opponentAnswerImage, rootPane);
            if (rootPane != null) { AnimationUtil.contentTransition(rootPane.getCenter(), node, null); rootPane.setCenter(node); }
        } catch (IOException e) { e.printStackTrace(); }
    }

    private void handleRoomClosed() {
        if (timerTimeline != null) timerTimeline.stop();
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
        Button okBtn = new Button("OK"); okBtn.getStyleClass().add("ch-btn-primary");
        okBtn.setOnAction(e -> { AnimationUtil.fadeOut(popupOverlay, () -> popupOverlay.setVisible(false)); if (onClose != null) onClose.run(); });
        footer.getChildren().add(okBtn);
        popupContent.getChildren().addAll(header, body, footer);
        popupOverlay.setVisible(true); AnimationUtil.fadeIn(popupOverlay);
    }
}
