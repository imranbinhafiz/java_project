package com.example.javaproject;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.image.ImageView;
import javafx.scene.image.Image;
import javafx.util.Duration;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Controller for the Exam-Taking screen.
 *
 * Features:
 * - Question card with styled MCQ option toggle buttons
 * - Countdown timer (auto-submits on expiry)
 * - Previous / Next navigation
 * - Progress indicator
 * - Custom confirmation before submit
 */
public class ExamTakingController {

    // ── FXML ───────────────────────────────────────────────────────────
    @FXML private Label  examTitleLabel;
    @FXML private Label  publisherLabel;
    @FXML private Label  timerLabel;
    @FXML private Label  progressLabel;
    @FXML private Label  questionNumberLabel;
    @FXML private Label  questionTextLabel;
    @FXML private ImageView questionImageView;
    @FXML private VBox   optionsContainer;
    @FXML private Button prevBtn;
    @FXML private Button nextBtn;
    @FXML private Button submitBtn;
    @FXML private HBox   questionNavBar;  // dot-per-question nav

    // ── State ─────────────────────────────────────────────────────────
    private BorderPane  rootPane;
    private Exam        exam;
    private ExamClient  client;
    private List<Question> questions;
    private int[]          selectedAnswers;  // -1 = unanswered
    private int            currentIndex = 0;
    private Timeline       countdownTimeline;
    private int            remainingSeconds;
    private List<ToggleButton> currentOptionBtns = new ArrayList<>();
    private List<Label>        navDots = new ArrayList<>();

    /** Optional callback invoked after exam submission (used by challenge mode). */
    private Runnable onExamComplete;

    private final String username = UserSession.getInstance().getUsername();

    public void setRootPane(BorderPane rp) { this.rootPane = rp; }

    /** Set a callback that runs after the exam result is received (e.g. challenge game end). */
    public void setOnExamComplete(Runnable callback) { this.onExamComplete = callback; }

    public void setExam(Exam exam, ExamClient client) {
        this.exam   = exam;
        this.client = client;
        this.questions = exam.getQuestions();
        this.selectedAnswers = new int[questions.size()];
        for (int i = 0; i < selectedAnswers.length; i++) selectedAnswers[i] = -1;

        examTitleLabel.setText(exam.getTitle());
        publisherLabel.setText("By: " + exam.getPublisherUsername());

        remainingSeconds = exam.getDurationMinutes() * 60;
        buildNavDots();
        showQuestion(0);
        startTimer();
    }

    @FXML
    public void initialize() {
        // UI configured after setExam
    }

    // ──────────────────────────────────────────────────────────────────
    // TIMER
    // ──────────────────────────────────────────────────────────────────

    private void startTimer() {
        countdownTimeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            remainingSeconds--;
            updateTimerLabel();
            if (remainingSeconds <= 0) {
                countdownTimeline.stop();
                autoSubmit();
            }
        }));
        countdownTimeline.setCycleCount(Timeline.INDEFINITE);
        countdownTimeline.play();
        updateTimerLabel();
    }

    private void updateTimerLabel() {
        int mins = remainingSeconds / 60;
        int secs = remainingSeconds % 60;
        String text = String.format("%02d:%02d", mins, secs);
        timerLabel.setText("⏱ " + text);
        if (remainingSeconds <= 60) {
            timerLabel.setStyle("-fx-text-fill: #fca5a5; -fx-font-weight: bold; -fx-font-size: 18px;");
        } else if (remainingSeconds <= 300) {
            timerLabel.setStyle("-fx-text-fill: #fde68a; -fx-font-weight: bold; -fx-font-size: 18px;");
        } else {
            timerLabel.setStyle("-fx-text-fill: #86efac; -fx-font-weight: bold; -fx-font-size: 18px;");
        }
    }

    // ──────────────────────────────────────────────────────────────────
    // QUESTION NAVIGATION
    // ──────────────────────────────────────────────────────────────────

    private void buildNavDots() {
        if (questionNavBar == null) return;
        questionNavBar.getChildren().clear();
        navDots.clear();
        questionNavBar.setSpacing(6);
        questionNavBar.setAlignment(Pos.CENTER);

        for (int i = 0; i < questions.size(); i++) {
            final int idx = i;
            Label dot = new Label(String.valueOf(i + 1));
            dot.getStyleClass().add("exam-nav-dot");
            dot.setOnMouseClicked(e -> showQuestion(idx));
            navDots.add(dot);
            questionNavBar.getChildren().add(dot);
        }
    }

    private void showQuestion(int index) {
        currentIndex = index;
        Question q   = questions.get(index);

        questionNumberLabel.setText("Question " + (index + 1) + " of " + questions.size());
        questionTextLabel.setText(q.getQuestionText());
        progressLabel.setText(getAnsweredCount() + "/" + questions.size() + " answered");

        if (q.getImageBase64() != null && !q.getImageBase64().isEmpty()) {
            try {
                byte[] bytes = java.util.Base64.getDecoder().decode(q.getImageBase64());
                questionImageView.setImage(new Image(new java.io.ByteArrayInputStream(bytes)));
                questionImageView.setVisible(true);
                questionImageView.setManaged(true);
            } catch (Exception e) {
                questionImageView.setVisible(false);
                questionImageView.setManaged(false);
            }
        } else {
            questionImageView.setVisible(false);
            questionImageView.setManaged(false);
        }

        // Rebuild options
        optionsContainer.getChildren().clear();
        currentOptionBtns.clear();

        ToggleGroup group = new ToggleGroup();
        String[] letters = {"A", "B", "C", "D"};

        for (int i = 0; i < q.getOptions().size(); i++) {
            final int optIdx = i;
            ToggleButton btn = new ToggleButton(letters[i] + ".  " + q.getOptions().get(i));
            btn.getStyleClass().add("exam-option-btn");
            btn.setMaxWidth(Double.MAX_VALUE);
            btn.setToggleGroup(group);

            if (selectedAnswers[index] == i) btn.setSelected(true);

            btn.selectedProperty().addListener((obs, wasOn, isOn) -> {
                if (isOn) {
                    selectedAnswers[index] = optIdx;
                    updateNavDot(index, true);
                    progressLabel.setText(getAnsweredCount() + "/" + questions.size() + " answered");
                }
                btn.getStyleClass().removeAll("exam-option-selected");
                if (isOn) btn.getStyleClass().add("exam-option-selected");
            });

            if (btn.isSelected()) btn.getStyleClass().add("exam-option-selected");

            currentOptionBtns.add(btn);
            optionsContainer.getChildren().add(btn);
        }

        // Nav buttons
        prevBtn.setDisable(index == 0);
        nextBtn.setDisable(index == questions.size() - 1);
        nextBtn.setText(index == questions.size() - 1 ? "Last" : "Next ▶");

        // Highlight active dot
        for (int i = 0; i < navDots.size(); i++) {
            navDots.get(i).getStyleClass().removeAll("exam-nav-dot-active");
            if (i == index) navDots.get(i).getStyleClass().add("exam-nav-dot-active");
        }
    }

    private void updateNavDot(int index, boolean answered) {
        if (index >= 0 && index < navDots.size()) {
            navDots.get(index).getStyleClass().removeAll("exam-nav-dot-answered");
            if (answered) navDots.get(index).getStyleClass().add("exam-nav-dot-answered");
        }
    }

    @FXML private void handlePrev(ActionEvent e) { if (currentIndex > 0) showQuestion(currentIndex - 1); }
    @FXML private void handleNext(ActionEvent e)  {
        if (currentIndex < questions.size() - 1) showQuestion(currentIndex + 1);
    }

    // ──────────────────────────────────────────────────────────────────
    // SUBMIT
    // ──────────────────────────────────────────────────────────────────

    @FXML
    private void handleSubmit(ActionEvent event) {
        int unanswered = (int) countUnAnswered();
        String msg = unanswered > 0
                ? unanswered + " question(s) unanswered. Submit anyway?"
                : "Are you sure you want to submit?";
        showConfirmDialog("Submit Exam", msg, this::doSubmit);
    }

    private void autoSubmit() {
        Platform.runLater(() -> {
            showInfoDialog("⏰ Time's Up!", "Your exam has been automatically submitted.", this::doSubmit);
        });
    }

    private void doSubmit() {
        if (countdownTimeline != null) countdownTimeline.stop();
        submitBtn.setDisable(true);

        List<Integer> answers = new ArrayList<>();
        for (int a : selectedAnswers) answers.add(a);

        new Thread(() -> {
            try {
                ExamResult result = client.submitExam(exam.getExamId(), username, answers);
                Platform.runLater(() -> openResultScreen(result));
            } catch (ExamClient.ExamClientException e) {
                Platform.runLater(() -> {
                    submitBtn.setDisable(false);
                    showInfoDialog("Error", "Failed to submit: " + e.getMessage(), null);
                });
            }
        }, "submit-thread").start();
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
            // Notify challenge mode that exam is complete
            if (onExamComplete != null) onExamComplete.run();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ──────────────────────────────────────────────────────────────────
    // ALERT DIALOGS
    // ──────────────────────────────────────────────────────────────────

    private void showConfirmDialog(String title, String message, Runnable onConfirm) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message);

        ButtonType submitType = new ButtonType("Submit", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelType = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(submitType, cancelType);

        styleAlertDialog(alert, 360);
        alert.showAndWait().ifPresent(result -> {
            if (result == submitType && onConfirm != null) {
                onConfirm.run();
            }
        });
    }

    private void showInfoDialog(String title, String message, Runnable onOk) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message);
        alert.getButtonTypes().setAll(new ButtonType("OK", ButtonBar.ButtonData.OK_DONE));

        styleAlertDialog(alert, 360);
        alert.showAndWait().ifPresent(result -> {
            if (onOk != null) {
                onOk.run();
            }
        });
    }

    private void styleAlertDialog(Alert alert, double width) {
        alert.getDialogPane().setPrefWidth(width);
        var cssUrl = getClass().getResource("css/alert.css");
        if (cssUrl != null) {
            alert.getDialogPane().getStylesheets().add(cssUrl.toExternalForm());
        }
    }
    private int getAnsweredCount() {
        int count = 0;
        for (int a : selectedAnswers) if (a != -1) count++;
        return count;
    }

    private long countUnAnswered() {
        int count = 0;
        for (int a : selectedAnswers) if (a == -1) count++;
        return count;
    }
}


