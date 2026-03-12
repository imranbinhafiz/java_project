package com.example.javaproject;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

public class SyllabusController {

    // ================================
    // FXML COMPONENTS
    // ================================

    @FXML private Label  courseNameLabel;
    @FXML private Label  topicCountBadge;
    @FXML private Label  selectionLabel;
    @FXML private Button backButton;
    @FXML private Button addTopicButton;
    @FXML private Button deleteTopicButton;
    @FXML private Button clearAllButton;
    @FXML private TextField topicNameField;
    @FXML private ListView<Topic> topicsListView;

    // ================================
    // INSTANCE VARIABLES
    // ================================

    private Course currentCourse;
    private ObservableList<Topic> topicsList = FXCollections.observableArrayList();
    private Runnable onBackCallback;

    // ================================
    // INITIALIZATION
    // ================================

    @FXML
    public void initialize() {
        topicsListView.setItems(topicsList);
        setupTopicsListDisplay();
        deleteTopicButton.setDisable(true);
        updateLabels();
    }

    public void setCourse(Course course) {
        this.currentCourse = course;
        courseNameLabel.setText(course.getName());
        topicsList = course.getTopics();
        topicsListView.setItems(topicsList);
        updateLabels();
    }

    public void setOnBackCallback(Runnable callback) {
        this.onBackCallback = callback;
    }

    // ================================
    // CELL FACTORY
    // ================================

    private void setupTopicsListDisplay() {
        topicsListView.setCellFactory(listView -> new ListCell<Topic>() {
            private CheckBox checkBox;
            private Topic currentTopic;

            @Override
            protected void updateItem(Topic topic, boolean empty) {
                super.updateItem(topic, empty);

                if (empty || topic == null) {
                    setGraphic(null); setText(null);
                    if (checkBox != null && currentTopic != null)
                        checkBox.selectedProperty().unbindBidirectional(currentTopic.completedProperty());
                    currentTopic = null;
                    return;
                }

                // Re-use checkbox
                if (checkBox == null) {
                    checkBox = new CheckBox();
                    checkBox.getStyleClass().add("topic-checkbox");
                }
                if (currentTopic != null)
                    checkBox.selectedProperty().unbindBidirectional(currentTopic.completedProperty());

                currentTopic = topic;
                checkBox.setSelected(topic.isCompleted());
                checkBox.selectedProperty().bindBidirectional(topic.completedProperty());
                checkBox.setOnAction(e -> {
                    updateLabels();
                    persistCoursesToFile();
                    // refresh styles
                    topicsListView.refresh();
                });
                checkBox.setText(topic.getName());
                checkBox.getStyleClass().removeAll("topic-completed", "topic-pending");
                checkBox.getStyleClass().add(topic.isCompleted() ? "topic-completed" : "topic-pending");

                // ── Comment button ─────────────────────────────
                Button commentBtn = new Button();
                boolean hasComment = topic.getComment() != null && !topic.getComment().isBlank();
                commentBtn.setText(hasComment ? "💬" : "🗨");
                commentBtn.getStyleClass().add(hasComment ? "comment-btn-active" : "comment-btn");
                commentBtn.setTooltip(new Tooltip(hasComment ? topic.getComment() : "Add comment"));
                commentBtn.setOnAction(e -> openCommentDialog(topic, commentBtn));

                // ── Row layout ─────────────────────────────────
                HBox row = new HBox(8);
                row.setAlignment(Pos.CENTER_LEFT);
                row.setMaxWidth(Double.MAX_VALUE);
                row.getStyleClass().add("topic-row-card");
                if (topic.isCompleted()) {
                    row.getStyleClass().add("topic-row-completed");
                }
                HBox.setHgrow(checkBox, Priority.ALWAYS);
                checkBox.setMaxWidth(Double.MAX_VALUE);
                row.getChildren().addAll(checkBox, commentBtn);
                setGraphic(row);
                setText(null);
            }
        });

        topicsListView.getSelectionModel().selectedItemProperty().addListener(
            (obs, oldVal, newVal) -> {
                deleteTopicButton.setDisable(newVal == null);
                selectionLabel.setText(newVal != null
                    ? "Selected: " + newVal.getName()
                    : "Check topics to mark as completed");
            }
        );
    }

    // ================================
    // COMMENT DIALOG
    // ================================

    private void openCommentDialog(Topic topic, Button commentBtn) {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Topic Comment");
        dialog.setHeaderText("📝  " + topic.getName());
        dialog.getDialogPane().getStylesheets().add(
            getClass().getResource("css/syllabus.css").toExternalForm());
        dialog.getDialogPane().getStyleClass().add("comment-dialog-pane");

        ButtonType saveBtn = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        ButtonType clearBtn = new ButtonType("Clear", ButtonBar.ButtonData.OTHER);
        dialog.getDialogPane().getButtonTypes().addAll(saveBtn, clearBtn, ButtonType.CANCEL);

        TextArea area = new TextArea(topic.getComment());
        area.setPromptText("Write a note or comment for this topic…");
        area.setWrapText(true);
        area.setPrefRowCount(5);
        area.getStyleClass().add("comment-textarea");

        dialog.getDialogPane().setContent(area);
        area.requestFocus();

        dialog.setResultConverter(btn -> {
            if (btn == saveBtn)  return area.getText().trim();
            if (btn == clearBtn) return "";
            return null;
        });

        dialog.showAndWait().ifPresent(result -> {
            topic.setComment(result);
            boolean hasComment = !result.isBlank();
            commentBtn.setText(hasComment ? "💬" : "🗨");
            commentBtn.getStyleClass().removeAll("comment-btn", "comment-btn-active");
            commentBtn.getStyleClass().add(hasComment ? "comment-btn-active" : "comment-btn");
            commentBtn.setTooltip(new Tooltip(hasComment ? result : "Add comment"));
            persistCoursesToFile();
        });
    }

    // ================================
    // ACTION HANDLERS
    // ================================

    @FXML private void handleBackClick() {
        if (onBackCallback != null) onBackCallback.run();
    }

    @FXML private void handleAddTopic() {
        String name = topicNameField.getText();
        if (name == null || name.isBlank()) {
            AnimationUtil.errorPulse(topicNameField,
                "-fx-border-color: rgba(148,163,184,0.3); -fx-border-width:1.5px; -fx-border-radius:10px;");
            return;
        }
        Topic t = new Topic(name.trim());
        topicsList.add(t);
        topicNameField.clear();
        updateLabels();
        topicsListView.scrollTo(t);
        AnimationUtil.pulse(addTopicButton, 1.08);
        persistCoursesToFile();
    }

    @FXML private void handleDeleteTopic() {
        Topic selected = topicsListView.getSelectionModel().getSelectedItem();
        if (selected != null) {
            topicsList.remove(selected);
            topicsListView.getSelectionModel().clearSelection();
            deleteTopicButton.setDisable(true);
            updateLabels();
            persistCoursesToFile();
        }
    }

    @FXML private void handleClearAll() {
        for (Topic t : topicsList) t.setCompleted(false);
        topicsListView.refresh();
        updateLabels();
        AnimationUtil.pulse(clearAllButton, 1.08);
        persistCoursesToFile();
    }

    // ================================
    // HELPERS
    // ================================

    private void updateLabels() {
        int total     = topicsList.size();
        int completed = (int) topicsList.stream().filter(Topic::isCompleted).count();
        topicCountBadge.setText(completed + "/" + total + " done");
    }

    private void persistCoursesToFile() {
        String username = UserSession.getInstance().getUsername();
        if (username == null || username.isBlank()) return;
        UserFileManager.persistAllCourses(username, CourseData.getCourses());
    }
}
