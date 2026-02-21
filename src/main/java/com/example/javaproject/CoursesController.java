package com.example.javaproject;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;

import java.io.IOException;

public class CoursesController {

    // ================================
    // FXML COMPONENTS
    // ================================

    @FXML
    private VBox contentArea;

    @FXML
    private Label courseCountBadge;

    @FXML
    private TextField courseNameField;

    @FXML
    private TextField courseCodeField;

    @FXML
    private TextField courseCreditsField;

    @FXML
    private Button addCourseButton;

    @FXML
    private ListView<Course> coursesListView;

    @FXML
    private Label selectionLabel;

    @FXML
    private Button deleteSelectedButton;

    // ================================
    // INSTANCE VARIABLES
    // ================================


    private javafx.util.Callback<Node, Void> contentLoader;
    private ObservableList<Course> coursesList = CourseData.getCourses();
    private Runnable onSyllabusOpenCallback;

    // ================================
    // INITIALIZATION
    // ================================

    @FXML
    public void initialize() {
        // TODO: Set up ListView items and cell factory
        // TODO: Set up selection listeners for the list
        // TODO: Initial state (disable delete button, update badge)
        coursesListView.setItems(coursesList);

        coursesListView.setCellFactory(courseListView -> new ListCell<Course>(){
            @Override
            protected void updateItem(Course course, boolean empty) {
                super.updateItem(course, empty);

                if (empty || course == null){
                    setGraphic(null);
                }
                else{
                    HBox cell = new HBox(80);
                    cell.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

                    Label name = new Label("Name: " + course.getName());
                    name.getStyleClass().add("course-name");

                    Label code = new Label("Code: " + course.getCode());
                    code.getStyleClass().add("course-code");

                    Label credits = new Label(course.getCredits() + " credits");
                    credits.getStyleClass().add("course-credits");

                    Pane spacer = new Pane();
                    HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);

                    cell.getChildren().addAll(name, code, credits);
                    setGraphic(cell);
                }
            }
        });

        deleteSelectedButton.setDisable(true);

        coursesListView.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldVal, newVal) ->{
                    if (newVal != null){
                        deleteSelectedButton.setDisable(false);
                        selectionLabel.setText("Selected: " + newVal.getName() + " (Double tap to open Topics)");
                    }
                    else{
                        selectionLabel.setText("Select a Course to delete");
                    }
                }
        );

        updateCourseCountBadge();
    }

    // ================================
    // COURSE MANAGEMENT HANDLERS
    // ================================

    @FXML
    private void handleAddCourse() {
        // TODO: Validate inputs
        // TODO: Create and add new Course to observable list
        // TODO: Clear fields and update badge

        if (validateInputs()){
            double credits = Double.parseDouble(courseCreditsField.getText());
            String name = courseNameField.getText();
            String code = courseCodeField.getText();

            Course newCourse = new Course(name, code, credits);
            coursesList.add(newCourse);

            updateCourseCountBadge();
            clearInputFields();
        }
    }

    @FXML
    private void handleCourseClick(MouseEvent event) throws IOException {
        // TODO: Detect selection and enable/disable delete button
        // TODO: Update selectionLabel text
        if (event.getClickCount() == 2){
            Course selected = coursesListView.getSelectionModel().getSelectedItem();
            if (selected != null)
                openSyllabus(selected);
        }
    }

    @FXML
    private void handleDeleteSelected() {
        // TODO: Remove selected item from list
        // TODO: Clear selection and update badge
        Course selectedCourse = coursesListView.getSelectionModel().getSelectedItem();

        coursesList.remove(selectedCourse);
        coursesListView.getSelectionModel().clearSelection();
        deleteSelectedButton.setDisable(true);
        updateCourseCountBadge();
    }

    // ================================
    // HELPER METHODS (PROTOTYPES)
    // ================================
    /**
     * Opens the syllabus view for a specific course
     */
    private void openSyllabus(Course course) throws IOException{
        // TODO: Load syllabus.fxml
        // TODO: Get SyllabusController and set the course
        // TODO: Set back callback
        // TODO: Notify parent to switch content
        try{
            FXMLLoader loader = new FXMLLoader(getClass().getResource("fxml files/syllabus.fxml"));
            VBox syllabusContent = loader.load();

            SyllabusController controller = loader.getController();
            controller.setCourse(course);

            // Set back callback to return to courses view
            controller.setOnBackCallback(() -> {
                if (onSyllabusOpenCallback != null) {
                    onSyllabusOpenCallback.run();
                }
            });

            if (contentLoader != null) {
                contentLoader.call(syllabusContent);
            }


            System.out.println("Opening syllabus for: " + course.getName());
        }
        catch (IOException e){
            e.printStackTrace();
        }
    }

    /**
     * Sets callback for when syllabus is opened
     */
    public void setOnSyllabusOpenCallback(Runnable callback) {
        // TODO: Store the callback
        this.onSyllabusOpenCallback = callback;
    }

    private void updateCourseCountBadge() {
        // TODO: Update badge text
        courseCountBadge.setText(coursesList.size() + " courses");
    }

    private void clearInputFields() {
        // TODO: Clear text fields
        courseNameField.clear();
        courseCodeField.clear();
        courseCreditsField.clear();

        AnimationUtil.pulse(courseNameField, 1.05);
    }

    private boolean validateInputs() {
        // TODO: Logic for ensuring fields aren't empty
        String credits = courseCreditsField.getText();
        String name = courseNameField.getText();
        String code = courseCodeField.getText();

        String normalStyle = "-fx-border-color: rgba(148, 163, 184, 0.3); -fx-border-width: 1.5px; -fx-border-radius: 10px;";
        int errorCount = 0;
        if (name.isEmpty()) {
            errorCount++;
            AnimationUtil.errorPulse(courseNameField, normalStyle);
        }
        if (code.isEmpty()){
            errorCount++;
            AnimationUtil.errorPulse(courseCodeField, normalStyle);

        }
        if (credits.isEmpty()){
            errorCount++;
            AnimationUtil.errorPulse(courseCreditsField, normalStyle);
        }else {
            try {
                if (Double.parseDouble(credits) <= 0) {
                    errorCount++;
                    AnimationUtil.errorPulse(courseCreditsField, normalStyle);
                }
            } catch (NumberFormatException e) {
                errorCount++;
                AnimationUtil.errorPulse(courseCreditsField, normalStyle);
            }
        }

        return errorCount == 0;
    }

    public void setContentLoader(javafx.util.Callback<Node, Void> loader) {
        this.contentLoader = loader;
    }
}