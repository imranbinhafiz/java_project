package com.example.javaproject;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.Duration;

import java.io.IOException;
import java.util.List;

/**
 * Speed mode exam scene.
 * Loads the assigned exam, player answers individually.
 * First correct answer gets full marks, others get 0 for that question.
 */
public class ChallengeSpeedExamController {

    @FXML private Label   examTitleLabel;
    @FXML private Label   timerLabel;
    @FXML private Label   progressLabel;
    @FXML private VBox    questionBox;
    @FXML private Label   questionText;
    @FXML private VBox    optionsBox;
    @FXML private Label   feedbackLabel;
    @FXML private Label   participantsStatusLabel;

    @FXML private StackPane popupOverlay;
    @FXML private VBox      popupContent;

    private ChallengeClient client;
    private String          username;
    private String          roomId;
    private String          examId;
    private String          examTitle;
    private BorderPane      rootPane;

    private Exam            exam;
    private int             currentQuestionIndex = 0;
    private int             totalScore = 0;
    private Timeline        timerTimeline;
    private int             secondsLeft;
    private List<Integer>   answers;
    private boolean         examSubmitted = false;

    public void init(ChallengeClient client, String username, String roomId,
                     String examId, String examTitle, BorderPane rootPane) {
        this.client    = client;
        this.username  = username;
        this.roomId    = roomId;
        this.examId    = examId;
        this.examTitle = examTitle;
        this.rootPane  = rootPane;

        examTitleLabel.setText(examTitle);
        client.setPushListener(this::handlePush);

        // Load exam questions via ExamClient
        loadExamQuestions();
    }

    @FXML public void initialize() {}

    private void loadExamQuestions() {
        new Thread(() -> {
            try {
                String host = UserSession.getInstance().getServerHost();
                ExamClient examClient = new ExamClient(host, ExamServer.PORT);
                examClient.connect();
                exam = examClient.startExam(examId, username);
                Platform.runLater(() -> {
                    if (exam == null || exam.getQuestions() == null || exam.getQuestions().isEmpty()) {
                        showStyledPopup("Error", "No questions found for this exam.", "fas-exclamation-triangle", this::returnToLobby);
                        return;
                    }
                    answers = new java.util.ArrayList<>();
                    for (int i = 0; i < exam.getQuestions().size(); i++) answers.add(-1);
                    secondsLeft = exam.getDurationMinutes() * 60;
                    showQuestion(0);
                    startTimer();
                });
            } catch (Exception e) {
                Platform.runLater(() -> showStyledPopup("Error", "Failed to load exam: " + e.getMessage(), "fas-exclamation-triangle", this::returnToLobby));
            }
        }).start();
    }

    private void showQuestion(int idx) {
        if (exam == null || idx >= exam.getQuestions().size()) { submitExam(); return; }
        currentQuestionIndex = idx;
        Question q = exam.getQuestions().get(idx);
        progressLabel.setText("Question " + (idx + 1) + " / " + exam.getQuestions().size());
        questionText.setText(q.getQuestionText());
        questionText.setWrapText(true);
        optionsBox.getChildren().clear();
        feedbackLabel.setText("");

        // Show question image if present
        if (q.getImageBase64() != null && !q.getImageBase64().isEmpty()) {
            try {
                byte[] bytes = java.util.Base64.getDecoder().decode(q.getImageBase64());
                javafx.scene.image.Image img = new javafx.scene.image.Image(new java.io.ByteArrayInputStream(bytes));
                javafx.scene.image.ImageView iv = new javafx.scene.image.ImageView(img);
                iv.setFitWidth(320); iv.setFitHeight(200); iv.setPreserveRatio(true);
                questionBox.getChildren().add(iv);
            } catch (Exception ignored) {}
        }

        List<String> options = q.getOptions();
        for (int i = 0; i < options.size(); i++) {
            final int optIdx = i;
            Button optBtn = new Button((char)('A' + i) + ".  " + options.get(i));
            optBtn.getStyleClass().add("ch-speed-option-btn");
            optBtn.setMaxWidth(Double.MAX_VALUE);
            optBtn.setWrapText(true);
            optBtn.setOnAction(e -> selectAnswer(optIdx));
            optionsBox.getChildren().add(optBtn);
        }
    }

    private void selectAnswer(int optIdx) {
        if (answers == null || currentQuestionIndex >= answers.size()) return;
        // Lock all option buttons immediately — only one selection allowed
        for (int i = 0; i < optionsBox.getChildren().size(); i++) {
            Node n = optionsBox.getChildren().get(i);
            if (n instanceof Button btn) {
                btn.getStyleClass().removeAll("ch-speed-option-selected");
                if (i == optIdx) btn.getStyleClass().add("ch-speed-option-selected");
                btn.setDisable(true);
            }
        }
        answers.set(currentQuestionIndex, optIdx);
        // Auto-advance after short delay to show selection feedback
        PauseTransition pt = new PauseTransition(Duration.millis(350));
        pt.setOnFinished(e -> {
            if (currentQuestionIndex + 1 < exam.getQuestions().size())
                showQuestion(currentQuestionIndex + 1);
            else submitExam();
        });
        pt.play();
    }

    private void startTimer() {
        updateTimerLabel();
        timerTimeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            secondsLeft--;
            updateTimerLabel();
            if (secondsLeft <= 0) { timerTimeline.stop(); submitExam(); }
        }));
        timerTimeline.setCycleCount(Animation.INDEFINITE);
        timerTimeline.play();
    }

    private void updateTimerLabel() {
        int m = secondsLeft / 60, s = secondsLeft % 60;
        timerLabel.setText(String.format("%02d:%02d", m, s));
        if (secondsLeft <= 60) timerLabel.setStyle("-fx-text-fill:#fca5a5; -fx-font-weight:bold;");
        else timerLabel.setStyle("-fx-text-fill:#86efac; -fx-font-weight:bold;");
    }

    private void submitExam() {
        if (examSubmitted) return;
        examSubmitted = true;
        if (timerTimeline != null) timerTimeline.stop();
        client.speedSubmitAnswers(username, roomId, answers != null ? answers : new java.util.ArrayList<>());
        // Show waiting screen — stay in exam scene until all players submit or timer ends
        showWaitingForOthers();
    }

    private void showWaitingForOthers() {
        // Replace exam content with a waiting message
        if (questionBox != null) {
            questionBox.getChildren().clear();
            Label waitLbl = new Label("\u2713  All answers submitted!");
            waitLbl.setStyle("-fx-font-size:20px; -fx-font-weight:bold; -fx-text-fill:#86efac;");
            Label subLbl = new Label("Waiting for other players to finish...");
            subLbl.setStyle("-fx-font-size:14px; -fx-text-fill:#94a3b8;");
            questionBox.getChildren().addAll(waitLbl, subLbl);
        }
        if (optionsBox != null) optionsBox.getChildren().clear();
        if (progressLabel != null) progressLabel.setText("Submitted — waiting for results");
        if (feedbackLabel != null) feedbackLabel.setText("");
        if (participantsStatusLabel != null)
            participantsStatusLabel.setText("Waiting for all participants to finish...");
    }

    private void handlePush(String msg) {
        String cmd     = ExamJsonUtil.parseCommand(msg);
        String payload = ExamJsonUtil.parsePayload(msg);
        Platform.runLater(() -> {
            switch (cmd) {
                case "CH_SPEED_SUBMITTED" -> {
                    String who = ChallengeClient.parseStr(payload, "username");
                    if (!who.equals(username))
                        participantsStatusLabel.setText("Participants submitting...");
                }
                case "CH_SPEED_RESULTS"   -> navigateToSpeedResults(payload);
                case "CH_ROOM_CLOSED"     -> handleRoomClosed();
            }
        });
    }

    private void navigateToSpeedResults(String payload) {
        if (timerTimeline != null) timerTimeline.stop();
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("fxml files/challenge_speed_results.fxml"));
            Node node = loader.load();
            ChallengeSpeedResultsController ctrl = loader.getController();
            ctrl.init(client, username, roomId, payload, rootPane);
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

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
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
