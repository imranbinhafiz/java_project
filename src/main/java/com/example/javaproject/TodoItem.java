package com.example.javaproject;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Model class representing a single Todo item.
 * Contains title, completion status, and optional due date.
 */
public class TodoItem {

    private String title;
    private boolean completed;
    private LocalDate dueDate;

    // Date formatter for consistent display
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MMM dd, yyyy");

    /**
     * Constructor with title only.
     * @param title The task title
     */
    public TodoItem(String title) {
        this(title, null);
//        Meaning:
//
//        If user gives only title
//
//        Then call second constructor
//
//        And set dueDate = null
    }

    /**
     * Full constructor with title and due date.
     * @param title The task title
     * @param dueDate Optional due date (can be null)
     */
    public TodoItem(String title, LocalDate dueDate) {
        if (title == null || title.trim().isEmpty()) {
            throw new IllegalArgumentException("Task title cannot be empty");
        }
        this.title = title.trim();//removes spaces
        this.completed = false;
        this.dueDate = dueDate;
    }

    // ============================================
    // GETTERS AND SETTERS
    // ============================================

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        if (title == null || title.trim().isEmpty()) {
            throw new IllegalArgumentException("Task title cannot be empty");
        }
        this.title = title.trim();
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    /**
     * Toggle completion status.
     */
    public void toggleCompleted() {
        this.completed = !this.completed;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    /**
     * Check if this task has a due date.
     * @return true if due date is set, false otherwise
     */
    public boolean hasDueDate() {
        return dueDate != null;
    }

    /**
     * Check if task is overdue.
     * @return true if task has a due date and it's before today
     */
    public boolean isOverdue() {
        if (dueDate == null || completed) {
            return false;
        }
        return dueDate.isBefore(LocalDate.now());
    }

    /**
     * Get formatted due date string.
     * @return Formatted date or empty string if no due date
     */
    public String getFormattedDueDate() {
        return dueDate != null ? dueDate.format(DATE_FORMATTER) : "";
    }

    // ============================================
    // DISPLAY METHODS
    // ============================================

    /**
     * Returns a display string for the ListView.
     * Format: [✅/☐] Title (Due: Date)
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        // Add completion checkbox
        sb.append(completed ? "✅ " : "☐ ");

        // Add title
        sb.append(title);

        // Add due date if exists
        if (dueDate != null) {
            sb.append(" (Due: ").append(getFormattedDueDate()).append(")");
        }

        return sb.toString();
    }

    /**
     * Get a simple display without checkbox (for editing).
     * @return Title with optional due date
     */
    public String getDisplayText() {
        if (dueDate != null) {
            return title + " (Due: " + getFormattedDueDate() + ")";
        }
        return title;
    }

    // ============================================
    // OBJECT METHODS
    // ============================================

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        TodoItem other = (TodoItem) obj;
        return title.equals(other.title) &&
                completed == other.completed &&
                (dueDate == null ? other.dueDate == null : dueDate.equals(other.dueDate));
    }

    @Override
    public int hashCode() {
        int result = title.hashCode();
        result = 31 * result + (completed ? 1 : 0);
        result = 31 * result + (dueDate != null ? dueDate.hashCode() : 0);
        return result;
    }
}