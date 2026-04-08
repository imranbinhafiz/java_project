package com.example.javaproject;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Controller for the Publish Exam workflow.
 *
 * Step 1 – Create Exam form (this controller manages the form).
 * Step 2 – Select Participants (shown after successful server publish).
 */
public class PublishExamController {

    // ── FXML – Exam Form ──────────────────────────────────────────────
    @FXML private TextField         examTitleField;
    @FXML private TextArea          examDescField;
    @FXML private TextField          durationField;
    @FXML private TextField          totalMarksField;
    @FXML private CheckBox          negativeMarkingCheck;
    @FXML private CheckBox          shuffleOptionsCheck;
    @FXML private RadioButton       realTimeRadio;
    @FXML private RadioButton       practiceRadio;
    @FXML private VBox              realTimeFields;
    @FXML private DatePicker        startDatePicker;
    @FXML private Spinner<Integer>  startHourSpinner;
    @FXML private Spinner<Integer>  startMinSpinner;
    @FXML private DatePicker        endDatePicker;
    @FXML private Spinner<Integer>  endHourSpinner;
    @FXML private Spinner<Integer>  endMinSpinner;
    @FXML private VBox              questionsContainer;
    @FXML private Label             statusLabel;
    @FXML private Button            publishButton;

    // ── State ─────────────────────────────────────────────────────────
    private BorderPane rootPane;
    private final List<QuestionFormRow> questionRows = new ArrayList<>();
    private ExamClient client;

    // ── Setters ───────────────────────────────────────────────────────
    public void setRootPane(BorderPane rootPane) { this.rootPane = rootPane; }

    // ──────────────────────────────────────────────────────────────────

    @FXML
    public void initialize() {
        // Default values for time spinners
        startHourSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 23, 9));
        startMinSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 59, 0));
        endHourSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 23, 10));
        endMinSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 59, 0));

        // Exam-type toggle
        ToggleGroup typeGroup = new ToggleGroup();
        realTimeRadio.setToggleGroup(typeGroup);
        practiceRadio.setToggleGroup(typeGroup);
        practiceRadio.setSelected(true);
        realTimeFields.setVisible(false);
        realTimeFields.setManaged(false);

        typeGroup.selectedToggleProperty().addListener((obs, o, n) -> {
            boolean rt = (n == realTimeRadio);
            realTimeFields.setVisible(rt);
            realTimeFields.setManaged(rt);
        });

        // Add first question row by default
        addQuestionRow();

        setStatus("", false);
    }

    // ──────────────────────────────────────────────────────────────────
    // QUESTION ROW MANAGEMENT
    // ──────────────────────────────────────────────────────────────────

    @FXML
    private void handleAddQuestion(ActionEvent event) {
        addQuestionRow();
    }

    private void addQuestionRow() {
        QuestionFormRow row = new QuestionFormRow(questionRows.size() + 1, this::removeQuestionRow);
        questionRows.add(row);
        questionsContainer.getChildren().add(row.getRoot());
        AnimationUtil.fadeIn(row.getRoot(), 300, null);
    }

    private void removeQuestionRow(QuestionFormRow row) {
        questionRows.remove(row);
        questionsContainer.getChildren().remove(row.getRoot());
        // Re-number
        for (int i = 0; i < questionRows.size(); i++) {
            questionRows.get(i).setNumber(i + 1);
        }
    }

    // ──────────────────────────────────────────────────────────────────
    // PUBLISH
    // ──────────────────────────────────────────────────────────────────

    @FXML
    private void handlePublish(ActionEvent event) {
        if (!validateForm()) return;

        Exam exam = buildExam();
        publishButton.setDisable(true);
        setStatus("Connecting to server...", false);

        new Thread(() -> {
            try {
                String serverHost = UserSession.getInstance().getServerHost();
                client = new ExamClient(serverHost, ExamServer.PORT);
                client.connect();
                String examId = client.createExam(exam);
                exam.setExamId(examId);

                Platform.runLater(() -> {
                    setStatus("✅ Exam published! ID: " + examId, false);
                    publishButton.setDisable(false);
                    openParticipantSelection(exam);
                });
            } catch (ExamClient.ExamClientException e) {
                Platform.runLater(() -> {
                    setStatus("❌ " + e.getMessage(), true);
                    publishButton.setDisable(false);
                });
            }
        }, "exam-publish-thread").start();
    }

    private void openParticipantSelection(Exam exam) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("fxml files/exam_participants.fxml"));
            Node content = loader.load();
            SelectParticipantsController ctrl = loader.getController();
            ctrl.setRootPane(rootPane);
            ctrl.setExam(exam, client);
            AnimationUtil.contentTransition(rootPane.getCenter(), content, null);
            rootPane.setCenter(content);
        } catch (IOException e) {
            setStatus("❌ Failed to open participant screen: " + e.getMessage(), true);
        }
    }

    // ──────────────────────────────────────────────────────────────────
    // FORM VALIDATION & BUILD
    // ──────────────────────────────────────────────────────────────────

    private boolean validateForm() {
        String title = examTitleField.getText().trim();
        if (title.isEmpty()) {
            setStatus("❌ Exam title is required.", true);
            AnimationUtil.pulse(examTitleField, 1.04);
            return false;
        }
        
        // Validate duration
        int duration = parseIntField(durationField, 60, 1, 300, "Duration");
        if (duration < 0) {
            setStatus("❌ Invalid duration. Please enter a number between 1 and 300.", true);
            AnimationUtil.pulse(durationField, 1.04);
            return false;
        }
        
        // Validate total marks
        int totalMarks = parseIntField(totalMarksField, 100, 1, 1000, "Total Marks");
        if (totalMarks < 0) {
            setStatus("❌ Invalid total marks. Please enter a number between 1 and 1000.", true);
            AnimationUtil.pulse(totalMarksField, 1.04);
            return false;
        }
        
        if (questionRows.isEmpty()) {
            setStatus("❌ Add at least one question.", true);
            return false;
        }
        for (int i = 0; i < questionRows.size(); i++) {
            String err = questionRows.get(i).validate();
            if (err != null) {
                setStatus("❌ Question " + (i + 1) + ": " + err, true);
                return false;
            }
        }
        if (realTimeRadio.isSelected()) {
            if (startDatePicker.getValue() == null || endDatePicker.getValue() == null) {
                setStatus("❌ Select start and end dates for Real-Time exam.", true);
                return false;
            }
        }
        return true;
    }

    private Exam buildExam() {
        String username = UserSession.getInstance().getUsername();
        Exam exam = new Exam();
        exam.setTitle(examTitleField.getText().trim());
        exam.setDescription(examDescField.getText().trim());
        exam.setDurationMinutes(parseIntField(durationField, 60, 1, 300, "Duration"));
        exam.setTotalMarks(parseIntField(totalMarksField, 100, 1, 1000, "Total Marks"));
        exam.setNegativeMarking(negativeMarkingCheck.isSelected());
        exam.setShuffleOptions(shuffleOptionsCheck.isSelected());
        exam.setPublisherUsername(username);
        exam.setExamType(realTimeRadio.isSelected() ? Exam.ExamType.REAL_TIME : Exam.ExamType.PRACTICE);

        if (realTimeRadio.isSelected()) {
            exam.setStartTime(LocalDateTime.of(
                    startDatePicker.getValue(),
                    java.time.LocalTime.of(startHourSpinner.getValue(), startMinSpinner.getValue())));
            exam.setEndTime(LocalDateTime.of(
                    endDatePicker.getValue(),
                    java.time.LocalTime.of(endHourSpinner.getValue(), endMinSpinner.getValue())));
        }

        List<Question> questions = new ArrayList<>();
        for (int i = 0; i < questionRows.size(); i++) {
            Question q = questionRows.get(i).buildQuestion("Q-" + (i + 1));
            questions.add(q);
        }
        exam.setQuestions(questions);
        return exam;
    }

    private void setStatus(String msg, boolean isError) {
        if (statusLabel == null) return;
        statusLabel.setText(msg);
        statusLabel.setStyle(isError
                ? "-fx-text-fill: #fca5a5;"
                : "-fx-text-fill: #86efac;");
    }

    /**
     * Parses an integer from a TextField with validation and default value.
     * @param field The TextField to parse
     * @param defaultValue The default value if parsing fails or field is empty
     * @param min Minimum allowed value (inclusive)
     * @param max Maximum allowed value (inclusive)
     * @param fieldName Name for error messages (unused, kept for consistency)
     * @return The parsed value clamped to [min, max], or -1 if invalid input
     */
    private int parseIntField(TextField field, int defaultValue, int min, int max, String fieldName) {
        String text = field.getText().trim();
        if (text.isEmpty()) {
            return defaultValue;
        }
        try {
            int value = Integer.parseInt(text);
            // Clamp to valid range
            return Math.max(min, Math.min(max, value));
        } catch (NumberFormatException e) {
            return -1; // Invalid input indicator
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
    // INNER CLASS – per-question form row (built programmatically)
    // ──────────────────────────────────────────────────────────────────

    static class QuestionFormRow {
        private final VBox root;
        private Label numberLabel;
        private final TextField questionField;
        private final Button addImageBtn;
        private final ImageView imageView;
        private String imageBase64;
        private final TextField[] optionFields;
        private final ToggleGroup correctGroup;
        private final RadioButton[] correctRadios;
        private final Spinner<Integer> marksSpinner;

        interface RemoveCallback { void remove(QuestionFormRow row); }

        QuestionFormRow(int number, RemoveCallback onRemove) {
            questionField = new TextField();
            questionField.setPromptText("Question text...");
            questionField.getStyleClass().add("exam-form-input");

            addImageBtn = new Button("🖼️ Add Image");
            addImageBtn.getStyleClass().add("exam-action-btn");
            imageView = new ImageView();
            imageView.setFitWidth(150);
            imageView.setFitHeight(150);
            imageView.setPreserveRatio(true);
            imageView.setVisible(false);
            imageView.setManaged(false);

            addImageBtn.setOnAction(e -> {
                FileChooser fc = new FileChooser();
                fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.gif"));
                java.io.File file = fc.showOpenDialog(questionField.getScene().getWindow());
                if (file != null) {
                    try {
                        byte[] bytes = java.nio.file.Files.readAllBytes(file.toPath());
                        imageBase64 = java.util.Base64.getEncoder().encodeToString(bytes);
                        imageView.setImage(new Image(new java.io.ByteArrayInputStream(bytes)));
                        imageView.setVisible(true);
                        imageView.setManaged(true);
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                }
            });

            optionFields = new TextField[4];
            correctGroup = new ToggleGroup();
            correctRadios = new RadioButton[4];

            String[] letters = {"A", "B", "C", "D"};
            VBox optionsBox = new VBox(6);
            for (int i = 0; i < 4; i++) {
                optionFields[i] = new TextField();
                optionFields[i].setPromptText("Option " + letters[i]);
                optionFields[i].getStyleClass().add("exam-form-input");
                HBox.setHgrow(optionFields[i], Priority.ALWAYS);

                correctRadios[i] = new RadioButton();
                correctRadios[i].setToggleGroup(correctGroup);
                correctRadios[i].setTooltip(new Tooltip("Mark as correct answer"));

                Label lbl = new Label(letters[i] + ".");
                lbl.setStyle("-fx-text-fill: #94a3b8; -fx-min-width: 20px; -fx-font-weight: bold;");

                HBox optRow = new HBox(8, correctRadios[i], lbl, optionFields[i]);
                optRow.setAlignment(Pos.CENTER_LEFT);
                optionsBox.getChildren().add(optRow);
            }
            correctRadios[0].setSelected(true);

            marksSpinner = new Spinner<>(1, 50, 1);
            marksSpinner.setPrefWidth(80);

            Label marksLbl = new Label("Marks:");
            marksLbl.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 13px;");

            Button removeBtn = new Button("✕ Remove");
            removeBtn.getStyleClass().add("exam-remove-btn");
            removeBtn.setOnAction(e -> onRemove.remove(this));

            HBox footer = new HBox(12, marksLbl, marksSpinner, new Pane() {{ HBox.setHgrow(this, Priority.ALWAYS); }}, removeBtn);
            footer.setAlignment(Pos.CENTER_LEFT);

            numberLabel = new Label("Q" + number);
            numberLabel.setStyle("-fx-text-fill: #818cf8; -fx-font-size: 15px; -fx-font-weight: bold;");

            root = new VBox(10, numberLabel, questionField, addImageBtn, imageView, optionsBox, footer);
            root.getStyleClass().add("exam-question-card");
            root.setPadding(new Insets(16));
        }

        void setNumber(int n) { numberLabel.setText("Q" + n); }
        Node getRoot() { return root; }

        String validate() {
            if (questionField.getText().trim().isEmpty()) return "Question text is empty.";
            for (TextField tf : optionFields)
                if (tf.getText().trim().isEmpty()) return "All 4 options must be filled.";
            return null;
        }

        Question buildQuestion(String id) {
            List<String> opts = new ArrayList<>();
            for (TextField tf : optionFields) opts.add(tf.getText().trim());
            int correct = 0;
            for (int i = 0; i < 4; i++) if (correctRadios[i].isSelected()) { correct = i; break; }
            Question q = new Question(id, questionField.getText().trim(), opts, correct, marksSpinner.getValue());
            q.setImageBase64(imageBase64);
            return q;
        }
    }
}
