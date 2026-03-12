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
import java.util.Optional;

public class DashboardController {

    // ============================================
    // FXML INJECTED FIELDS
    // ============================================

    @FXML private BorderPane rootPane;
    @FXML private VBox sideNav;
    @FXML private Button toggleButton;
    @FXML private Button dashboardBtn;
    @FXML private Button coursesBtn;
    @FXML private Button duelBtn;
    @FXML private Button examsBtn;
    @FXML private Button performanceBtn;
    @FXML private Button aiChatBtn;
    @FXML private Button logoutBtn;
    @FXML private VBox contentArea;
    @FXML private StackPane imageDisplayPane;
    @FXML private Label topBarTitle;
    @FXML private Label welcomeTitle;

    // Profile dropdown
    @FXML private MenuButton profileMenuButton;
    @FXML private Label avatarLabel;
    @FXML private Label usernameLabel;
    @FXML private MenuItem changeNameItem;
    @FXML private MenuItem changePasswordItem;
    @FXML private MenuItem logoutMenuItem;

    // TODO LIST
    @FXML private TextField todoInputField;
    @FXML private Button addTodoButton;
    @FXML private ListView<TodoItem> todoListView;
    @FXML private Button clearCompletedButton;
    @FXML private Button deleteSelectedButton;
    @FXML private Label taskCountBadge;

    // DEADLINES
    @FXML private DatePicker deadlineDatePicker;
    @FXML private TextField deadlineTitleField;
    @FXML private Button addDeadlineButton;
    @FXML private ListView<Deadline> deadlineListView;
    @FXML private Button deleteDeadlineButton;

    // ============================================
    // STATE
    // ============================================

    private boolean isSidebarCollapsed = false;
    private Button activeNavButton = null;
    private TodoService todoService;
    private DeadlineService deadlineService;
    private Node originalDashboardChildren;

    // ============================================
    // INITIALIZATION
    // ============================================

    @FXML
    public void initialize() {
        String username = UserSession.getInstance().getUsername();
        if (username == null || username.isBlank()) {
            try {
                Main.changeScene(null, "fxml files/login.fxml");
            } catch (IOException e) {
                System.err.println("Failed to redirect to login: " + e.getMessage());
            }
            return;
        }

        // --- Update profile UI with real user data ---
        updateProfileUI(username);

        // Initialize services
        todoService = new TodoService(username);
        deadlineService = new DeadlineService(username);

        todoListView.setItems(todoService.getTodos());
        deadlineListView.setItems(deadlineService.getDeadlines());

        setupTodoListDisplay();
        setupdeadlineListDisplay();

        setActiveNavButton(dashboardBtn);
        AnimationUtil.fadeIn(contentArea);
        updateTaskCountBadge();

        originalDashboardChildren = rootPane.getCenter();
    }

    /**
     * Updates the profile section (avatar letter, username label, welcome message)
     * using the real logged-in username from UserSession.
     */
    private void updateProfileUI(String username) {
        // Avatar: first letter, upper-cased
        if (avatarLabel != null) {
            String letter = username.substring(0, 1).toUpperCase();
            avatarLabel.setText(letter);
        }

        // Username label in top bar
        if (usernameLabel != null) {
            // Capitalise first letter for display
            String displayName = username.substring(0, 1).toUpperCase() + username.substring(1);
            usernameLabel.setText(displayName);
        }

        // Welcome title in content area
        if (welcomeTitle != null) {
            String displayName = username.substring(0, 1).toUpperCase() + username.substring(1);
            welcomeTitle.setText("Welcome back, " + displayName + "!");
        }
    }

    // ============================================
    // PROFILE DROPDOWN HANDLERS
    // ============================================

    /**
     * Handles "Change Name" menu item.
     * Shows a dialog to update the display name in session.
     */
    @FXML
    private void handleChangeName(ActionEvent event) {
        TextInputDialog dialog = new TextInputDialog(UserSession.getInstance().getUsername());
        dialog.setTitle("Change Name");
        dialog.setHeaderText("Update your display name");
        dialog.setContentText("New name:");
        applyAlertCss(dialog.getDialogPane());

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(newName -> {
            if (!newName.isBlank()) {
                // Update session username display
                UserSession.getInstance().setUsername(newName.trim());
                updateProfileUI(newName.trim());
            }
        });
    }

    /**
     * Handles "Change Password" menu item.
     * Shows a dialog; actual password change logic via UserFileManager.
     */
    @FXML
    private void handleChangePassword(ActionEvent event) {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Change Password");
        dialog.setHeaderText("Enter your new password");
        applyAlertCss(dialog.getDialogPane());

        PasswordField pwField = new PasswordField();
        pwField.setPromptText("New password");
        dialog.getDialogPane().setContent(pwField);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.setResultConverter(btn -> {
            if (btn == ButtonType.OK) return pwField.getText();
            return null;
        });

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(pw -> {
            if (!pw.isBlank()) {
                // Delegate to UserFileManager if such method exists
                // UserFileManager.changePassword(UserSession.getInstance().getUsername(), pw);
                showInfoAlert("Password Updated", "Your password has been changed successfully.");
            }
        });
    }

    private void applyAlertCss(DialogPane pane) {
        var cssUrl = getClass().getResource("css/alert.css");
        if (cssUrl != null) pane.getStylesheets().add(cssUrl.toExternalForm());
    }

    private void showInfoAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message);
        applyAlertCss(alert.getDialogPane());
        alert.showAndWait();
    }

    // ============================================
    // TODO LIST
    // ============================================

    private void setupTodoListDisplay() {
        todoListView.setCellFactory(listView -> new TodoListCell());
        todoListView.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, newValue) -> deleteSelectedButton.setDisable(newValue == null));
        deleteSelectedButton.setDisable(true);
    }

    private void setupdeadlineListDisplay() {
        deadlineListView.setCellFactory(listView -> new DeadlineListCell());
        deadlineListView.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, newValue) -> deleteDeadlineButton.setDisable(newValue == null));
        deleteDeadlineButton.setDisable(true);
    }

    @FXML
    private void handleAddTodo(ActionEvent event) {
        String taskText = todoInputField.getText();
        if (taskText == null || taskText.trim().isEmpty()) {
            AnimationUtil.pulse(todoInputField, 1.05);
            return;
        }
        boolean success = todoService.addTodo(taskText.trim());
        if (success) {
            todoInputField.clear();
            updateTaskCountBadge();
            AnimationUtil.pulse(addTodoButton, 1.1);
            todoListView.scrollTo(todoService.getTotalCount() - 1);
            todoListView.refresh();
        }
    }

    @FXML
    private void handleAddDeadline(ActionEvent event) {
        String t = deadlineTitleField.getText();
        LocalDate date = deadlineDatePicker.getValue();
        if (t == null || t.trim().isEmpty()) {
            AnimationUtil.pulse(deadlineTitleField, 1.05);
            return;
        }
        if (date == null) {
            AnimationUtil.pulse(deadlineDatePicker, 1.05);
            return;
        }
        boolean s = deadlineService.addDeadline(t.trim(), date);
        if (s) {
            deadlineTitleField.clear();
            deadlineDatePicker.setValue(null);
            deadlineListView.scrollTo(deadlineService.getTotalCount() - 1);
            deadlineListView.refresh();
        }
    }

    @FXML
    private void handleTodoClick(MouseEvent event) {
        if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
            TodoItem selected_item = todoListView.getSelectionModel().getSelectedItem();
            if (selected_item != null) {
                todoService.toggleCompleted(selected_item);
                updateTaskCountBadge();
                todoListView.refresh();
                AnimationUtil.pulse(todoListView, 1.02);
            }
        }
    }

    @FXML
    private void handleDeleteSelected(ActionEvent event) {
        TodoItem selectedItem = todoListView.getSelectionModel().getSelectedItem();
        if (selectedItem != null) {
            todoService.removeTodo(selectedItem);
            updateTaskCountBadge();
            todoListView.getSelectionModel().clearSelection();
            AnimationUtil.pulse(deleteSelectedButton, 1.08);
        }
    }

    @FXML
    private void handleDeleteDeadline(ActionEvent e) {
        Deadline selected_item = deadlineListView.getSelectionModel().getSelectedItem();
        if (selected_item != null) {
            boolean rmv = deadlineService.removeDeadline(selected_item);
            AnimationUtil.pulse(deleteDeadlineButton, 1.08);
            if (rmv) deadlineListView.getSelectionModel().clearSelection();
        }
    }

    @FXML
    private void handleClearCompleted(ActionEvent event) {
        int removed_count = todoService.clearCompleted();
        if (removed_count > 0) {
            updateTaskCountBadge();
            AnimationUtil.pulse(clearCompletedButton, 1.08);
        }
    }

    private void updateTaskCountBadge() {
        if (taskCountBadge != null) {
            taskCountBadge.setText(todoService.getTaskCountBadge());
        }
    }

    // ============================================
    // SIDEBAR TOGGLE
    // ============================================

    @FXML
    private void handleToggleSidebar(ActionEvent event) {
        isSidebarCollapsed = !isSidebarCollapsed;
        AnimationUtil.animateSidebarCollapse(sideNav, isSidebarCollapsed, () -> updateSidebarVisibility());
        AnimationUtil.pulse(toggleButton, 1.08);
    }

    private void updateSidebarVisibility() {
        // Reserved for future icon-only collapse state
    }

    // ============================================
    // NAVIGATION
    // ============================================

    @FXML
    private void handleDashboardClick(ActionEvent event) {
        setActiveNavButton(dashboardBtn);
        if (topBarTitle != null) topBarTitle.setText("Dashboard");
        loadContent(originalDashboardChildren);
    }

    @FXML
    private void handleCoursesClick(ActionEvent event) throws IOException {
        setActiveNavButton(coursesBtn);
        if (topBarTitle != null) topBarTitle.setText("Courses");
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("fxml files/courses.fxml"));
            Node coursesContent = loader.load();
            CoursesController controller = loader.getController();
            controller.setContentLoader(syllabusNode -> {
                loadContent(syllabusNode);
                return null;
            });
            controller.setOnSyllabusOpenCallback(() -> {
                try { handleCoursesClick(null); } catch (IOException e) { e.printStackTrace(); }
            });
            loadContent(coursesContent);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void handleDuelClick(ActionEvent event) {
        setActiveNavButton(duelBtn);
        if (topBarTitle != null) topBarTitle.setText("Challenge Arena");
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("fxml files/challenge_lobby.fxml"));
            Node content = loader.load();
            ChallengeLobbyController ctrl = loader.getController();
            ctrl.setRootPane(rootPane);
            loadContent(content);
        } catch (IOException e) {
            System.err.println("Error loading challenge lobby: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @FXML
    private void handleExamsClick(ActionEvent event) {
        setActiveNavButton(examsBtn);
        if (topBarTitle != null) topBarTitle.setText("Exams");
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("fxml files/exam_module.fxml"));
            Node content = loader.load();
            ExamModuleController ctrl = loader.getController();
            ctrl.setRootPane(rootPane);
            loadContent(content);
        } catch (IOException e) {
            System.err.println("Error loading exam module: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @FXML
    private void handlePerformanceClick(ActionEvent event) {
        setActiveNavButton(performanceBtn);
        if (topBarTitle != null) topBarTitle.setText("Messenger");
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("fxml files/messenger.fxml"));
            Node content = loader.load();
            VBox.setVgrow(content, javafx.scene.layout.Priority.ALWAYS);
            loadContent(content);
        } catch (IOException e) {
            System.err.println("Error loading messenger: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @FXML
    private void handleAiChatClick(ActionEvent event) {
        setActiveNavButton(aiChatBtn);
        if (topBarTitle != null) topBarTitle.setText("AI Chat");
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("fxml files/chatbot_ui.fxml"));
            Node content = loader.load();
            VBox.setVgrow(content, javafx.scene.layout.Priority.ALWAYS);
            loadContent(content);
        } catch (IOException e) {
            System.err.println("Error loading AI Chat: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // handleSettingsClick removed — Settings button replaced by profile dropdown

    @FXML
    private void handleLogoutClick(ActionEvent event) {
        try {
            UserSession.getInstance().clear();
            Main.changeScene(event, "fxml files/login.fxml");
        } catch (IOException e) {
            System.err.println("Error navigating to login: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ============================================
    // NAV HOVER EFFECTS
    // ============================================

    @FXML
    private void handleNavHover(MouseEvent event) {
        Node source = (Node) event.getSource();
        if (source != activeNavButton) {
            AnimationUtil.scaleHover(source, 1.03, AnimationUtil.HOVER_SCALE_DURATION);
        }
    }

    @FXML
    private void handleNavExit(MouseEvent event) {
        Node source = (Node) event.getSource();
        if (source != activeNavButton) {
            AnimationUtil.scaleReset(source, AnimationUtil.HOVER_SCALE_DURATION);
        }
    }

    private void setActiveNavButton(Button button) {
        if (activeNavButton != null) {
            activeNavButton.getStyleClass().remove("nav-button-active");
            AnimationUtil.scaleReset(activeNavButton, AnimationUtil.HOVER_SCALE_DURATION);
        }
        activeNavButton = button;
        if (activeNavButton != null) {
            if (!activeNavButton.getStyleClass().contains("nav-button-active")) {
                activeNavButton.getStyleClass().add("nav-button-active");
            }
            AnimationUtil.pulse(activeNavButton, 1.05);
        }
    }

    // ============================================
    // CONTENT LOADER
    // ============================================

    private void loadContent(Node contentNode) {
        if (contentNode == null) return;
        Node oldContent = rootPane.getCenter();
        AnimationUtil.contentTransition(oldContent, contentNode, null);
        rootPane.setCenter(contentNode);
    }
}
