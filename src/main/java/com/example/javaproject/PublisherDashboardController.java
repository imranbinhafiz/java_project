package com.example.javaproject;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.beans.value.ChangeListener;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.io.IOException;
import java.net.URL;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * [NEW FILE] Controller for the Publisher Dashboard screen.
 *
 * Shows all exams published by the logged-in user with:
 *   - Exam title, type badge, status badge
 *   - Duration, total marks, participant count
 *   - "View Participants" button  → opens participant list overlay
 *   - "View Questions" button     → opens question/answer review overlay
 *
 * Accessed from ExamModuleController via "My Published Exams" button.
 */
public class PublisherDashboardController {

    @FXML private VBox   publishedExamsContainer;
    @FXML private Label  statusLabel;

    private BorderPane rootPane;
    private ExamClient client;
    private final String username = UserSession.getInstance().getUsername();

    private static final DateTimeFormatter DT_FMT =
            DateTimeFormatter.ofPattern("MMM dd, yyyy  HH:mm");

    public void setRootPane(BorderPane rp) {
        this.rootPane = rp;
        loadPublishedExams();
    }

    @FXML
    public void initialize() { /* data loaded after rootPane is set */ }

    // ── LOAD DATA ─────────────────────────────────────────────────────

    private void loadPublishedExams() {
        setStatus("Loading your exams…", false);
        publishedExamsContainer.getChildren().clear();

        new Thread(() -> {
            try {
                String serverHost = UserSession.getInstance().getServerHost();
                client = new ExamClient(serverHost, ExamServer.PORT);
                client.connect();
                List<Exam> published = client.getPublishedExams(username);

                Platform.runLater(() -> {
                    setStatus("", false);
                    if (published.isEmpty()) {
                        publishedExamsContainer.getChildren().add(emptyCard("You haven't published any exams yet."));
                    } else {
                        for (Exam exam : published) {
                            publishedExamsContainer.getChildren().add(buildPublisherCard(exam));
                        }
                    }
                });
            } catch (ExamClient.ExamClientException e) {
                Platform.runLater(() -> setStatus("❌ " + e.getMessage(), true));
            }
        }, "publisher-load-thread").start();
    }

    @FXML
    private void handleRefresh(ActionEvent event) {
        loadPublishedExams();
    }

    // ── BUILD EXAM CARD ───────────────────────────────────────────────

    private Node buildPublisherCard(Exam exam) {
        // Title row
        Label titleLbl = new Label(exam.getTitle());
        titleLbl.getStyleClass().add("exam-card-title");
        titleLbl.setWrapText(true);

        // Type badge
        boolean isPractice = exam.getExamType() == Exam.ExamType.PRACTICE;
        Label typeBadge = new Label(isPractice ? "📘 Practice" : "🔴 Real-Time");
        typeBadge.getStyleClass().add(isPractice ? "exam-badge-practice" : "exam-badge-live");

        // Status badge
        Exam.ExamStatus status = exam.getComputedStatus();
        Label statusBadge = buildStatusBadge(status);

        Pane spacer = new Pane();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox titleRow = new HBox(10, titleLbl, spacer, typeBadge, statusBadge);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        // Meta row
        Label durationLbl = new Label("⏱ " + exam.getDurationMinutes() + " min");
        durationLbl.getStyleClass().add("exam-card-meta");

        Label marksLbl = new Label("📊 " + exam.getTotalMarks() + " marks");
        marksLbl.getStyleClass().add("exam-card-meta");

        Label participantsLbl = new Label("👤 " + exam.getParticipantCount() + " participant(s)");
        participantsLbl.getStyleClass().add("exam-card-meta");

        Label examIdLbl = new Label("ID: " + exam.getExamId());
        examIdLbl.getStyleClass().add("exam-card-meta");
        examIdLbl.setStyle("-fx-text-fill: #475569; -fx-font-size: 11px;");

        HBox metaRow = new HBox(20, durationLbl, marksLbl, participantsLbl, examIdLbl);
        metaRow.setAlignment(Pos.CENTER_LEFT);

        // Schedule row (real-time only)
        HBox scheduleRow = new HBox();
        if (!isPractice) {
            String startStr = exam.getStartTime() != null ? exam.getStartTime().format(DT_FMT) : "Not set";
            String endStr   = exam.getEndTime()   != null ? exam.getEndTime().format(DT_FMT)   : "Not set";
            Label schedLbl  = new Label("🗓 " + startStr + "  →  " + endStr);
            schedLbl.getStyleClass().add("exam-card-meta");
            schedLbl.setStyle("-fx-text-fill: #a5b4fc;");
            scheduleRow.getChildren().add(schedLbl);
        }
        scheduleRow.setAlignment(Pos.CENTER_LEFT);

        // Action buttons
        Button viewParticipantsBtn = new Button("👥 View Participants");
        viewParticipantsBtn.getStyleClass().add("exam-action-btn");
        viewParticipantsBtn.setOnAction(e -> openParticipantsOverlay(exam));

        Button viewQuestionsBtn = new Button("📋 View Questions & Answers");
        viewQuestionsBtn.getStyleClass().add("exam-view-btn");
        viewQuestionsBtn.setOnAction(e -> openQuestionsOverlay(exam));

        Pane btnSpacer = new Pane();
        HBox.setHgrow(btnSpacer, Priority.ALWAYS);
        HBox btnRow = new HBox(10, btnSpacer, viewParticipantsBtn, viewQuestionsBtn);
        btnRow.setAlignment(Pos.CENTER_RIGHT);

        VBox card = new VBox(10, titleRow, metaRow);
        if (!isPractice && scheduleRow.getChildren().size() > 0) card.getChildren().add(scheduleRow);
        card.getChildren().add(btnRow);
        card.getStyleClass().add("publisher-exam-card");
        card.setPadding(new Insets(20));

        AnimationUtil.fadeIn(card, 350, null);
        return card;
    }

    private Label buildStatusBadge(Exam.ExamStatus status) {
        Label badge = new Label();
        switch (status) {
            case SCHEDULED -> {
                badge.setText("📅 Scheduled");
                badge.setStyle("-fx-text-fill: #fde68a; -fx-background-color: rgba(245,158,11,0.15);"
                        + "-fx-background-radius: 20px; -fx-padding: 4 12 4 12;"
                        + "-fx-border-color: rgba(245,158,11,0.4); -fx-border-width: 1; -fx-border-radius: 20px;"
                        + "-fx-font-size: 12px; -fx-font-weight: bold;");
            }
            case LIVE -> {
                badge.setText("🔴 Live");
                badge.setStyle("-fx-text-fill: #fca5a5; -fx-background-color: rgba(239,68,68,0.15);"
                        + "-fx-background-radius: 20px; -fx-padding: 4 12 4 12;"
                        + "-fx-border-color: rgba(239,68,68,0.4); -fx-border-width: 1; -fx-border-radius: 20px;"
                        + "-fx-font-size: 12px; -fx-font-weight: bold;");
            }
            case COMPLETED -> {
                badge.setText("✅ Completed");
                badge.setStyle("-fx-text-fill: #86efac; -fx-background-color: rgba(34,197,94,0.15);"
                        + "-fx-background-radius: 20px; -fx-padding: 4 12 4 12;"
                        + "-fx-border-color: rgba(34,197,94,0.4); -fx-border-width: 1; -fx-border-radius: 20px;"
                        + "-fx-font-size: 12px; -fx-font-weight: bold;");
            }
            default -> {  // PRACTICE
                badge.setText("📘 Practice");
                badge.setStyle("-fx-text-fill: #93c5fd; -fx-background-color: rgba(59,130,246,0.15);"
                        + "-fx-background-radius: 20px; -fx-padding: 4 12 4 12;"
                        + "-fx-border-color: rgba(59,130,246,0.4); -fx-border-width: 1; -fx-border-radius: 20px;"
                        + "-fx-font-size: 12px; -fx-font-weight: bold;");
            }
        }
        return badge;
    }

    // ── PARTICIPANTS OVERLAY ──────────────────────────────────────────

    private void openParticipantsOverlay(Exam exam) {
        StackPane overlay = buildOverlay();
        VBox dialog = buildOverlayDialog("👥 Participants – " + exam.getTitle(), 620);
        double dialogMaxHeight = Math.min(rootPane.getHeight() * 0.72, 560);
        dialog.setMaxHeight(dialogMaxHeight);

        Label loadingLbl = new Label("Loading…");
        loadingLbl.getStyleClass().add("exam-card-meta");
        dialog.getChildren().add(loadingLbl);

        Button closeBtn = new Button("✕ Close");
        closeBtn.getStyleClass().add("exam-back-btn");
        closeBtn.setOnAction(e -> closeOverlay(overlay));
        HBox closeBtnRow = new HBox(closeBtn);
        closeBtnRow.setAlignment(Pos.CENTER_RIGHT);

        overlay.getChildren().add(dialog);
        showOverlay(overlay);

        // Load data
        new Thread(() -> {
            try {
                List<ExamResult> participants = client.getExamParticipants(exam.getExamId());
                Platform.runLater(() -> {
                    dialog.getChildren().remove(loadingLbl);
                    if (participants.isEmpty()) {
                        Label none = new Label("No participants yet.");
                        none.getStyleClass().add("exam-card-meta");
                        dialog.getChildren().add(none);
                    } else {
                        // Table header
                        HBox header = buildParticipantRow(
                                "Student", "Score", "Percentage", "Grade", "Submitted At", true);
                        dialog.getChildren().add(header);

                        ScrollPane scroll = new ScrollPane();
                        scroll.setFitToWidth(true);
                        scroll.getStyleClass().add("exam-scroll-pane");
                        scroll.setMaxHeight(Math.max(220, dialogMaxHeight - 190));
                        VBox rows = new VBox(6);
                        rows.setPadding(new Insets(4, 0, 4, 0));
                        for (ExamResult r : participants) {
                            rows.getChildren().add(buildParticipantRow(
                                    r.getStudentUsername(),
                                    r.getScore() + "/" + r.getTotalMarks(),
                                    r.getFormattedPercentage(),
                                    r.getGrade(),
                                    r.getFormattedAttemptDate(),
                                    false));
                        }
                        scroll.setContent(rows);
                        dialog.getChildren().add(scroll);
                    }
                    dialog.getChildren().add(closeBtnRow);
                });
            } catch (ExamClient.ExamClientException e) {
                Platform.runLater(() -> {
                    dialog.getChildren().remove(loadingLbl);
                    Label errLbl = new Label("❌ " + e.getMessage());
                    errLbl.setStyle("-fx-text-fill: #fca5a5;");
                    dialog.getChildren().addAll(errLbl, closeBtnRow);
                });
            }
        }, "participants-load-thread").start();
    }

    private HBox buildParticipantRow(String name, String score, String pct,
                                     String grade, String date, boolean isHeader) {
        String style = isHeader
                ? "-fx-text-fill: #818cf8; -fx-font-weight: bold; -fx-font-size: 13px;"
                : "-fx-text-fill: #e2e8f0; -fx-font-size: 13px;";

        Label nameLbl  = makeCell(name,  200, style);
        Label scoreLbl = makeCell(score, 100, style);
        Label pctLbl   = makeCell(pct,   100, style);
        Label gradeLbl = makeCell(grade,  70, style);
        Label dateLbl  = makeCell(date,  180, style);

        HBox row = new HBox(10, nameLbl, scoreLbl, pctLbl, gradeLbl, dateLbl);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(8, 12, 8, 12));
        if (!isHeader) {
            row.setStyle("-fx-background-color: rgba(30,41,59,0.55); -fx-background-radius: 8px;");
        }
        return row;
    }

    private Label makeCell(String text, double minWidth, String style) {
        Label l = new Label(text);
        l.setMinWidth(minWidth);
        l.setStyle(style);
        l.setWrapText(false);
        return l;
    }

    // ── QUESTIONS OVERLAY ─────────────────────────────────────────────

    private void openQuestionsOverlay(Exam exam) {
        StackPane overlay = buildOverlay();
        VBox dialog = buildOverlayDialog("📋 Questions & Answers – " + exam.getTitle(), 640);
        double dialogMaxHeight = Math.min(rootPane.getHeight() * 0.75, 600);
        dialog.setMaxHeight(dialogMaxHeight);

        Label loadingLbl = new Label("Loading…");
        loadingLbl.getStyleClass().add("exam-card-meta");
        dialog.getChildren().add(loadingLbl);

        Button closeBtn = new Button("✕ Close");
        closeBtn.getStyleClass().add("exam-back-btn");
        closeBtn.setOnAction(e -> closeOverlay(overlay));

        overlay.getChildren().add(dialog);
        showOverlay(overlay);

        new Thread(() -> {
            try {
                Exam fullExam = client.getExamQuestions(exam.getExamId(), username);
                Platform.runLater(() -> {
                    dialog.getChildren().remove(loadingLbl);

                    ScrollPane scroll = new ScrollPane();
                    scroll.setFitToWidth(true);
                    scroll.getStyleClass().add("exam-scroll-pane");
                    scroll.setMaxHeight(Math.max(240, dialogMaxHeight - 160));

                    VBox questionList = new VBox(12);
                    questionList.setPadding(new Insets(4, 0, 4, 0));
                    List<Question> questions = fullExam.getQuestions();
                    for (int i = 0; i < questions.size(); i++) {
                        questionList.getChildren().add(buildQuestionCard(i + 1, questions.get(i)));
                    }
                    scroll.setContent(questionList);
                    dialog.getChildren().addAll(scroll, new HBox(closeBtn) {{ setAlignment(Pos.CENTER_RIGHT); }});
                });
            } catch (ExamClient.ExamClientException e) {
                Platform.runLater(() -> {
                    dialog.getChildren().remove(loadingLbl);
                    Label errLbl = new Label("❌ " + e.getMessage());
                    errLbl.setStyle("-fx-text-fill: #fca5a5;");
                    dialog.getChildren().addAll(errLbl, new HBox(closeBtn) {{ setAlignment(Pos.CENTER_RIGHT); }});
                });
            }
        }, "questions-load-thread").start();
    }

    private Node buildQuestionCard(int number, Question q) {
        Label numLbl = new Label("Q" + number);
        numLbl.setStyle("-fx-text-fill: #818cf8; -fx-font-size: 13px; -fx-font-weight: bold;");

        Label qTextLbl = new Label(q.getQuestionText());
        qTextLbl.setWrapText(true);
        qTextLbl.setStyle("-fx-text-fill: #f8fafc; -fx-font-size: 14px; -fx-font-weight: bold;");

        Label marksLbl = new Label("(" + q.getMarks() + " mark" + (q.getMarks() > 1 ? "s)" : ")"));
        marksLbl.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px;");

        HBox qHeader = new HBox(10, numLbl, qTextLbl, new Pane() {{ HBox.setHgrow(this, Priority.ALWAYS); }}, marksLbl);
        qHeader.setAlignment(Pos.CENTER_LEFT);

        VBox optionsList = new VBox(4);
        String[] letters = {"A", "B", "C", "D"};
        for (int i = 0; i < q.getOptions().size(); i++) {
            boolean isCorrect = (i == q.getCorrectOptionIndex());
            String text   = letters[i] + ".  " + q.getOptions().get(i);
            Label optLbl  = new Label(isCorrect ? "✅ " + text : "    " + text);
            optLbl.setWrapText(true);
            optLbl.setStyle(isCorrect
                    ? "-fx-text-fill: #86efac; -fx-font-weight: bold; -fx-font-size: 13px;"
                    : "-fx-text-fill: #94a3b8; -fx-font-size: 13px;");
            optionsList.getChildren().add(optLbl);
        }

        VBox card = new VBox(8, qHeader, optionsList);
        card.setStyle("-fx-background-color: rgba(30,41,59,0.7); -fx-background-radius: 12px;"
                + "-fx-border-color: rgba(99,102,241,0.2); -fx-border-width: 1; -fx-border-radius: 12px;");
        card.setPadding(new Insets(14, 16, 14, 16));
        return card;
    }

    // ── BACK ──────────────────────────────────────────────────────────

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

    // ── HELPERS ───────────────────────────────────────────────────────

    private StackPane buildOverlay() {
        StackPane overlay = new StackPane();
        overlay.setStyle("-fx-background-color: rgba(0,0,0,0.65);");
        // Click outside to close
        overlay.setOnMouseClicked(e -> {
            if (e.getTarget() == overlay) closeOverlay(overlay);
        });
        return overlay;
    }

    private VBox buildOverlayDialog(String title, double maxWidth) {
        Label titleLbl = new Label(title);
        titleLbl.getStyleClass().add("exam-dialog-title");
        titleLbl.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #f8fafc;");
        titleLbl.setWrapText(true);

        VBox dialog = new VBox(14, titleLbl);
        dialog.getStyleClass().add("exam-dialog");
        dialog.setStyle("-fx-background-color: linear-gradient(to bottom right, #1e293b, #334155);"
                + "-fx-background-radius: 18px;"
                + "-fx-border-color: rgba(99,102,241,0.35);"
                + "-fx-border-width: 1.5;"
                + "-fx-border-radius: 18px;");
        dialog.setMaxWidth(maxWidth);
        dialog.setPadding(new Insets(20));
        dialog.setAlignment(Pos.TOP_LEFT);
        return dialog;
    }

    private void showOverlay(StackPane overlay) {
        if (rootPane == null) {
            return;
        }
        attachExamStyles(overlay);
        configureOverlayBounds(overlay);
        rootPane.getChildren().add(overlay);
        syncOverlayBounds(overlay);
        overlay.toFront();
    }

    private void configureOverlayBounds(StackPane overlay) {
        overlay.setManaged(false);

        ChangeListener<Number> widthListener = (obs, oldV, newV) -> syncOverlayBounds(overlay);
        ChangeListener<Number> heightListener = (obs, oldV, newV) -> syncOverlayBounds(overlay);
        rootPane.widthProperty().addListener(widthListener);
        rootPane.heightProperty().addListener(heightListener);

        overlay.getProperties().put("overlay-width-listener", widthListener);
        overlay.getProperties().put("overlay-height-listener", heightListener);
    }

    private void syncOverlayBounds(StackPane overlay) {
        overlay.resizeRelocate(0, 0, rootPane.getWidth(), rootPane.getHeight());
    }

    @SuppressWarnings("unchecked")
    private void closeOverlay(StackPane overlay) {
        Object widthListenerObj = overlay.getProperties().remove("overlay-width-listener");
        Object heightListenerObj = overlay.getProperties().remove("overlay-height-listener");

        if (widthListenerObj instanceof ChangeListener<?> widthListener) {
            rootPane.widthProperty().removeListener((ChangeListener<? super Number>) widthListener);
        }
        if (heightListenerObj instanceof ChangeListener<?> heightListener) {
            rootPane.heightProperty().removeListener((ChangeListener<? super Number>) heightListener);
        }

        rootPane.getChildren().remove(overlay);
    }

    private void attachExamStyles(Parent parent) {
        URL cssUrl = getClass().getResource("css/exam.css");
        if (cssUrl == null) return;
        String css = cssUrl.toExternalForm();
        if (!parent.getStylesheets().contains(css)) {
            parent.getStylesheets().add(css);
        }
    }

    private Node emptyCard(String text) {
        Label lbl = new Label(text);
        lbl.setStyle("-fx-text-fill: #64748b; -fx-font-size: 15px;");
        VBox box = new VBox(lbl);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(40));
        box.getStyleClass().add("exam-empty-card");
        return box;
    }

    private void setStatus(String msg, boolean isError) {
        if (statusLabel == null) return;
        statusLabel.setText(msg);
        statusLabel.setStyle(isError ? "-fx-text-fill: #fca5a5;" : "-fx-text-fill: #86efac;");
    }
}

