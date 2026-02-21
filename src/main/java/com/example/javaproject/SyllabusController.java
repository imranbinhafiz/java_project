package com.example.javaproject;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;

public class SyllabusController {

    // ================================
    // FXML COMPONENTS
    // ================================

    @FXML
    private Label courseNameLabel;

    @FXML
    private Label courseCodeLabel;

    @FXML
    private Button backButton;

    @FXML
    private ProgressBar progressBar;

    @FXML
    private Label progressLabel;

    @FXML
    private Label completedCountLabel;

    @FXML
    private Label totalCountLabel;

    @FXML
    private TextField topicNameField;

    @FXML
    private Button addTopicButton;

    @FXML
    private ListView<Topic> topicsListView;

    @FXML
    private Label topicCountBadge;

    @FXML
    private Label selectionLabel;

    @FXML
    private Button deleteTopicButton;

    @FXML
    private Button clearAllButton;

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
        // TODO: Bind topics to ListView
        // TODO: Set up custom cell factory with checkboxes
        // TODO: Disable delete button initially
        // TODO: Update UI labels
        topicsListView.setItems(topicsList);

        // Set up custom cell factory with checkboxes
        setupTopicsListDisplay();

        // Disable delete button initially
        deleteTopicButton.setDisable(true);

        // Update UI
        updateAllLabels();topicsListView.setItems(topicsList);

        // Set up custom cell factory with checkboxes
        setupTopicsListDisplay();

        // Disable delete button initially
        deleteTopicButton.setDisable(true);

        // Update UI
        updateAllLabels();
    }

    /**
     * Sets the course to display syllabus for
     */
    public void setCourse(Course course) {
        // TODO: Store the course
        // TODO: Update course name and code labels
        // TODO: Load topics for this course
        // TODO: Update all labels
        this.currentCourse = course;
        courseNameLabel.setText(course.getName());
        courseCodeLabel.setText("(" + course.getCode() + ")");

        topicsList.clear();
        updateAllLabels();
    }

    /**
     * Sets the callback function to execute when back button is clicked
     */
    public void setOnBackCallback(Runnable callback) {
        // TODO: Store the callback
        this.onBackCallback = callback;
    }

    // ================================
    // LIST DISPLAY SETUP
    // ================================

    private void setupTopicsListDisplay() {
        topicsListView.setCellFactory(listView -> new ListCell<Topic>() {
            private CheckBox checkBox;
            private Topic currentTopic;

            @Override
            protected void updateItem(Topic topic, boolean empty) {
                super.updateItem(topic, empty);

                if (empty || topic == null) {
                    setGraphic(null);
                    setText(null);
                    // Unbind from previous topic if exists
                    if (checkBox != null && currentTopic != null) {
                        checkBox.selectedProperty().unbindBidirectional(currentTopic.completedProperty());
                    }
                    currentTopic = null;
                } else {
                    // Create checkbox if it doesn't exist
                    if (checkBox == null) {
                        checkBox = new CheckBox();
                        checkBox.getStyleClass().add("topic-checkbox");
                    }

                    // Unbind from previous topic first
                    if (currentTopic != null) {
                        checkBox.selectedProperty().unbindBidirectional(currentTopic.completedProperty());
                    }

                    // Store current topic reference
                    currentTopic = topic;

                    // Set checkbox state and bind to new topic
                    checkBox.setSelected(topic.isCompleted());
                    checkBox.selectedProperty().bindBidirectional(topic.completedProperty());

                    // Update progress when checkbox changes
                    checkBox.setOnAction(e -> {
                        updateProgress();
                        AnimationUtil.pulse(progressBar, 1.03);
                    });

                    checkBox.setText(topic.getName());

                    // Apply completed/pending style
                    checkBox.getStyleClass().removeAll("topic-completed", "topic-pending");
                    if (topic.isCompleted()) {
                        checkBox.getStyleClass().add("topic-completed");
                    } else {
                        checkBox.getStyleClass().add("topic-pending");
                    }

                    HBox cell = new HBox(checkBox);
                    cell.setAlignment(Pos.CENTER_LEFT);
                    setGraphic(cell);
                }
            }
        });

        // Selection listener for delete button
        topicsListView.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, newValue) -> {
                    deleteTopicButton.setDisable(newValue == null);
                    if (newValue != null) {
                        selectionLabel.setText("Selected: " + newValue.getName());
                    } else {
                        selectionLabel.setText("Check topics to mark as completed");
                    }
                }
        );
    }

    // ================================
    // ACTION HANDLERS
    // ================================

    @FXML
    private void handleBackClick() {
        // TODO: Execute back callback
        if (onBackCallback != null){
            onBackCallback.run();
        }
    }

    @FXML
    private void handleAddTopic() {
        // TODO: Validate input
        // TODO: Create new Topic
        // TODO: Add to list
        // TODO: Clear input field
        // TODO: Update labels
        // TODO: Animation feedback
        String topicName = topicNameField.getText();

        if (validateInputs()){
            Topic newTopic = new Topic(topicName.trim());
            topicsList.add(newTopic);
//            topicsListView.getItems().add(newTopic);

            // Clear input and update UI
            topicNameField.clear();
            updateAllLabels();

            // Scroll to new item
            topicsListView.scrollTo(newTopic);

            // Animation feedback
            AnimationUtil.pulse(addTopicButton, 1.08);
        }
    }

    @FXML
    private void handleDeleteTopic() {
        // TODO: Get selected topic
        // TODO: Remove from list
        // TODO: Clear selection
        // TODO: Update labels

        Topic selected = topicsListView.getSelectionModel().getSelectedItem();

        if (selected != null){
            topicsList.remove(selected);
            topicsListView.getSelectionModel().clearSelection();
            deleteTopicButton.setDisable(true);
            updateAllLabels();
        }
    }

    @FXML
    private void handleClearAll() {
        // TODO: Uncheck all topics
        // TODO: Refresh list
        // TODO: Update progress
        for (Topic topic: topicsList){
            topic.setCompleted(false);
        }
        topicsListView.refresh();
        updateAllLabels();
        AnimationUtil.pulse(clearAllButton, 1.08);
    }

    // ================================
    // HELPER METHODS
    // ================================

    private void updateAllLabels() {
        // TODO: Update progress and topic count
        updateProgress();
        updateTopicCount();
    }

    private void updateProgress() {
        // TODO: Calculate completed count
        // TODO: Calculate progress percentage
        // TODO: Update progress bar
        // TODO: Update progress label
        // TODO: Update completed/total labels

        int total = topicsList.size();
        int completed = (int) topicsList.stream().filter(Topic::isCompleted).count();

        // Update progress bar
        double progress = total > 0 ? (double) completed / total : 0.0;
        progressBar.setProgress(progress);

        // Update labels
        int percentage = (int) (progress * 100);
        progressLabel.setText(percentage + "%");
        completedCountLabel.setText(completed + " completed");
        totalCountLabel.setText(total + " total topics");

    }

    private void updateTopicCount() {
        // TODO: Update topic count badge

        topicCountBadge.setText(topicsList.size() + " Topics");
        totalCountLabel.setText(topicsList.size() + " total topics");

    }

    private boolean validateInputs(){
        String topicName = topicNameField.getText();

        String normalStyle = "-fx-border-color: rgba(148, 163, 184, 0.3); -fx-border-width: 1.5px; -fx-border-radius: 10px;";
        if (topicName.isEmpty()){
            AnimationUtil.errorPulse(topicNameField, normalStyle);
            return false;
        }

        return true;
    }
}