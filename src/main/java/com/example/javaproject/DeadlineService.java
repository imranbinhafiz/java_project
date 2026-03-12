package com.example.javaproject;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service layer for managing Deadline items.
 * Provides business logic and data management independent of UI.
 * Uses ObservableList for automatic UI updates.
 * Persists data to the user's deadline file via UserFileManager.
 */
public class DeadlineService {

    // ObservableList for automatic UI binding
    private final ObservableList<Deadline> deadlines;

    // The username whose file we read/write
    private final String username;

    /**
     * Initialize the service for a specific user.
     * Loads existing deadlines from file on startup.
     *
     * @param username the logged-in username
     */
    public DeadlineService(String username) {
        this.username = username;
        this.deadlines = FXCollections.observableArrayList();
        // Load persisted deadlines from file
        List<Deadline> loaded = UserFileManager.loadDeadlines(username);
        deadlines.addAll(loaded);
        sortDeadlines();
    }

    /**
     * No-arg constructor for backward compatibility (uses empty list, no
     * persistence).
     * 
     * @deprecated Use DeadlineService(String username) instead.
     */
    @Deprecated
    public DeadlineService() {
        this.username = null;
        this.deadlines = FXCollections.observableArrayList();
    }

    /** Persist the full list to file (used after any mutation). */
    private void persistAll() {
        if (username != null) {
            UserFileManager.persistAllDeadlines(username, new ArrayList<>(deadlines));
        }
    }

    // ============================================
    // CRUD OPERATIONS
    // ============================================

    /**
     * Add a new deadline.
     * Automatically sorts list by nearest date first.
     *
     * @param deadline The Deadline to add
     * @return true if added successfully, false if deadline is null
     */
    public boolean addDeadline(Deadline deadline) {
        if (deadline == null) {
            return false;
        }
        deadlines.add(deadline);
        sortDeadlines();
        if (username != null) {
            // Persist the full sorted list (simpler than appending and re-sorting on load)
            persistAll();
        }
        return true;
    }

    /**
     * Add a new deadline with title and date.
     * Convenience method for quick deadline creation.
     *
     * @param title The deadline title
     * @param date  The deadline date
     * @return true if added successfully, false if validation fails
     */
    public boolean addDeadline(String title, LocalDate date) {
        if (title == null || title.trim().isEmpty() || date == null) {
            return false;
        }
        return addDeadline(new Deadline(title, date));
    }

    /**
     * Remove a deadline.
     *
     * @param deadline The Deadline to remove
     * @return true if removed successfully, false if not found
     */
    public boolean removeDeadline(Deadline deadline) {
        if (deadline == null) {
            return false;
        }
        boolean removed = deadlines.remove(deadline);
        if (removed)
            persistAll();
        return removed;
    }

    /**
     * Remove a deadline by index.
     *
     * @param index The index of the deadline to remove
     * @return true if removed successfully, false if index is invalid
     */
    public boolean removeDeadlineByIndex(int index) {
        if (index < 0 || index >= deadlines.size()) {
            return false;
        }
        deadlines.remove(index);
        persistAll();
        return true;
    }

    /**
     * Clear all deadlines.
     */
    public void clearAll() {
        deadlines.clear();
        persistAll();
    }

    /**
     * Clear all overdue deadlines.
     *
     * @return Number of items removed
     */
    public int clearOverdue() {
        List<Deadline> overdue = deadlines.stream()
                .filter(Deadline::isOverdue)
                .collect(Collectors.toList());

        deadlines.removeAll(overdue);
        if (!overdue.isEmpty())
            persistAll();
        return overdue.size();
    }

    // ============================================
    // SORTING
    // ============================================

    /**
     * Sort deadlines by date (nearest first).
     * Overdue items appear at the top.
     */
    public void sortDeadlines() {
        FXCollections.sort(deadlines, Comparator.comparing(Deadline::getDate));
    }

    // ============================================
    // QUERY METHODS
    // ============================================

    /**
     * Get the observable list of deadlines for UI binding.
     *
     * @return ObservableList of Deadlines
     */
    public ObservableList<Deadline> getDeadlines() {
        return deadlines;
    }

    /**
     * Get total number of deadlines.
     *
     * @return Total count
     */
    public int getTotalCount() {
        return deadlines.size();
    }

    /**
     * Get all overdue deadlines.
     *
     * @return List of overdue items
     */
    public List<Deadline> getOverdueDeadlines() {
        return deadlines.stream()
                .filter(Deadline::isOverdue)
                .collect(Collectors.toList());
    }

    /**
     * Get number of overdue deadlines.
     *
     * @return Overdue count
     */
    public int getOverdueCount() {
        return (int) deadlines.stream()
                .filter(Deadline::isOverdue)
                .count();
    }

    /**
     * Get all urgent deadlines (≤ 2 days).
     *
     * @return List of urgent items
     */
    public List<Deadline> getUrgentDeadlines() {
        return deadlines.stream()
                .filter(Deadline::isUrgent)
                .collect(Collectors.toList());
    }

    /**
     * Get number of urgent deadlines.
     *
     * @return Urgent count
     */
    public int getUrgentCount() {
        return (int) deadlines.stream()
                .filter(Deadline::isUrgent)
                .count();
    }

    /**
     * Get all approaching deadlines (≤ 7 days).
     *
     * @return List of approaching items
     */
    public List<Deadline> getApproachingDeadlines() {
        return deadlines.stream()
                .filter(Deadline::isApproaching)
                .collect(Collectors.toList());
    }

    /**
     * Get deadlines due today.
     *
     * @return List of deadlines due today
     */
    public List<Deadline> getDeadlinesToday() {
        return deadlines.stream()
                .filter(Deadline::isToday)
                .collect(Collectors.toList());
    }

    /**
     * Get upcoming deadlines (not overdue).
     *
     * @return List of upcoming deadlines
     */
    public List<Deadline> getUpcomingDeadlines() {
        return deadlines.stream()
                .filter(d -> !d.isOverdue())
                .collect(Collectors.toList());
    }

    /**
     * Check if there are any deadlines.
     *
     * @return true if list is empty
     */
    public boolean isEmpty() {
        return deadlines.isEmpty();
    }

    /**
     * Get a summary string for display.
     *
     * @return Summary message
     */
    public String getSummary() {
        int total = getTotalCount();
        int overdue = getOverdueCount();
        int urgent = getUrgentCount();

        if (total == 0) {
            return "No upcoming deadlines";
        }

        if (overdue > 0) {
            return overdue + " overdue, " + (total - overdue) + " upcoming";
        }

        if (urgent > 0) {
            return urgent + " urgent deadline" + (urgent == 1 ? "" : "s");
        }

        return total + " upcoming";
    }
}
