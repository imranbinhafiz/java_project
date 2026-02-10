package com.example.javaproject;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.Node;
import javafx.event.ActionEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.MouseButton;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;


public class DashboardController {

    // ============================================
    // FXML INJECTED FIELDS
    // ============================================

    @FXML
    private BorderPane rootPane;

    @FXML
    private VBox sideNav;

    @FXML
    private Button toggleButton;

    @FXML
    private Button dashboardBtn;

    // Navigation Buttons
    @FXML
    private Button coursesBtn;

    @FXML
    private Button duelBtn;

    @FXML
    private Button examsBtn;

    @FXML
    private Button performanceBtn;

    @FXML
    private Button settingsBtn;

    @FXML
    private Button logoutBtn;

    // Content Area
    @FXML
    private VBox contentArea;

    @FXML
    private StackPane imageDisplayPane;

    // ============================================
    // TODO LIST COMPONENTS
    // ============================================

    @FXML
    private TextField todoInputField;

    @FXML
    private Button addTodoButton;

    @FXML
    private ListView<TodoItem> todoListView;

    @FXML
    private Button clearCompletedButton;

    @FXML
    private Button deleteSelectedButton;

    @FXML
    private Label taskCountBadge;


    // ============================================
    // DEADLINE COMPONENTS
    // ============================================

    @FXML
    private DatePicker deadlineDatePicker;

    @FXML
    private TextField deadlineTitleField;

    @FXML
    private Button addDeadlineButton;

    @FXML
    private ListView<Deadline> deadlineListView;

    private Node originalDashboardChildren;

    @FXML
    private Button deleteDeadlineButton;

    // ============================================
    // STATE MANAGEMENT
    // ============================================

    private boolean isSidebarCollapsed = false;
    private Button activeNavButton = null;

    // Todo Service - manages all todo operations
    private TodoService todoService;

    //manage all deadline operations
    private DeadlineService deadlineService;//reference object will be declared later
    // ============================================
    // INITIALIZATION
    // ============================================

    /**
     * Initializes the dashboard after FXML loading.
     * Sets up initial state, binds data, and applies entry animations.
     */
    @FXML
    public void initialize() {
        // Initialize todo service
        todoService = new TodoService();
        deadlineService = new DeadlineService();

        // Bind ListView to todo service
        todoListView.setItems(todoService.getTodos());
        deadlineListView.setItems(deadlineService.getDeadlines());

        // Set up cell factory for custom todo item display
        setupTodoListDisplay();
        setupdeadlineListDisplay();

        // Set default active navigation (Dashboard)
       setActiveNavButton(dashboardBtn);

        // Apply fade-in animation to content area
        AnimationUtil.fadeIn(contentArea);

        // Update task count badge
        updateTaskCountBadge();

        originalDashboardChildren=rootPane.getCenter();


    }

    // ============================================
    // TODO LIST FUNCTIONALITY
    // ============================================

    /**
     * Sets up custom cell factory for todo items.
     * Creates styled cells with proper formatting.
     */
    private void setupTodoListDisplay() {
        todoListView.setCellFactory(listView -> new TodoListCell());//for custom visual representation

        // Add selection listener to enable/disable delete button
        todoListView.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, newValue) -> {
                    deleteSelectedButton.setDisable(newValue == null);//select korle enable hobe nahole disable
                }
        );

        // Initially disable delete button (no selection)
        deleteSelectedButton.setDisable(true);
    }

    private void setupdeadlineListDisplay(){
        deadlineListView.setCellFactory(listView -> new DeadlineListCell());
        deadlineListView.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, newValue) -> {
                    deleteDeadlineButton.setDisable(newValue == null);
        });
        // Initially disable delete button (no selection)
        deleteDeadlineButton.setDisable(true);
    }

    /**
     * Handles adding a new todo item.
     * Triggered by pressing Enter in text field or clicking Add button.
     */
    @FXML
    private void handleAddTodo(ActionEvent event) {
        String taskText = todoInputField.getText();

        if (taskText == null || taskText.trim().isEmpty()) {
            // Shake animation for empty input
            AnimationUtil.pulse(todoInputField, 1.05);
            return;
        }

        // Add task to service
        boolean success = todoService.addTodo(taskText.trim());

        if (success) {
            // Clear input field
            todoInputField.clear();

            // Update badge
            updateTaskCountBadge();

            // Apply subtle animation to add button for feedback
            AnimationUtil.pulse(addTodoButton, 1.1);

            // Scroll to newly added item
            todoListView.scrollTo(todoService.getTotalCount() - 1);
            todoListView.refresh();//for fade in animation
        }
    }

    @FXML
    private void handleAddDeadline(ActionEvent event){
        String t= deadlineTitleField.getText();
        LocalDate date=deadlineDatePicker.getValue();

        if(t==null || t.trim().isEmpty()){
            //shake animation for empty input
            AnimationUtil.pulse(deadlineTitleField,1.05);
            return;
        }
        if(date==null){
            AnimationUtil.pulse(deadlineDatePicker,1.05);
            return;
        }

        boolean s=deadlineService.addDeadline(t.trim(),date);
        if(s){
            //clearing the input field
            deadlineTitleField.clear();
            deadlineDatePicker.setValue(null);
            deadlineListView.scrollTo(deadlineService.getTotalCount()-1);
            deadlineListView.refresh();//fade in animation for new item
        }
    }

    /**
     * Handles clicking on a todo item in the list.
     * Double-click toggles completion status.
     */
    @FXML
    private void handleTodoClick(MouseEvent event) {
        if(event.getButton()==MouseButton.PRIMARY && event.getClickCount()==2){
            //primary=left button
            //secondary=right button
            //middle=wheel

            TodoItem selected_item=todoListView.getSelectionModel().getSelectedItem();

            if(selected_item!=null){
                todoService.toggleCompleted(selected_item);

                updateTaskCountBadge();

                todoListView.refresh();

                AnimationUtil.pulse(todoListView,1.02);
            }
        }
    }

    /**
     * Handles deleting the selected todo item.
     */
    @FXML
    private void handleDeleteSelected(ActionEvent event) {
        TodoItem selectedItem = todoListView.getSelectionModel().getSelectedItem();

        if (selectedItem != null) {
            // Remove from service
            todoService.removeTodo(selectedItem);

            // Update badge
            updateTaskCountBadge();

            // Clear selection
            todoListView.getSelectionModel().clearSelection();

            // Apply animation for feedback
            AnimationUtil.pulse(deleteSelectedButton, 1.08);
        }
    }

    @FXML

    private void handleDeleteDeadline(ActionEvent e){
        Deadline selected_item=deadlineListView.getSelectionModel().getSelectedItem();
        if(selected_item!=null){
            boolean rmv=deadlineService.removeDeadline(selected_item);
            if(rmv){
                AnimationUtil.pulse(deleteDeadlineButton,1.08);
                deadlineListView.getSelectionModel().clearSelection();
            }
            else {
                //no selection pulse for feedback
                AnimationUtil.pulse(deleteDeadlineButton,1.08);
            }
        }

    }

    /**
     * Handles clearing all completed todos.
     */
    @FXML
    private void handleClearCompleted(ActionEvent event) {
       int removed_count = todoService.clearCompleted();

       if(removed_count>0){
           updateTaskCountBadge();
           AnimationUtil.pulse(clearCompletedButton,1.08);
       }
    }

    /**
     * Updates the task count badge in the header.
     */
    private void updateTaskCountBadge() {

        //ui te updated task ta dekhanor jnno
        if (taskCountBadge != null) {
            taskCountBadge.setText(todoService.getTaskCountBadge());
        }
    }


    // ============================================
    // SIDEBAR TOGGLE FUNCTIONALITY
    // ============================================

    /**
     * Handles the hamburger toggle button click.
     * Animates sidebar collapse/expand with smooth transition.
     *
     * @param event The action event from the toggle button
     */
    @FXML
    private void handleToggleSidebar(ActionEvent event) {
       isSidebarCollapsed=!isSidebarCollapsed;

       //ekta transition
        AnimationUtil.animateSidebarCollapse(sideNav,isSidebarCollapsed,() -> {
            updateSidebarVisibility();
        });

        AnimationUtil.pulse(toggleButton,1.08);
    }

    /**
     * Updates sidebar text visibility based on collapsed state.
     * Hides text labels when collapsed, shows when expanded.
     */
    //last e korbo
    private void updateSidebarVisibility() {
        if (isSidebarCollapsed) {
            // Optional: Update button text to icons only
            // This can be expanded based on design requirements
        } else {
            // No specific text elements to show
        }
    }

    // ============================================
    // NAVIGATION BUTTON HANDLERS
    // ============================================

    /**
     * Handles Dashboard button click.
     * Loads the dashboard view content.
     */
    @FXML
    private void handleDashboardClick(ActionEvent event) {
        setActiveNavButton(dashboardBtn);
        loadContent(originalDashboardChildren);

    }

    /**
     * Handles Courses button click.
     * Future: Load courses view content.
     */
    @FXML
    private void handleCoursesClick(ActionEvent event) {
        setActiveNavButton(coursesBtn);



    }

    /**
     * Handles Duel button click.
     * Future: Load duel mode content.
     */
    @FXML
    private void handleDuelClick(ActionEvent event) {
        setActiveNavButton(duelBtn);

    }

    /**
     * Handles Exams button click.
     * Future: Load exams view content.
     */
    @FXML
    private void handleExamsClick(ActionEvent event) {
        setActiveNavButton(examsBtn);

    }

    /**
     * Handles Performance button click.
     * Future: Load performance analytics content.
     */
    @FXML
    private void handlePerformanceClick(ActionEvent event) {
        setActiveNavButton(performanceBtn);
    }

    /**
     * Handles Settings button click.
     * Future: Load settings panel.
     */
    @FXML
    private void handleSettingsClick(ActionEvent event) {
        setActiveNavButton(settingsBtn);

    }

    /**
     * Handles Logout button click.
     * Returns to login screen.
     */
    @FXML
    private void handleLogoutClick(ActionEvent event) {
        try {
            // Navigate back to login screen
            Main.changeScene(event, "fxml files/login.fxml");
        } catch (IOException e) {
            System.err.println("Error navigating to login: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ============================================
    // NAVIGATION BUTTON HOVER EFFECTS
    // ============================================

    /**
     * Handles mouse enter event for navigation buttons.
     * Applies smooth scale-up hover animation.
     *
     * @param event The mouse event
     */
    @FXML
    private void handleNavHover(MouseEvent event) {
        Node source = (Node) event.getSource();

        // Only animate if not the active button
        if (source != activeNavButton) {
            AnimationUtil.scaleHover(source, 1.03, AnimationUtil.HOVER_SCALE_DURATION);
        }
    }

    /**
     * Handles mouse exit event for navigation buttons.
     * Resets button to original scale.
     *
     * @param event The mouse event
     */
    @FXML
    private void handleNavExit(MouseEvent event) {
        Node source = (Node) event.getSource();

        // Only animate if not the active button
        if (source != activeNavButton) {
            AnimationUtil.scaleReset(source, AnimationUtil.HOVER_SCALE_DURATION);
        }
    }

    // ============================================
    // ACTIVE NAVIGATION STATE MANAGEMENT
    // ============================================

    /**
     * Sets the active navigation button and updates styling.
     * Removes active state from previous button.
     *
     * @param button The button to set as active
     */
    private void setActiveNavButton(Button button) {
       if(activeNavButton!=null){
           activeNavButton.getStyleClass().remove("nav-button-active");
           AnimationUtil.scaleReset(activeNavButton,AnimationUtil.HOVER_SCALE_DURATION);
       }

       activeNavButton=button;

       if(activeNavButton!=null){
           if(!activeNavButton.getStyleClass().contains("nav-button-active")){
               activeNavButton.getStyleClass().add("nav-button-active");
           }
           AnimationUtil.pulse(activeNavButton,1.05);
       }


    }

    // ============================================
    // FUTURE EXTENSION METHODS
    // ============================================




    private void loadContent(Node contentNode) {

        if (contentNode == null) return;

        Node oldContent = rootPane.getCenter();
        AnimationUtil.contentTransition(oldContent, contentNode, null);
        rootPane.setCenter(contentNode);

    }



    /**
     * Refreshes dashboard data.
     * Placeholder for future data loading.
     */
    private void refreshDashboard() {
        // TODO: Fetch and update dashboard statistics
        // TODO: Update todo list items
        // TODO: Update upcoming deadlines

    }
}