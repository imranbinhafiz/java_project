package com.example.javaproject;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.Node;
import javafx.event.ActionEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.MouseButton;
import java.io.IOException;


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
    // STATE MANAGEMENT
    // ============================================

    private boolean isSidebarCollapsed = false;
    private Button activeNavButton = null;

    // Todo Service - manages all todo operations
    private TodoService todoService;

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

        // Bind ListView to todo service
        todoListView.setItems(todoService.getTodos());

        // Set up cell factory for custom todo item display
        setupTodoListDisplay();

        // Set default active navigation (Dashboard)
        setActiveNavButton(dashboardBtn);

        // Apply fade-in animation to content area
        AnimationUtil.fadeIn(contentArea, AnimationUtil.FADE_IN_DURATION, null);

        // Update task count badge
        updateTaskCountBadge();

        // Add some sample tasks for demonstration (optional - remove in production)
        // addSampleTasks();
    }

    // ============================================
    // TODO LIST FUNCTIONALITY
    // ============================================

    /**
     * Sets up custom cell factory for todo items.
     * Creates styled cells with proper formatting.
     */
    private void setupTodoListDisplay() {
        todoListView.setCellFactory(listView -> new TodoListCell());

        // Add selection listener to enable/disable delete button
        todoListView.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, newValue) -> {
                    deleteSelectedButton.setDisable(newValue == null);
                }
        );

        // Initially disable delete button (no selection)
        deleteSelectedButton.setDisable(true);
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
        }
    }

    /**
     * Handles clicking on a todo item in the list.
     * Double-click toggles completion status.
     */
    @FXML
    private void handleTodoClick(MouseEvent event) {
        if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
            TodoItem selectedItem = todoListView.getSelectionModel().getSelectedItem();

            if (selectedItem != null) {
                // Toggle completion
                todoService.toggleCompleted(selectedItem);

                // Update badge
                updateTaskCountBadge();

                // Refresh list view
                todoListView.refresh();

                // Apply pulse animation to the list for feedback
                AnimationUtil.pulse(todoListView, 1.02);
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

    /**
     * Handles clearing all completed todos.
     */
    @FXML
    private void handleClearCompleted(ActionEvent event) {
        int removedCount = todoService.clearCompleted();

        if (removedCount > 0) {
            // Update badge
            updateTaskCountBadge();

            // Apply animation for feedback
            AnimationUtil.pulse(clearCompletedButton, 1.08);

            System.out.println("Cleared " + removedCount + " completed task(s)");
        }
    }

    /**
     * Updates the task count badge in the header.
     */
    private void updateTaskCountBadge() {
        if (taskCountBadge != null) {
            taskCountBadge.setText(todoService.getTaskCountBadge());
        }
    }

    /**
     * Add sample tasks for demonstration.
     * Remove this method in production or call only for demo purposes.
     */
    private void addSampleTasks() {
        todoService.addTodo("Review calculus notes");
        todoService.addTodo("Complete physics assignment");
        todoService.addTodo("Practice coding problems");
        todoService.addTodo("Read chapter 5 of biology");
        updateTaskCountBadge();
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
        isSidebarCollapsed = !isSidebarCollapsed;

        // Animate sidebar with professional transition
        AnimationUtil.animateSidebarCollapse(sideNav, isSidebarCollapsed, () -> {
            // Callback after animation completes
            updateSidebarVisibility();
        });

        // Animate hamburger button for visual feedback
        AnimationUtil.pulse(toggleButton, 1.08);
    }

    /**
     * Updates sidebar text visibility based on collapsed state.
     * Hides text labels when collapsed, shows when expanded.
     */
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
        System.out.println("Dashboard clicked");
    }

    /**
     * Handles Courses button click.
     * Future: Load courses view content.
     */
    @FXML
    private void handleCoursesClick(ActionEvent event) {
        setActiveNavButton(coursesBtn);

        System.out.println("Courses clicked");
    }

    /**
     * Handles Duel button click.
     * Future: Load duel mode content.
     */
    @FXML
    private void handleDuelClick(ActionEvent event) {
        setActiveNavButton(duelBtn);
        System.out.println("Duel clicked");
    }

    /**
     * Handles Exams button click.
     * Future: Load exams view content.
     */
    @FXML
    private void handleExamsClick(ActionEvent event) {
        setActiveNavButton(examsBtn);
        System.out.println("Exams clicked");
    }

    /**
     * Handles Performance button click.
     * Future: Load performance analytics content.
     */
    @FXML
    private void handlePerformanceClick(ActionEvent event) {
        setActiveNavButton(performanceBtn);
        System.out.println("Performance clicked");
    }

    /**
     * Handles Settings button click.
     * Future: Load settings panel.
     */
    @FXML
    private void handleSettingsClick(ActionEvent event) {
        setActiveNavButton(settingsBtn);
        System.out.println("Settings clicked");
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
        // Remove active class from previous button
        if (activeNavButton != null) {
            activeNavButton.getStyleClass().remove("nav-button-active");
            AnimationUtil.scaleReset(activeNavButton, AnimationUtil.HOVER_SCALE_DURATION);
        }

        // Set new active button
        activeNavButton = button;

        if (activeNavButton != null) {
            // Add active class to new button
            if (!activeNavButton.getStyleClass().contains("nav-button-active")) {
                activeNavButton.getStyleClass().add("nav-button-active");
            }

            // Apply subtle pulse animation for feedback
            AnimationUtil.pulse(activeNavButton, 1.05);
        }
    }

    // ============================================
    // FUTURE EXTENSION METHODS
    // ============================================

    /**
     * Loads content into the main content area.
     * Template method for future content switching.
     *
     * @param contentNode The node to display in the content area
     */
    private void loadContent(Node contentNode) {
        // TODO: Implement content switching with transitions
        // AnimationUtil.contentTransition(oldContent, contentNode, null);
        System.out.println("Content loading - To be implemented");
    }

    /**
     * Updates the image display pane.
     * Placeholder for future motivational images/GIFs.
     *
     * @param imageUrl The URL of the image to display
     */
    private void updateMotivationalImage(String imageUrl) {
        // TODO: Load and display image in imageDisplayPane
        System.out.println("Image update - To be implemented");
    }

    /**
     * Refreshes dashboard data.
     * Placeholder for future data loading.
     */
    private void refreshDashboard() {
        // TODO: Fetch and update dashboard statistics
        // TODO: Update todo list items
        // TODO: Update upcoming deadlines
        System.out.println("Dashboard refresh - To be implemented");
    }
}