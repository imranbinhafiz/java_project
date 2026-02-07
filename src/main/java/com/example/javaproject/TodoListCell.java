package com.example.javaproject;

import javafx.scene.control.ListCell;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.geometry.Insets;
import javafx.geometry.Pos;

/**
 * Custom ListCell for rendering TodoItem objects.
 * Provides visual feedback for completed vs pending tasks.
 */
public class TodoListCell extends ListCell<TodoItem> {

    private HBox content;
    private Label checkboxLabel;
    private VBox textContainer;
    private Label titleLabel;
    private Label dueDateLabel;

    /**
     * Constructor - sets up the cell layout.
     */
    public TodoListCell() {
        super();
        setupLayout();
    }

    /**
     * Sets up the visual layout of the cell.
     */
    private void setupLayout() {
        // Checkbox indicator
        checkboxLabel = new Label();
        checkboxLabel.setStyle("-fx-font-size: 16px; -fx-min-width: 24px;");

        // Text container for title and due date
        textContainer = new VBox(2);
        textContainer.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(textContainer, Priority.ALWAYS);

        // Title label
        titleLabel = new Label();
        titleLabel.setWrapText(true);
        titleLabel.setMaxWidth(Double.MAX_VALUE);
        titleLabel.getStyleClass().add("todo-item-title");

        // Due date label (optional)
        dueDateLabel = new Label();
        dueDateLabel.getStyleClass().add("todo-item-date");

        textContainer.getChildren().addAll(titleLabel, dueDateLabel);

        // Main content container
        content = new HBox(12);
        content.setAlignment(Pos.CENTER_LEFT);
        content.setPadding(new Insets(8, 12, 8, 12));
        content.getChildren().addAll(checkboxLabel, textContainer);

        // Apply base style class
        content.getStyleClass().add("todo-list-cell-content");
    }

    @Override
    protected void updateItem(TodoItem item, boolean empty) {
        super.updateItem(item, empty);

        if (empty || item == null) {
            setText(null);
            setGraphic(null);
            getStyleClass().removeAll("todo-item-completed", "todo-item-pending", "todo-item-overdue");
        } else {
            // Update checkbox
            checkboxLabel.setText(item.isCompleted() ? "✅" : "☐");

            // Update title
            titleLabel.setText(item.getTitle());

            // Update due date
            if (item.hasDueDate()) {
                dueDateLabel.setText("📅 Due: " + item.getFormattedDueDate());
                dueDateLabel.setVisible(true);
                dueDateLabel.setManaged(true);
            } else {
                dueDateLabel.setVisible(false);
                dueDateLabel.setManaged(false);
            }

            // Apply styling based on state
            getStyleClass().removeAll("todo-item-completed", "todo-item-pending", "todo-item-overdue");

            if (item.isCompleted()) {
                getStyleClass().add("todo-item-completed");
                titleLabel.setStyle("-fx-text-fill: #94a3b8; -fx-strikethrough: true;");
            } else if (item.isOverdue()) {
                getStyleClass().add("todo-item-overdue");
                titleLabel.setStyle("-fx-text-fill: #fca5a5; -fx-font-weight: 600;");
            } else {
                getStyleClass().add("todo-item-pending");
                titleLabel.setStyle("-fx-text-fill: #f8fafc; -fx-font-weight: 500;");
            }

            setGraphic(content);
        }
    }
}