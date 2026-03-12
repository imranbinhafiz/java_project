package com.example.javaproject;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.io.IOException;
import java.util.List;

/**
 * Controller for the Participate Exam screen.
 * Contains two tabs:
 *   Tab 1 – Available Exams   (not yet attempted, within time window)
 *   Tab 2 – Previous Exams    (attempt history with scores)
 */
public class ParticipateExamController {

    @FXML private TabPane   tabPane;
    @FXML private Tab       availableTab;
    @FXML private Tab       previousTab;
    @FXML private VBox      availableExamsContainer;
    @FXML private VBox      previousExamsContainer;
    @FXML private Label     availableStatusLabel;
    @FXML private Label     previousStatusLabel;

    private BorderPane rootPane;
    private ExamClient client;
    private final String username = UserSession.getInstance().getUsername();

    public void setRootPane(BorderPane rp) {
        this.rootPane = rp;
        loadData();
    }

    @FXML
    public void initialize() {
        // Data loaded after rootPane is set
    }

    // ──────────────────────────────────────────────────────────────────
    // LOAD DATA
    // ──────────────────────────────────────────────────────────────────

    private void loadData() {
        new Thread(() -> {
            try {
                String serverHost = UserSession.getInstance().getServerHost();
                client = new ExamClient(serverHost, ExamServer.PORT);
                client.connect();

                List<Exam>       available = client.getAvailableExams(username);
                List<ExamResult> previous  = client.getPreviousExams(username);

                Platform.runLater(() -> {
                    renderAvailableExams(available);
                    renderPreviousExams(previous);
                });
            } catch (ExamClient.ExamClientException e) {
                Platform.runLater(() -> {
                    setStatus(availableStatusLabel, "❌ " + e.getMessage(), true);
                    setStatus(previousStatusLabel,  "❌ " + e.getMessage(), true);
                });
            }
        }, "participate-load-thread").start();
    }

    @FXML
    private void handleRefresh(ActionEvent event) {
        availableExamsContainer.getChildren().clear();
        previousExamsContainer.getChildren().clear();
        setStatus(availableStatusLabel, "Refreshing...", false);
        setStatus(previousStatusLabel,  "Refreshing...", false);
        loadData();
    }

    // ──────────────────────────────────────────────────────────────────
    // RENDER – AVAILABLE EXAMS
    // ──────────────────────────────────────────────────────────────────

    private void renderAvailableExams(List<Exam> exams) {
        availableExamsContainer.getChildren().clear();
        setStatus(availableStatusLabel, "", false);

        if (exams.isEmpty()) {
            availableExamsContainer.getChildren().add(emptyCard("No exams available right now."));
            return;
        }

        for (Exam exam : exams) {
            availableExamsContainer.getChildren().add(buildAvailableCard(exam));
        }
    }

    private Node buildAvailableCard(Exam exam) {
        // Title row
        Label titleLbl = new Label(exam.getTitle());
        titleLbl.getStyleClass().add("exam-card-title");

        // Type badge
        boolean isRealTime = exam.getExamType() == Exam.ExamType.REAL_TIME;
        Label typeBadge = new Label(isRealTime ? "\uD83D\uDD34 Live" : "\uD83D\uDCD8 Practice");
        typeBadge.getStyleClass().add(isRealTime ? "exam-badge-live" : "exam-badge-practice");

        HBox badgeRow = new HBox(8, typeBadge);
        badgeRow.setAlignment(Pos.CENTER_RIGHT);
        if (exam.isProtected()) {
            Label protectedBadge = new Label("\uD83D\uDD12 Protected");
            protectedBadge.getStyleClass().add("exam-badge-protected");
            badgeRow.getChildren().add(protectedBadge);
        }

        HBox titleRow = new HBox(10, titleLbl, new Pane() {{ HBox.setHgrow(this, Priority.ALWAYS); }}, badgeRow);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        // Meta info
        Label publisherLbl = new Label("By: " + exam.getPublisherUsername());
        publisherLbl.getStyleClass().add("exam-card-meta");

        Label durationLbl = new Label("⏱ " + exam.getDurationMinutes() + " min");
        durationLbl.getStyleClass().add("exam-card-meta");

        Label marksLbl = new Label("📊 " + exam.getTotalMarks() + " marks");
        marksLbl.getStyleClass().add("exam-card-meta");

        String deadlineText = (isRealTime && exam.getEndTime() != null)
                ? "⏰ Ends: " + exam.getEndTime().format(java.time.format.DateTimeFormatter.ofPattern("MMM dd, HH:mm"))
                : "";
        Label deadlineLbl = new Label(deadlineText);
        deadlineLbl.getStyleClass().add("exam-card-meta");

        HBox metaRow = new HBox(20, publisherLbl, durationLbl, marksLbl);
        if (!deadlineText.isEmpty()) metaRow.getChildren().add(deadlineLbl);
        metaRow.setAlignment(Pos.CENTER_LEFT);

        // Start button
        Button startBtn = new Button("▶ Start Exam");
        startBtn.getStyleClass().add("exam-start-btn");

        boolean accessible = exam.isAccessible();
        boolean expired = exam.isExpired();
        boolean scheduled = isRealTime && !accessible && !expired;

        startBtn.setDisable(!accessible);
        if (scheduled) {
            startBtn.setText("Scheduled");
        } else if (expired) {
            startBtn.setText("Expired");
        }

        startBtn.setOnAction(e -> handleStartExam(exam));

        HBox btnRow = new HBox(startBtn);
        btnRow.setAlignment(Pos.CENTER_RIGHT);

        VBox card = new VBox(10, titleRow, metaRow, btnRow);
        card.getStyleClass().add("exam-available-card");
        card.setPadding(new Insets(18));

        AnimationUtil.fadeIn(card, 350, null);
        return card;
    }

    // ──────────────────────────────────────────────────────────────────
    // RENDER – PREVIOUS EXAMS
    // ──────────────────────────────────────────────────────────────────

    private void renderPreviousExams(List<ExamResult> results) {
        previousExamsContainer.getChildren().clear();
        setStatus(previousStatusLabel, "", false);

        if (results.isEmpty()) {
            previousExamsContainer.getChildren().add(emptyCard("No previous exam attempts yet."));
            return;
        }

        for (ExamResult r : results) {
            previousExamsContainer.getChildren().add(buildPreviousCard(r));
        }
    }

    private Node buildPreviousCard(ExamResult r) {
        Label titleLbl = new Label(r.getExamTitle());
        titleLbl.getStyleClass().add("exam-card-title");

        Label scoreLbl = new Label(r.getScore() + "/" + r.getTotalMarks());
        scoreLbl.getStyleClass().add("exam-score-badge");

        HBox titleRow = new HBox(10, titleLbl,
                new Pane() {{ HBox.setHgrow(this, Priority.ALWAYS); }}, scoreLbl);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        Label pctLbl    = new Label(r.getFormattedPercentage() + "  " + r.getGrade());
        pctLbl.getStyleClass().add("exam-card-meta");

        Label dateLbl   = new Label("📅 " + r.getFormattedAttemptDate());
        dateLbl.getStyleClass().add("exam-card-meta");

        Label correctLbl = new Label("✅ " + r.getCorrectCount() + "  ❌ " + r.getWrongCount());
        correctLbl.getStyleClass().add("exam-card-meta");

        HBox metaRow = new HBox(20, pctLbl, dateLbl, correctLbl);
        metaRow.setAlignment(Pos.CENTER_LEFT);

        Button viewBtn = new Button("📋 View Result");
        viewBtn.getStyleClass().add("exam-view-btn");
        viewBtn.setOnAction(e -> openResultScreen(r));

        HBox btnRow = new HBox(viewBtn);
        btnRow.setAlignment(Pos.CENTER_RIGHT);

        VBox card = new VBox(10, titleRow, metaRow, btnRow);
        card.getStyleClass().add("exam-previous-card");
        card.setPadding(new Insets(18));

        AnimationUtil.fadeIn(card, 350, null);
        return card;
    }

    // ──────────────────────────────────────────────────────────────────
    // START EXAM
    // ──────────────────────────────────────────────────────────────────

    private void handleStartExam(Exam exam) {
        if (exam.isProtected()) {
            TextInputDialog dialog = new TextInputDialog();
            dialog.setTitle("Protected Exam");
            dialog.setHeaderText("Password Required");
            dialog.setContentText("Enter password:");
            styleDialog(dialog, 380);
            java.util.Optional<String> result = dialog.showAndWait();
            if (result.isPresent()) {
                if (!result.get().equals(exam.getPassword())) {
                    setStatus(availableStatusLabel, "❌ Incorrect password.", true);
                    return;
                }
            } else {
                return;
            }
        }

        setStatus(availableStatusLabel, "Starting exam...", false);
        new Thread(() -> {
            try {
                Exam fullExam = client.startExam(exam.getExamId(), username);
                Platform.runLater(() -> openTakingScreen(fullExam));
            } catch (ExamClient.ExamClientException e) {
                Platform.runLater(() -> setStatus(availableStatusLabel, "❌ " + e.getMessage(), true));
            }
        }, "start-exam-thread").start();
    }

    private void openTakingScreen(Exam exam) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("fxml files/exam_taking.fxml"));
            Node content = loader.load();
            ExamTakingController ctrl = loader.getController();
            ctrl.setRootPane(rootPane);
            ctrl.setExam(exam, client);
            AnimationUtil.contentTransition(rootPane.getCenter(), content, null);
            rootPane.setCenter(content);
        } catch (IOException e) {
            setStatus(availableStatusLabel, "❌ Failed to open exam: " + e.getMessage(), true);
        }
    }

    private void openResultScreen(ExamResult result) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("fxml files/exam_result.fxml"));
            Node content = loader.load();
            ExamResultController ctrl = loader.getController();
            ctrl.setRootPane(rootPane);
            ctrl.setResult(result);
            AnimationUtil.contentTransition(rootPane.getCenter(), content, null);
            rootPane.setCenter(content);
        } catch (IOException e) {
            setStatus(previousStatusLabel, "❌ " + e.getMessage(), true);
        }
    }

    // ──────────────────────────────────────────────────────────────────
    // BACK
    // ──────────────────────────────────────────────────────────────────

    @FXML
    private void handleBack(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("fxml files/exam_module.fxml"));
            Node content = loader.load();
            ExamModuleController ctrl = loader.getController();
            ctrl.setRootPane(rootPane);
            AnimationUtil.contentTransition(rootPane.getCenter(), content, null);
            rootPane.setCenter(content);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ──────────────────────────────────────────────────────────────────
    // HELPERS
    // ──────────────────────────────────────────────────────────────────

    private Node emptyCard(String text) {
        Label lbl = new Label(text);
        lbl.setStyle("-fx-text-fill: #64748b; -fx-font-size: 15px;");
        VBox box = new VBox(lbl);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(40));
        box.getStyleClass().add("exam-empty-card");
        return box;
    }

    private void setStatus(Label lbl, String msg, boolean isError) {
        if (lbl == null) return;
        lbl.setText(msg);
        lbl.setStyle(isError ? "-fx-text-fill: #fca5a5;" : "-fx-text-fill: #86efac;");
    }

    private void styleDialog(Dialog<?> dialog, double width) {
        dialog.getDialogPane().setPrefWidth(width);
        var cssUrl = getClass().getResource("css/alert.css");
        if (cssUrl != null) {
            dialog.getDialogPane().getStylesheets().add(cssUrl.toExternalForm());
        }
    }
}




