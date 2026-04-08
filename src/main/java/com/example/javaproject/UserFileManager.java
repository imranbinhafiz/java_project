package com.example.javaproject;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.nio.charset.StandardCharsets;
import javafx.collections.FXCollections;

/**
 * Utility class for all file-based user data operations.
 * Manages the data/ folder with users.txt and per-user data files.
 *
 * File layout:
 * data/users.txt – username,password per line
 * data/user_<username>_todo.txt – one todo item per line (text|completed)
 * data/user_<username>_deadline.txt – one deadline per line (title|YYYY-MM-DD)
 * data/user_<username>_courses.txt – future use
 * data/user_<username>_exams.txt – future use
 */
public class UserFileManager {

    private static final String DATA_DIR = "data";
    private static final String USERS_FILE = DATA_DIR + "/users.txt";

    // ============================================================
    // INITIALISATION
    // ============================================================

    /** Ensures the data/ directory exists. Called lazily before every file op. */
    private static void ensureDataDir() {
        File dir = new File(DATA_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    // ============================================================
    // USER MANAGEMENT
    // ============================================================

    /**
     * Checks whether a username already exists in users.txt.
     *
     * @param username the username to check
     * @return true if the username is already taken
     */
    public static boolean isUsernameTaken(String username) {
        ensureDataDir();
        File file = new File(USERS_FILE);
        if (!file.exists())
            return false;

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",", 2);
                if (parts.length >= 1 && parts[0].trim().equalsIgnoreCase(username.trim())) {
                    return true;
                }
            }
        } catch (IOException e) {
            System.err.println("Error reading users file: " + e.getMessage());
        }
        return false;
    }

    /**
     * Validates login credentials against users.txt.
     *
     * @param username the username to check
     * @param password the password to verify
     * @return true if the username/password pair matches
     */
    public static boolean validateLogin(String username, String password) {
        ensureDataDir();
        File file = new File(USERS_FILE);
        if (!file.exists())
            return false;

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",", 2);
                if (parts.length == 2
                        && parts[0].trim().equalsIgnoreCase(username.trim())
                        && parts[1].trim().equals(password.trim())) {
                    return true;
                }
            }
        } catch (IOException e) {
            System.err.println("Error reading users file: " + e.getMessage());
        }
        return false;
    }

    /**
     * Adds a new user to users.txt.
     *
     * @param username the new username
     * @param password the new password
     */
    public static void addUser(String username, String password) {
        ensureDataDir();
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(USERS_FILE, true))) {
            writer.write(username.trim() + "," + password.trim());
            writer.newLine();
        } catch (IOException e) {
            System.err.println("Error writing to users file: " + e.getMessage());
        }
    }

    /**
     * Returns a list of all registered usernames.
     * Used by the ExamServer to populate the participant selection list.
     */
    public static List<String> getAllUsernames() {
        ensureDataDir();
        List<String> usernames = new ArrayList<>();
        File file = new File(USERS_FILE);
        if (!file.exists()) return usernames;

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",", 2);
                if (parts.length >= 1 && !parts[0].trim().isEmpty()) {
                    usernames.add(parts[0].trim());
                }
            }
        } catch (IOException e) {
            System.err.println("Error reading users file: " + e.getMessage());
        }
        return usernames;
    }

    /**
     * Creates all user-specific data files for a new user.
     * Files are created empty if they don't already exist.
     *
     * @param username the username whose files should be created
     */
    public static void createUserFiles(String username) {
        ensureDataDir();
        String[] files = {
                todoFilePath(username),
                deadlineFilePath(username),
                courseFilePath(username),
                examFilePath(username)
        };
        for (String path : files) {
            File f = new File(path);
            if (!f.exists()) {
                try {
                    f.createNewFile();
                } catch (IOException e) {
                    System.err.println("Error creating file " + path + ": " + e.getMessage());
                }
            }
        }
    }

    // ============================================================
    // TODO OPERATIONS
    // ============================================================

    /**
     * Appends a single todo item to the user's todo file.
     * Format: text|completed
     *
     * @param username the user
     * @param todoItem the TodoItem to save
     */
    public static void saveTodo(String username, TodoItem todoItem) {
        ensureDataDir();
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(todoFilePath(username), true))) {
            writer.write(encodeTodo(todoItem));
            writer.newLine();
        } catch (IOException e) {
            System.err.println("Error saving todo: " + e.getMessage());
        }
    }

    /**
     * Overwrites the user's todo file with the provided list.
     * Use this after deletions or status changes.
     *
     * @param username the user
     * @param todos    the full current list of todos
     */
    public static void persistAllTodos(String username, List<TodoItem> todos) {
        ensureDataDir();
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(todoFilePath(username), false))) {
            for (TodoItem item : todos) {
                writer.write(encodeTodo(item));
                writer.newLine();
            }
        } catch (IOException e) {
            System.err.println("Error persisting todos: " + e.getMessage());
        }
    }

    /**
     * Loads all todos from the user's todo file.
     *
     * @param username the user
     * @return list of TodoItems (may be empty)
     */
    public static List<TodoItem> loadTodos(String username) {
        List<TodoItem> todos = new ArrayList<>();
        File file = new File(todoFilePath(username));
        if (!file.exists())
            return todos;

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty())
                    continue;
                TodoItem item = decodeTodo(line);
                if (item != null)
                    todos.add(item);
            }
        } catch (IOException e) {
            System.err.println("Error loading todos: " + e.getMessage());
        }
        return todos;
    }

    // ============================================================
    // DEADLINE OPERATIONS
    // ============================================================

    /**
     * Appends a single deadline to the user's deadline file.
     * Format: title|YYYY-MM-DD
     *
     * @param username the user
     * @param deadline the Deadline to save
     */
    public static void saveDeadline(String username, Deadline deadline) {
        ensureDataDir();
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(deadlineFilePath(username), true))) {
            writer.write(encodeDeadline(deadline));
            writer.newLine();
        } catch (IOException e) {
            System.err.println("Error saving deadline: " + e.getMessage());
        }
    }

    /**
     * Overwrites the user's deadline file with the provided list.
     *
     * @param username  the user
     * @param deadlines the full current list of deadlines
     */
    public static void persistAllDeadlines(String username, List<Deadline> deadlines) {
        ensureDataDir();
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(deadlineFilePath(username), false))) {
            for (Deadline d : deadlines) {
                writer.write(encodeDeadline(d));
                writer.newLine();
            }
        } catch (IOException e) {
            System.err.println("Error persisting deadlines: " + e.getMessage());
        }
    }

    /**
     * Loads all deadlines from the user's deadline file.
     *
     * @param username the user
     * @return list of Deadlines (may be empty)
     */
    public static List<Deadline> loadDeadlines(String username) {
        List<Deadline> deadlines = new ArrayList<>();
        File file = new File(deadlineFilePath(username));
        if (!file.exists())
            return deadlines;

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty())
                    continue;
                Deadline d = decodeDeadline(line);
                if (d != null)
                    deadlines.add(d);
            }
        } catch (IOException e) {
            System.err.println("Error loading deadlines: " + e.getMessage());
        }
        return deadlines;
    }

    // ============================================================
    // COURSE OPERATIONS
    // ============================================================

    /**
     * Overwrites the user's course file with all courses and their topics.
     * Format per line:
     * base64(courseName)|base64(courseCode)|credits|base64(topicName):0/1,base64(topicName):0/1
     */
    public static void persistAllCourses(String username, List<Course> courses) {
        ensureDataDir();
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(courseFilePath(username), false))) {
            for (Course course : courses) {
                writer.write(encodeCourse(course));
                writer.newLine();
            }
        } catch (IOException e) {
            System.err.println("Error persisting courses: " + e.getMessage());
        }
    }

    /**
     * Loads all courses (including topics) for a user.
     */
    public static List<Course> loadCourses(String username) {
        List<Course> courses = new ArrayList<>();
        File file = new File(courseFilePath(username));
        if (!file.exists()) return courses;

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                Course c = decodeCourse(line);
                if (c != null) courses.add(c);
            }
        } catch (IOException e) {
            System.err.println("Error loading courses: " + e.getMessage());
        }
        return courses;
    }

    // ============================================================
    // FUTURE STUBS / LEGACY
    // ============================================================

    /** Save a course entry for the user (future use). */
    public static void saveCourse(String username, String courseItem) {
        ensureDataDir();
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(courseFilePath(username), true))) {
            writer.write(courseItem.trim());
            writer.newLine();
        } catch (IOException e) {
            System.err.println("Error saving course: " + e.getMessage());
        }
    }

    /** Save an exam result for the user (future use). */
    public static void saveExamResult(String username, String examResult) {
        ensureDataDir();
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(examFilePath(username), true))) {
            writer.write(examResult.trim());
            writer.newLine();
        } catch (IOException e) {
            System.err.println("Error saving exam result: " + e.getMessage());
        }
    }

    // ============================================================
    // PRIVATE HELPERS – FILE PATHS
    // ============================================================

    private static String todoFilePath(String username) {
        return DATA_DIR + "/user_" + username + "_todo.txt";
    }

    private static String deadlineFilePath(String username) {
        return DATA_DIR + "/user_" + username + "_deadline.txt";
    }

    private static String courseFilePath(String username) {
        return DATA_DIR + "/user_" + username + "_courses.txt";
    }

    private static String examFilePath(String username) {
        return DATA_DIR + "/user_" + username + "_exams.txt";
    }

    // ============================================================
    // PRIVATE HELPERS – ENCODING / DECODING
    // ============================================================

    /** Encodes a TodoItem as "text|completed|dueDate" */
    private static String encodeTodo(TodoItem item) {
        String dueDate = item.hasDueDate() ? item.getDueDate().toString() : "";
        return item.getTitle() + "|" + item.isCompleted() + "|" + dueDate;
    }

    /** Decodes a line back into a TodoItem. Returns null on parse error. */
    private static TodoItem decodeTodo(String line) {
        try {
            String[] parts = line.split("\\|", -1);
            if (parts.length < 2)
                return null;
            String title = parts[0];
            boolean completed = Boolean.parseBoolean(parts[1]);
            LocalDate dueDate = (parts.length >= 3 && !parts[2].isEmpty())
                    ? LocalDate.parse(parts[2])
                    : null;

            TodoItem item = (dueDate != null) ? new TodoItem(title, dueDate) : new TodoItem(title);
            item.setCompleted(completed);
            return item;
        } catch (Exception e) {
            System.err.println("Error decoding todo line: " + line);
            return null;
        }
    }

    /** Encodes a Deadline as "title|YYYY-MM-DD" */
    private static String encodeDeadline(Deadline d) {
        return d.getTitle() + "|" + d.getDate().toString();
    }

    /** Decodes a line back into a Deadline. Returns null on parse error. */
    private static Deadline decodeDeadline(String line) {
        try {
            String[] parts = line.split("\\|", 2);
            if (parts.length < 2)
                return null;
            String title = parts[0];
            LocalDate date = LocalDate.parse(parts[1]);
            return new Deadline(title, date);
        } catch (Exception e) {
            System.err.println("Error decoding deadline line: " + line);
            return null;
        }
    }

    private static String encodeCourse(Course course) {
        StringBuilder topicParts = new StringBuilder();
        List<Topic> topics = course.getTopics();
        for (int i = 0; i < topics.size(); i++) {
            Topic t = topics.get(i);
            topicParts.append(encodeBase64(t.getName()))
                    .append(":")
                    .append(t.isCompleted() ? "1" : "0")
                    .append(":")
                    .append(encodeBase64(t.getComment()));
            if (i < topics.size() - 1) topicParts.append(",");
        }

        // Format: name|code|credits|topics|instructor|schedule|room|color|description
        return encodeBase64(course.getName()) + "|"
                + encodeBase64(course.getCode()) + "|"
                + course.getCredits() + "|"
                + topicParts + "|"
                + encodeBase64(course.getInstructor()) + "|"
                + encodeBase64(course.getSchedule()) + "|"
                + encodeBase64(course.getRoom()) + "|"
                + encodeBase64(course.getColor()) + "|"
                + encodeBase64(course.getDescription());
    }

    private static Course decodeCourse(String line) {
        try {
            String[] parts = line.split("\\|", -1);
            if (parts.length < 4) return null;

            String name    = decodeBase64(parts[0]);
            String code    = decodeBase64(parts[1]);
            double credits = Double.parseDouble(parts[2]);

            // Extended fields (backward-compatible: may not exist in old files)
            String instructor  = parts.length > 4 ? decodeBase64(parts[4]) : "";
            String schedule    = parts.length > 5 ? decodeBase64(parts[5]) : "";
            String room        = parts.length > 6 ? decodeBase64(parts[6]) : "";
            String color       = parts.length > 7 ? decodeBase64(parts[7]) : "#6366f1";
            String description = parts.length > 8 ? decodeBase64(parts[8]) : "";

            Course course = new Course(name, code, credits, instructor, schedule, room, color, description);

            List<Topic> topics = new ArrayList<>();
            if (!parts[3].isBlank()) {
                String[] topicEntries = parts[3].split(",");
                for (String entry : topicEntries) {
                    if (entry.isBlank()) continue;
                    String[] topicData = entry.split(":", 3);
                    if (topicData.length < 2) continue;
                    String topicName = decodeBase64(topicData[0]);
                    boolean completed = "1".equals(topicData[1]);
                    String comment = topicData.length > 2 ? decodeBase64(topicData[2]) : "";
                    topics.add(new Topic(topicName, completed, comment));
                }
            }
            course.setTopics(FXCollections.observableArrayList(topics));
            return course;
        } catch (Exception e) {
            System.err.println("Error decoding course line: " + line);
            return null;
        }
    }

    private static String encodeBase64(String value) {
        return Base64.getEncoder().encodeToString(
                (value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
    }

    private static String decodeBase64(String value) {
        return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
    }
}
