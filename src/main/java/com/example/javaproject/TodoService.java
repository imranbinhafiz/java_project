package com.example.javaproject;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service layer for managing Todo items.
 * Provides business logic and data management independent of UI.
 * Uses ObservableList for automatic UI updates.
 */
public class TodoService {

    // ObservableList for automatic UI binding
    private final ObservableList<TodoItem> todos;

    /**
     * Initialize the service with an empty todo list.
     */
    public TodoService() {
        this.todos = FXCollections.observableArrayList();
    }

    // ============================================
    // CRUD OPERATIONS
    // ============================================

    /**
     * Add a new todo item.
     * @param item The TodoItem to add
     * @return true if added successfully, false if item is null
     */
    public boolean addTodo(TodoItem item) {
        if (item == null) {
            return false;
        }
        todos.add(item);
        return true;
    }

    /**
     * Add a new todo with just a title.
     * Convenience method for quick task creation.
     * @param title The task title
     * @return true if added successfully, false if title is empty
     */
    public boolean addTodo(String title) {
        if (title == null || title.trim().isEmpty()) {
            return false;
        }
        return addTodo(new TodoItem(title));
    }

    /**
     * Add a new todo with title and due date.
     * @param title The task title
     * @param dueDate The due date
     * @return true if added successfully
     */
    public boolean addTodo(String title, LocalDate dueDate) {
        if (title == null || title.trim().isEmpty()) {
            return false;
        }
        return addTodo(new TodoItem(title, dueDate));
    }

    /**
     * Remove a todo item.
     * @param item The TodoItem to remove
     * @return true if removed successfully, false if item not found
     */
    public boolean removeTodo(TodoItem item) {
        if (item == null) {
            return false;
        }
        return todos.remove(item);
    }

    /**
     * Remove a todo by index.
     * @param index The index of the item to remove
     * @return true if removed successfully, false if index is invalid
     */
    public boolean removeTodoByIndex(int index) {
        if (index < 0 || index >= todos.size()) {
            return false;
        }
        todos.remove(index);
        return true;
    }

    /**
     * Toggle completion status of a todo item.
     * @param item The item to toggle
     * @return true if toggled successfully
     */
    public boolean toggleCompleted(TodoItem item) {
        if (item == null || !todos.contains(item)) {
            return false;
        }
        item.toggleCompleted();
        // Force list update for UI refresh
        int index = todos.indexOf(item);
        todos.set(index, item);
        return true;
    }

    /**
     * Mark a todo as completed.
     * @param item The item to mark as completed
     * @return true if successful
     */
    public boolean markCompleted(TodoItem item) {
        if (item == null || !todos.contains(item)) {
            return false;
        }
        if (!item.isCompleted()) {
            item.setCompleted(true);
            int index = todos.indexOf(item);
            todos.set(index, item);
        }
        return true;
    }

    /**
     * Mark a todo as incomplete.
     * @param item The item to mark as incomplete
     * @return true if successful
     */
    public boolean markIncomplete(TodoItem item) {
        if (item == null || !todos.contains(item)) {
            return false;
        }
        if (item.isCompleted()) {
            item.setCompleted(false);
            int index = todos.indexOf(item);
            todos.set(index, item);
        }
        return true;
    }

    /**
     * Clear all completed todos.
     * @return Number of items removed
     */
    public int clearCompleted() {
        List<TodoItem> completed = todos.stream()
                .filter(TodoItem::isCompleted)
                .collect(Collectors.toList());

        todos.removeAll(completed);
        return completed.size();
    }

    /**
     * Clear all todos.
     */
    public void clearAll() {
        todos.clear();
    }

    // ============================================
    // QUERY METHODS
    // ============================================

    /**
     * Get the observable list of todos for UI binding.
     * @return ObservableList of TodoItems
     */
    public ObservableList<TodoItem> getTodos() {
        return todos;
    }

    /**
     * Get total number of todos.
     * @return Total count
     */
    public int getTotalCount() {
        return todos.size();
    }

    /**
     * Get number of completed todos.
     * @return Completed count
     */
    public int getCompletedCount() {
        return (int) todos.stream()
                .filter(TodoItem::isCompleted)
                .count();
    }

    /**
     * Get number of pending (incomplete) todos.
     * @return Pending count
     */
    public int getPendingCount() {
        return getTotalCount() - getCompletedCount();
    }

    /**
     * Get all completed todos.
     * @return List of completed items
     */
    public List<TodoItem> getCompletedTodos() {
        return todos.stream()
                .filter(TodoItem::isCompleted)
                .collect(Collectors.toList());
    }

    /**
     * Get all pending todos.
     * @return List of pending items
     */
    public List<TodoItem> getPendingTodos() {
        return todos.stream()
                .filter(item -> !item.isCompleted())
                .collect(Collectors.toList());
    }

    /**
     * Get all overdue todos.
     * @return List of overdue items
     */
    public List<TodoItem> getOverdueTodos() {
        return todos.stream()
                .filter(TodoItem::isOverdue)
                .collect(Collectors.toList());
    }

    /**
     * Get todos due today.
     * @return List of todos due today
     */
    public List<TodoItem> getTodosDueToday() {
        LocalDate today = LocalDate.now();
        return todos.stream()
                .filter(item -> item.hasDueDate() && item.getDueDate().equals(today))
                .collect(Collectors.toList());
    }

    /**
     * Check if there are any todos.
     * @return true if list is empty
     */
    public boolean isEmpty() {
        return todos.isEmpty();
    }

    /**
     * Get a summary string for display.
     * @return Summary in format "X pending, Y completed"
     */
    public String getSummary() {
        int pending = getPendingCount();
        int completed = getCompletedCount();

        if (getTotalCount() == 0) {
            return "0 tasks";
        }

        if (pending == 0) {
            return "All done! (" + completed + " completed)";
        }

        return pending + " pending" + (completed > 0 ? ", " + completed + " done" : "");
    }

    /**
     * Get task count badge text.
     * @return Badge text like "5 tasks" or "1 task"
     */
    public String getTaskCountBadge() {
        int total = getTotalCount();
        return total + (total == 1 ? " task" : " tasks");
    }
}