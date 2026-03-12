package com.example.javaproject;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.List;

/**
 * Select Exam scene — host picks any published exam for the room.
 * Protected exams show a password entry; the password is verified
 * server-side before the exam is accepted.
 */
public class ChallengeSelectExamController {

    @FXML private VBox      examsBox;
    @FXML private TextField searchField;
    @FXML private Label     statusLabel;

    private ChallengeClient          client;
    private String                   username;
    private String                   roomId;
    private BorderPane               rootPane;
    private ChallengeRoomController  roomCtrl;

    private List<Exam> allExams;

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
                    case "CH_EXAMS_LIST" -> {
                        allExams = ExamJsonUtil.parseExamList(payload);
                        filterAndDisplay(searchField.getText());
                    }
                    case "CH_SELECT_EXAM_OK"   -> {
                        statusLabel.getStyleClass().removeAll("ch-exam-none");
                        statusLabel.getStyleClass().add("ch-status-ok");
                        statusLabel.setText("✓  Exam selected successfully!");
                    }
                    case "CH_SELECT_EXAM_FAIL" -> {
                        statusLabel.getStyleClass().removeAll("ch-status-ok");
                        statusLabel.getStyleClass().add("ch-exam-none");
                        statusLabel.setText("⚠  " + ChallengeClient.parseStr(payload, "message"));
                        // Re-enable all select buttons so host can try again
                        filterAndDisplay(searchField.getText());
                    }
                }
            });
        });
        client.getChallengeExams(roomId);
    }

    @FXML public void initialize() {}

    @FXML
    private void handleSearch() {
        if (allExams != null) filterAndDisplay(searchField.getText());
    }

    private void filterAndDisplay(String query) {
        examsBox.getChildren().clear();
        String q = (query == null) ? "" : query.toLowerCase().trim();
        if (allExams == null || allExams.isEmpty()) {
            Label lbl = new Label("No exams found. Make sure an exam is published and its creator is not in this room.");
            lbl.getStyleClass().add("ch-meta-lbl");
            lbl.setWrapText(true);
            examsBox.getChildren().add(lbl);
            return;
        }
        boolean any = false;
        for (Exam exam : allExams) {
            if (!q.isEmpty()
                    && !exam.getTitle().toLowerCase().contains(q)
                    && !exam.getExamId().toLowerCase().contains(q)) continue;
            examsBox.getChildren().add(buildExamCard(exam));
            any = true;
        }
        if (!any) {
            Label lbl = new Label("No exams match your search.");
            lbl.getStyleClass().add("ch-meta-lbl");
            examsBox.getChildren().add(lbl);
        }
    }

    private VBox buildExamCard(Exam exam) {
        VBox card = new VBox(10);
        card.getStyleClass().add("ch-exam-row");

        // ── Top row: title + badges ────────────────────────────────
        HBox topRow = new HBox(8);
        topRow.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label(exam.getTitle());
        title.setStyle("-fx-font-size:15px; -fx-font-weight:bold; -fx-text-fill:#f8fafc;");
        HBox.setHgrow(title, Priority.ALWAYS);
        topRow.getChildren().add(title);

        if (exam.isProtected()) {
            Label protBadge = new Label("🔒 Protected");
            protBadge.getStyleClass().add("ch-badge-protected");
            topRow.getChildren().add(protBadge);
        }
        if (exam.getExamType() == Exam.ExamType.REAL_TIME) {
            Label liveBadge = new Label("⚡ Live");
            liveBadge.getStyleClass().add("ch-badge-ingame");
            topRow.getChildren().add(liveBadge);
        }

        // ── Meta line ─────────────────────────────────────────────
        Label meta = new Label(
                "ID: " + exam.getExamId()
                + "  ·  Creator: " + exam.getPublisherUsername()
                + "  ·  " + exam.getQuestions().size() + " questions"
                + "  ·  " + exam.getTotalMarks() + " marks");
        meta.getStyleClass().add("ch-meta-lbl");

        // ── Password panel (only for protected exams) ─────────────
        // We store the password field reference so the Select button can read it
        PasswordField pwField;
        VBox pwPanel = new VBox(6);

        if (exam.isProtected()) {
            pwPanel.getStyleClass().add("ch-pw-panel");
            pwPanel.setVisible(true);
            pwPanel.setManaged(true);

            Label pwLbl = new Label("EXAM PASSWORD");
            pwLbl.getStyleClass().add("ch-sec-lbl");

            pwField = new PasswordField();
            pwField.getStyleClass().add("ch-field");
            pwField.setPromptText("Enter the exam password to unlock…");

            pwPanel.getChildren().addAll(pwLbl, pwField);
        } else {
            pwField = null;
            pwPanel.setVisible(false);
            pwPanel.setManaged(false);
        }

        // ── Select button ─────────────────────────────────────────
        Button selectBtn = new Button("Select");
        selectBtn.getStyleClass().add("ch-btn-gold");

        selectBtn.setOnAction(e -> {
            String password = "";
            if (exam.isProtected()) {
                if (pwField == null || pwField.getText().trim().isEmpty()) {
                    setStatus("⚠  Please enter the exam password.", false);
                    return;
                }
                password = pwField.getText().trim();
            }
            // Send selection to server (password included for protected exams)
            client.selectExam(roomId, exam.getExamId(), exam.getTitle(), password);
            selectBtn.setText("Selected ✓");
            selectBtn.setDisable(true);
            setStatus("Selecting \"" + exam.getTitle() + "\"…", true);
        });

        HBox btnRow = new HBox();
        btnRow.setAlignment(Pos.CENTER_RIGHT);
        btnRow.getChildren().add(selectBtn);

        card.getChildren().addAll(topRow, meta);
        if (exam.isProtected()) card.getChildren().add(pwPanel);
        card.getChildren().add(btnRow);

        return card;
    }

    private void setStatus(String text, boolean ok) {
        statusLabel.getStyleClass().removeAll("ch-status-ok", "ch-exam-none");
        statusLabel.getStyleClass().add(ok ? "ch-status-ok" : "ch-exam-none");
        statusLabel.setText(text);
    }

    @FXML
    private void handleBack() {
        roomCtrl.restoreRoomView();
    }
}
