package com.example.javaproject;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.io.IOException;

/**
 * Controller for the Results Screen.
 *
 * ═══════════════════════════════════════════════════════════════
 * CHANGES FROM ORIGINAL:
 *   [ADDED] Statistics section: accuracy %, attempted vs unanswered count.
 *   [ADDED] Publisher name shown prominently in summary card.
 *   [FIXED] Breakdown always shown (server now sends QuestionResult data).
 *   [ADDED] Unanswered question count in stats row.
 * ═══════════════════════════════════════════════════════════════
 */
public class ExamResultController {

    @FXML private Label examTitleLabel;
    @FXML private Label scoreLabel;
    @FXML private Label percentageLabel;
    @FXML private Label gradeLabel;
    @FXML private Label correctLabel;
    @FXML private Label wrongLabel;
    @FXML private Label attemptDateLabel;
    @FXML private Label publisherLabel;
    @FXML private VBox  breakdownContainer;

    // [ADDED] Statistics labels – must exist in FXML or handled safely
    @FXML private Label accuracyLabel;
    @FXML private Label attemptedLabel;
    @FXML private Label unansweredLabel;

    private BorderPane rootPane;
    private ExamResult result;

    public void setRootPane(BorderPane rp) { this.rootPane = rp; }

    public void setResult(ExamResult result) {
        this.result = result;
        populateUI();
    }

    @FXML
    public void initialize() {}

    // ── POPULATE ──────────────────────────────────────────────────────

    private void populateUI() {
        examTitleLabel.setText(result.getExamTitle());
        scoreLabel.setText(result.getScore() + " / " + result.getTotalMarks());
        percentageLabel.setText(result.getFormattedPercentage());
        gradeLabel.setText(result.getGrade());
        correctLabel.setText("✅ Correct: " + result.getCorrectCount());
        wrongLabel.setText("❌ Wrong: " + result.getWrongCount());
        attemptDateLabel.setText("📅 " + result.getFormattedAttemptDate());
        publisherLabel.setText("Published by: " + result.getPublisherUsername());

        // Grade colour
        double pct        = result.getPercentage();
        String gradeColor = pct >= 70 ? "#86efac" : pct >= 50 ? "#fde68a" : "#fca5a5";
        gradeLabel.setStyle("-fx-text-fill: " + gradeColor
                + "; -fx-font-size: 36px; -fx-font-weight: bold;");

        // [ADDED] Statistics section
        int total      = result.getTotalQuestions();
        int attempted  = result.getCorrectCount() + result.getWrongCount();
        int unanswered = total - attempted;
        double accuracy = attempted > 0
                ? (result.getCorrectCount() / (double) attempted) * 100.0 : 0.0;

        safeSet(accuracyLabel,   String.format("🎯 Accuracy: %.1f%%", accuracy));
        safeSet(attemptedLabel,  "📝 Attempted: " + attempted + "/" + total);
        safeSet(unansweredLabel, "⬜ Unanswered: " + unanswered);

        // Question breakdown
        buildBreakdown();
    }

    private void buildBreakdown() {
        breakdownContainer.getChildren().clear();
        var qResults = result.getQuestionResults();

        if (qResults == null || qResults.isEmpty()) {
            Label noBreakdown = new Label("No detailed breakdown available. "
                    + "(Re-submit an exam to see per-question review.)");
            noBreakdown.setStyle("-fx-text-fill: #64748b; -fx-font-size: 13px;");
            noBreakdown.setWrapText(true);
            breakdownContainer.getChildren().add(noBreakdown);
            return;
        }

        for (int i = 0; i < qResults.size(); i++) {
            breakdownContainer.getChildren().add(buildBreakdownRow(i + 1, qResults.get(i)));
        }
    }

    private Node buildBreakdownRow(int number, ExamResult.QuestionResult qr) {
        Label numLbl = new Label("Q" + number);
        numLbl.setStyle("-fx-text-fill: #818cf8; -fx-font-size: 13px;"
                + "-fx-font-weight: bold; -fx-min-width: 32px;");

        Label qText = new Label(qr.getQuestionText());
        qText.setWrapText(true);
        qText.setStyle("-fx-text-fill: #f8fafc; -fx-font-size: 14px; -fx-font-weight: 600;");

        String yourText = (qr.getSelectedOption() != null && !qr.getSelectedOption().isBlank())
                ? qr.getSelectedOption() : "Not answered";
        boolean answered = !"Not answered".equals(yourText);

        Label yourAns = new Label("Your answer: " + yourText);
        yourAns.setWrapText(true);
        yourAns.setStyle("-fx-text-fill: "
                + (qr.isCorrect() ? "#86efac" : (answered ? "#fca5a5" : "#94a3b8"))
                + "; -fx-font-size: 13px;");

        VBox textCol = new VBox(4, qText, yourAns);

        // Always show correct answer for wrong/unanswered items
        if (!qr.isCorrect()) {
            Label correctAns = new Label("✔ Correct: " + qr.getCorrectOption());
            correctAns.setWrapText(true);
            correctAns.setStyle("-fx-text-fill: #86efac; -fx-font-size: 13px; -fx-font-weight: 600;");
            textCol.getChildren().add(correctAns);
        }
        HBox.setHgrow(textCol, Priority.ALWAYS);

        Label icon = new Label(qr.isCorrect() ? "✅" : (answered ? "❌" : "⬜"));
        icon.setStyle("-fx-font-size: 18px;");

        HBox row = new HBox(12, numLbl, textCol, icon);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(14, 16, 14, 16));
        row.getStyleClass().add(qr.isCorrect() ? "exam-breakdown-correct" : "exam-breakdown-wrong");

        AnimationUtil.fadeIn(row, 200, null);
        return row;
    }

    // ── BACK NAVIGATION ───────────────────────────────────────────────

    @FXML
    private void handleBackToExams(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("fxml files/exam_participate.fxml"));
            Node content = loader.load();
            ParticipateExamController ctrl = loader.getController();
            ctrl.setRootPane(rootPane);
            AnimationUtil.contentTransition(rootPane.getCenter(), content, null);
            rootPane.setCenter(content);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void handleBackToHome(ActionEvent event) {
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

    // ── HELPERS ───────────────────────────────────────────────────────

    /** Safely sets label text (label may be null if not in FXML). */
    private void safeSet(Label label, String text) {
        if (label != null) label.setText(text);
    }
}
