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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;

/**
 * Evaluation Phase for Swap Duel.
 * Player reads the opponent's answer to THEIR question and assigns marks.
 */
public class ChallengeSwapEvalController {

    @FXML private Label    roundLabel;
    @FXML private Label    opponentStatusLabel;

    @FXML private Label    myQuestionLabel;
    @FXML private ImageView myQuestionImageView;
    @FXML private VBox     myQuestionImageBox;
    @FXML private Label    opponentAnswerLabel;
    @FXML private ImageView opponentAnswerImageView;
    @FXML private VBox     opponentAnswerImageBox;

    @FXML private Spinner<Integer> marksSpinner;
    @FXML private Button   submitMarksBtn;

    @FXML private StackPane popupOverlay;
    @FXML private VBox      popupContent;

    private ChallengeClient client;
    private String          username;
    private String          roomId;
    private int             round;
    private int             totalRounds;
    private BorderPane      rootPane;

    private boolean marksSubmitted = false;

    public void init(ChallengeClient client, String username, String roomId,
                     int round, int totalRounds,
                     String myQuestion, String myQuestionImageBase64,
                     String opponentAnswer, String opponentAnswerImageBase64,
                     BorderPane rootPane) {
        this.client      = client;
        this.username    = username;
        this.roomId      = roomId;
        this.round       = round;
        this.totalRounds = totalRounds;
        this.rootPane    = rootPane;

        roundLabel.setText("Round " + round + " / " + totalRounds + "  —  Evaluation Phase");

        myQuestionLabel.setText(myQuestion.isEmpty() ? "(No question text)" : myQuestion);
        myQuestionLabel.setWrapText(true);

        // Show question image if provided
        if (myQuestionImageBase64 != null && !myQuestionImageBase64.isEmpty()) {
            try {
                byte[] bytes = Base64.getDecoder().decode(myQuestionImageBase64);
                Image img = new Image(new ByteArrayInputStream(bytes));
                if (myQuestionImageView != null) {
                    myQuestionImageView.setImage(img);
                    if (myQuestionImageBox != null) {
                        myQuestionImageBox.setVisible(true);
                        myQuestionImageBox.setManaged(true);
                    }
                }
            } catch (Exception ignored) {}
        }

        opponentAnswerLabel.setText(opponentAnswer.isEmpty() ? "(No answer provided)" : opponentAnswer);
        opponentAnswerLabel.setWrapText(true);

        if (opponentAnswerImageBase64 != null && !opponentAnswerImageBase64.isEmpty()) {
            try {
                byte[] bytes = Base64.getDecoder().decode(opponentAnswerImageBase64);
                Image img = new Image(new ByteArrayInputStream(bytes));
                opponentAnswerImageView.setImage(img);
                opponentAnswerImageBox.setVisible(true);
                opponentAnswerImageBox.setManaged(true);
            } catch (Exception ignored) {}
        }

        // Marks spinner: 0-10
        marksSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 100, 5, 1));
        marksSpinner.setEditable(true);

        client.setPushListener(this::handlePush);
    }

    @FXML public void initialize() {}

    private void handlePush(String msg) {
        String cmd     = ExamJsonUtil.parseCommand(msg);
        String payload = ExamJsonUtil.parsePayload(msg);
        Platform.runLater(() -> {
            switch (cmd) {
                case "CH_SWAP_MARKS_SUBMITTED" -> {
                    String who = ChallengeClient.parseStr(payload, "username");
                    if (!who.equals(username)) {
                        opponentStatusLabel.setText("✓ Opponent submitted marks");
                        opponentStatusLabel.setStyle("-fx-text-fill:#86efac;");
                    }
                }
                case "CH_SWAP_ROUND_DONE" -> handleRoundDone(payload);
                case "CH_SWAP_GAME_OVER"  -> handleGameOver(payload);
                case "CH_ROOM_CLOSED"     -> handleRoomClosed();
            }
        });
    }

    @FXML private void handleSubmitMarks() {
        if (marksSubmitted) return;
        int marks = marksSpinner.getValue();
        client.swapSubmitMarks(username, roomId, round, marks);
        marksSubmitted = true;
        submitMarksBtn.setDisable(true);
        submitMarksBtn.setText("Marks Submitted ✓");
    }

    private void handleRoundDone(String payload) {
        int nextRound = ChallengeClient.parseInt(payload, "nextRound");
        int totalRnds = ChallengeClient.parseInt(payload, "totalRounds");
        // Navigate to next round intro
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("fxml files/challenge_round_intro.fxml"));
            Node node = loader.load();
            ChallengeRoundIntroController ctrl = loader.getController();
            int timer = ChallengeClient.parseInt(payload, "roundTimerSeconds");
            ctrl.init(client, username, roomId, nextRound, totalRnds, timer > 0 ? timer : 120, rootPane);
            if (rootPane != null) { AnimationUtil.contentTransition(rootPane.getCenter(), node, null); rootPane.setCenter(node); }
        } catch (IOException e) { e.printStackTrace(); }
    }

    private void handleGameOver(String payload) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("fxml files/challenge_swap_results.fxml"));
            Node node = loader.load();
            ChallengeSwapResultsController ctrl = loader.getController();
            ctrl.init(client, username, roomId, payload, rootPane);
            if (rootPane != null) { AnimationUtil.contentTransition(rootPane.getCenter(), node, null); rootPane.setCenter(node); }
        } catch (IOException e) { e.printStackTrace(); }
    }

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
