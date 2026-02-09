package com.example.javaproject;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * Model class representing a single Deadline item.
 * Contains title, date, and dynamically calculates days remaining.
 */
public class Deadline {

    private String title;
    private LocalDate date;

    // Date formatter for consistent display
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MMM dd, yyyy");

    /**
     * Constructor with title and date.
     * @param title The deadline title/event name
     * @param date The deadline date
     */
    public Deadline(String title, LocalDate date) {
        if (title == null || title.trim().isEmpty()) {
            throw new IllegalArgumentException("Deadline title cannot be empty");
        }
        if (date == null) {
            throw new IllegalArgumentException("Deadline date cannot be null");
        }
        this.title = title.trim();
        this.date = date;
    }

    // ============================================
    // GETTERS AND SETTERS
    // ============================================

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        if (title == null || title.trim().isEmpty()) {
            throw new IllegalArgumentException("Deadline title cannot be empty");
        }
        this.title = title.trim();
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        if (date == null) {
            throw new IllegalArgumentException("Deadline date cannot be null");
        }
        this.date = date;
    }

    // ============================================
    // DYNAMIC DATE CALCULATIONS
    // ============================================

    /**
     * Calculate days remaining until deadline.
     * Returns negative value if overdue.
     * IMPORTANT: Always calculated dynamically, never stored.
     * 
     * @return Days left (negative if overdue)
     */
    public long getDaysLeft() {
        return ChronoUnit.DAYS.between(LocalDate.now(), date);
    }

    /**
     * Check if this deadline is overdue.
     * @return true if date is in the past
     */
    public boolean isOverdue() {
        return date.isBefore(LocalDate.now());
    }

    /**
     * Check if deadline is today.
     * @return true if deadline is today
     */
    public boolean isToday() {
        return date.equals(LocalDate.now());
    }

    /**
     * Check if deadline is urgent (≤ 2 days).
     * @return true if 2 days or less remaining
     */
    public boolean isUrgent() {
        long daysLeft = getDaysLeft();
        return daysLeft >= 0 && daysLeft <= 2;
    }

    /**
     * Check if deadline is approaching (≤ 7 days).
     * @return true if 7 days or less remaining
     */
    public boolean isApproaching() {
        long daysLeft = getDaysLeft();
        return daysLeft >= 0 && daysLeft <= 7;
    }

    /**
     * Get formatted date string.
     * @return Formatted date (e.g., "Feb 20, 2026")
     */
    public String getFormattedDate() {
        return date.format(DATE_FORMATTER);
    }

    /**
     * Get days left display text.
     * @return Human-readable time remaining (e.g., "5 days left", "Overdue", "Today")
     */
    public String getDaysLeftText() {
        if (isToday()) {
            return "Today";
        }
        
        long daysLeft = getDaysLeft();
        
        if (daysLeft < 0) {
            return "Overdue";
        } else if (daysLeft == 0) {
            return "Today";
        } else if (daysLeft == 1) {
            return "1 day left";
        } else {
            return daysLeft + " days left";
        }
    }

    // ============================================
    // OBJECT METHODS
    // ============================================

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        Deadline other = (Deadline) obj;
        return title.equals(other.title) && date.equals(other.date);
    }

    @Override
    public int hashCode() {
        int result = title.hashCode();
        result = 31 * result + date.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return title + " - " + getFormattedDate() + " (" + getDaysLeftText() + ")";
    }
}
