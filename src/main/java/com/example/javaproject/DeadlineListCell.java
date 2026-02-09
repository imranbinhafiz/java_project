package com.example.javaproject;

import javafx.scene.control.ListCell;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.geometry.Insets;
import javafx.geometry.Pos;

/**
 * Custom ListCell for rendering Deadline objects.
 * Provides visual feedback for overdue, urgent, and approaching deadlines.
 */
public class DeadlineListCell extends ListCell<Deadline> {

    private VBox cardContainer;
    private VBox contentContainer;
    private Label titleLabel;
    private HBox bottomRow;
    private Label daysLeftLabel;
    private Label dateLabel;

    /**
     * Constructor - sets up the cell layout.
     */
    public DeadlineListCell() {
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
        titleLabel.getStyleClass().add("deadline-item-title");

        // Days left label
        daysLeftLabel = new Label();
        daysLeftLabel.getStyleClass().add("deadline-days-left");

        // Date label
        dateLabel = new Label();
        dateLabel.getStyleClass().add("deadline-date");

        // Bottom row with time info
        bottomRow = new HBox(12);
        bottomRow.setAlignment(Pos.CENTER_LEFT);
        bottomRow.getChildren().addAll(daysLeftLabel, dateLabel);

        // Content container
        contentContainer = new VBox(8);
        contentContainer.setAlignment(Pos.CENTER_LEFT);
        contentContainer.getChildren().addAll(titleLabel, bottomRow);
        VBox.setVgrow(contentContainer, Priority.ALWAYS);

        // Card container with padding
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
    protected void updateItem(Deadline item, boolean empty) {
        super.updateItem(item, empty);

        if (empty || item == null) {
            setText(null);
            setGraphic(null);
            getStyleClass().removeAll("deadline-item-overdue", "deadline-item-urgent", 
                                      "deadline-item-approaching", "deadline-item-normal");
        } else {
            // Update title
            titleLabel.setText(" " + item.getTitle());

            // Update days left
            daysLeftLabel.setText("⏳ " + item.getDaysLeftText());

            // Update date
            dateLabel.setText("📅 " + item.getFormattedDate());

            // Apply uniform blue styling for all deadline states
            getStyleClass().removeAll("deadline-item-overdue", "deadline-item-urgent", 
                                      "deadline-item-approaching", "deadline-item-normal");

            // All items get the same blue theme styling
            getStyleClass().add("deadline-item-normal");
            titleLabel.setStyle("-fx-text-fill: #f8fafc; -fx-font-weight: bold");
            daysLeftLabel.setStyle("-fx-text-fill: #93c5fd; -fx-font-weight: bold");
            dateLabel.setStyle("-fx-text-fill: #94a3b8; -fx-font-weight: bold");

            setGraphic(cardContainer);
        }
    }
}
