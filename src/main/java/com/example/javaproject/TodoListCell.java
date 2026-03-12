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
 * Matches the design of DeadlineListCell.
 */
public class TodoListCell extends ListCell<TodoItem> {

    private VBox cardContainer;
    private VBox contentContainer;
    private Label titleLabel;
    private HBox bottomRow;
    private Label statusLabel;
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
        // Title label
        titleLabel = new Label();
        titleLabel.setWrapText(true);
        titleLabel.setMaxWidth(Double.MAX_VALUE);
        titleLabel.getStyleClass().add("todo-item-title");

        // Status label (replacing the simple checkbox icon)
        statusLabel = new Label();
        statusLabel.getStyleClass().add("todo-item-status");

        // Due date label
        dueDateLabel = new Label();
        dueDateLabel.getStyleClass().add("todo-item-date");

        // Bottom row with status and date info
        bottomRow = new HBox(12);
        bottomRow.setAlignment(Pos.CENTER_LEFT);
        bottomRow.getChildren().addAll(statusLabel, dueDateLabel);

        // Content container
        contentContainer = new VBox(8);
        contentContainer.setAlignment(Pos.CENTER_LEFT);
        contentContainer.getChildren().addAll(titleLabel, bottomRow);
        VBox.setVgrow(contentContainer, Priority.ALWAYS);

        // Card container with padding - matches DeadlineListCell
        cardContainer = new VBox();
        cardContainer.getStyleClass().add("deadline-list-cell-card");
        cardContainer.setPadding(new Insets(14, 16, 14, 16));
        cardContainer.getChildren().add(contentContainer);

        // Apply hover effects
        cardContainer.setOnMouseEntered(e -> {
            if (!isEmpty()) {
                AnimationUtil.scaleHover(cardContainer, 1.02, AnimationUtil.HOVER_SCALE_DURATION);
            }
        });

        cardContainer.setOnMouseExited(e -> {
            if (!isEmpty()) {
                AnimationUtil.scaleReset(cardContainer, AnimationUtil.HOVER_SCALE_DURATION);
            }
        });
    }

    @Override
    protected void updateItem(TodoItem item, boolean empty) {
        super.updateItem(item, empty);

        if (empty || item == null) {
            setText(null);
            setGraphic(null);
        } else {
            // Update title
            titleLabel.setText(" " + item.getTitle());

            // Update status
            if (item.isCompleted()) {
                statusLabel.setText("✅ Completed");
                statusLabel.setStyle("-fx-text-fill: #4ade80; -fx-font-weight: bold;");
                titleLabel.setStyle("-fx-text-fill: #94a3b8; -fx-strikethrough: true;");
            } else if (item.isOverdue()) {
                statusLabel.setText("⚠️ Overdue");
                statusLabel.setStyle("-fx-text-fill: #fca5a5; -fx-font-weight: bold;");
                titleLabel.setStyle("-fx-text-fill: #fca5a5; -fx-font-weight: bold;");
            } else {
                statusLabel.setText("⏳ Pending");
                statusLabel.setStyle("-fx-text-fill: #93c5fd; -fx-font-weight: bold;");
                titleLabel.setStyle("-fx-text-fill: #f8fafc; -fx-font-weight: bold;");
            }

            // Update due date
            if (item.hasDueDate()) {
                dueDateLabel.setText("📅 " + item.getFormattedDueDate());
                dueDateLabel.setVisible(true);
                dueDateLabel.setManaged(true);
            } else {
                dueDateLabel.setVisible(false);
                dueDateLabel.setManaged(false);
            }

            setGraphic(cardContainer);
        }
    }
}