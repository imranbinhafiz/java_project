package com.example.javaproject;

import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javafx.stage.FileChooser;
import javafx.scene.control.ButtonBar;

public class CoursesController {

    @FXML private VBox contentArea;
    @FXML private Label courseCountBadge;
    @FXML private TextField courseNameField;
    @FXML private Button addCourseButton;
    @FXML private FlowPane coursesGrid;
    @FXML private ScrollPane coursesScrollPane;

    private javafx.util.Callback<Node, Void> contentLoader;
    private final ObservableList<Course> coursesList = CourseData.getCourses();
    private Runnable onSyllabusOpenCallback;

    @FXML
    public void initialize() {
        loadCoursesFromFile();
        updateCourseCountBadge();
        rebuildGrid();
    }

    @FXML
    private void handleAddCourse() {
        String name = courseNameField != null ? courseNameField.getText().trim() : "";
        if (name.isBlank()) {
            String ns = "-fx-border-color: rgba(148,163,184,0.3); -fx-border-width: 1.5px; -fx-border-radius: 10px;";
            AnimationUtil.errorPulse(courseNameField, ns);
            return;
        }

        String codeBase = name.replaceAll("\\s+", "").toUpperCase();
        String code = codeBase.substring(0, Math.min(6, codeBase.length()));
        Course c = new Course(name, code, 0);
        c.setColor("#6366f1");

        coursesList.add(c);
        persistCoursesToFile();
        updateCourseCountBadge();
        courseNameField.clear();
        rebuildGrid();
        AnimationUtil.pulse(addCourseButton, 1.06);
    }

    private void handleDeleteCourse(Course course) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete Course");
        confirm.setHeaderText("Delete \"" + course.getName() + "\"?");
        confirm.setContentText("All topics for this course will also be removed.");
        confirm.getDialogPane().getStylesheets().add(
            getClass().getResource("css/alert.css").toExternalForm());

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            coursesList.remove(course);
            persistCoursesToFile();
            updateCourseCountBadge();
            rebuildGrid();
        }
    }

    private void openSyllabus(Course course) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("fxml files/syllabus.fxml"));
            VBox syllabusContent = loader.load();

            SyllabusController ctrl = loader.getController();
            ctrl.setCourse(course);
            ctrl.setOnBackCallback(() -> {
                updateCourseCountBadge();
                rebuildGrid();
                if (onSyllabusOpenCallback != null) onSyllabusOpenCallback.run();
            });

            if (contentLoader != null) contentLoader.call(syllabusContent);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void rebuildGrid() {
        if (coursesGrid == null) return;
        coursesGrid.getChildren().clear();
        for (Course course : coursesList) {
            coursesGrid.getChildren().add(buildCourseCard(course));
        }
    }

    private VBox buildCourseCard(Course course) {
        VBox card = new VBox();
        card.getStyleClass().add("course-card");
        card.setPrefWidth(220);
        card.setMinWidth(200);
        card.setMaxWidth(250);
        card.setAlignment(Pos.TOP_CENTER);

        VBox body = new VBox(10);
        body.setAlignment(Pos.CENTER);
        body.setPadding(new Insets(18, 16, 14, 16));
        VBox.setVgrow(body, Priority.ALWAYS);

        Label nameLabel = new Label(course.getName());
        nameLabel.getStyleClass().add("card-course-name");
        nameLabel.setWrapText(true);
        nameLabel.setAlignment(Pos.CENTER);
        nameLabel.setMaxWidth(Double.MAX_VALUE);

        double progress = course.getProgress();
        ProgressBar pb = new ProgressBar(progress);
        pb.getStyleClass().add("card-progress-bar");
        pb.setMaxWidth(Double.MAX_VALUE);

        Label topicBadge = new Label(
            course.getCompletedTopicCount() + "/" + course.getTotalTopicCount() + " topics | "
                + (int) (progress * 100) + "%");
        topicBadge.getStyleClass().add("card-topic-badge");

        Label statusBadge = new Label(course.getStatus());
        statusBadge.getStyleClass().addAll("status-badge", statusBadgeClass(course.getStatus()));

        body.getChildren().addAll(nameLabel, pb, topicBadge, statusBadge);

        HBox actionRow = new HBox(8);
        actionRow.setAlignment(Pos.CENTER);
        actionRow.getStyleClass().add("card-action-row");
        actionRow.setPadding(new Insets(0, 14, 14, 14));
        actionRow.setVisible(false);
        actionRow.setManaged(false);

        Button openBtn = new Button("Open");
        openBtn.getStyleClass().addAll("card-btn", "card-btn-primary");
        openBtn.setOnAction(e -> openSyllabus(course));

        Button exportBtn = new Button("Export");
        exportBtn.getStyleClass().addAll("card-btn", "card-btn-primary");
        exportBtn.setTooltip(new Tooltip("Export course to JSON"));
        exportBtn.setOnAction(e -> handleExportCourse(course));

        Button delBtn = new Button("Delete");
        delBtn.getStyleClass().addAll("card-btn", "card-btn-danger");
        delBtn.setTooltip(new Tooltip("Delete course"));
        delBtn.setOnAction(e -> handleDeleteCourse(course));

        actionRow.getChildren().addAll(openBtn, exportBtn, delBtn);

        card.setOnMouseEntered(e -> {
            actionRow.setVisible(true);
            actionRow.setManaged(true);
        });
        card.setOnMouseExited(e -> {
            actionRow.setVisible(false);
            actionRow.setManaged(false);
        });

        card.getChildren().addAll(body, actionRow);
        return card;
    }

    private String statusBadgeClass(String status) {
        return switch (status) {
            case "Completed" -> "badge-completed";
            case "In Progress" -> "badge-in-progress";
            default -> "badge-not-started";
        };
    }

    private void updateCourseCountBadge() {
        if (courseCountBadge != null) {
            courseCountBadge.setText(coursesList.size() + " courses");
        }
    }

    private void loadCoursesFromFile() {
        String username = UserSession.getInstance().getUsername();
        if (username == null || username.isBlank()) return;
        coursesList.setAll(UserFileManager.loadCourses(username));
    }

    private void persistCoursesToFile() {
        String username = UserSession.getInstance().getUsername();
        if (username == null || username.isBlank()) return;
        UserFileManager.persistAllCourses(username, coursesList);
    }

    // --- Nested DTOs for import/export ---

    private static class TopicDto {
        String name;
        boolean completed;
        String comment;
        TopicDto() {}
        TopicDto(Topic t) {
            this.name = t.getName();
            this.completed = t.isCompleted();
            this.comment = t.getComment();
        }
    }

    private static class CourseDto {
        String name;
        String code;
        double credits;
        List<TopicDto> topics;
        String instructor;
        String schedule;
        String room;
        String color;
        String description;

        CourseDto() {}
        CourseDto(Course c) {
            this.name = c.getName();
            this.code = c.getCode();
            this.credits = c.getCredits();
            this.instructor = c.getInstructor();
            this.schedule = c.getSchedule();
            this.room = c.getRoom();
            this.color = c.getColor();
            this.description = c.getDescription();
            this.topics = new java.util.ArrayList<>();
            if (c.getTopics() != null) {
                for (Topic t : c.getTopics()) {
                    this.topics.add(new TopicDto(t));
                }
            }
        }

        Course toCourse() {
            Course c = new Course(name, code, credits, instructor, schedule, room, color, description);
            if (topics != null) {
                List<Topic> parsedTopics = new java.util.ArrayList<>();
                for (TopicDto td : topics) {
                    parsedTopics.add(new Topic(td.name, td.completed, td.comment));
                }
                c.setTopics(javafx.collections.FXCollections.observableArrayList(parsedTopics));
            }
            return c;
        }
    }

    @FXML
    private void handleImportCourse() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Import Course");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON Files", "*.json"));
        java.io.File file = fileChooser.showOpenDialog(null);

        if (file != null) {
            try {
                String json = Files.readString(file.toPath(), StandardCharsets.UTF_8);
                CourseDto dto = fromJson(json);
                if (dto != null) {
                    Course imported = dto.toCourse();
                    boolean exists = false;
                    for (int i = 0; i < coursesList.size(); i++) {
                        if (coursesList.get(i).getCode().equals(imported.getCode())) {
                            exists = true;
                            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
                            confirm.setTitle("Replace or Skip?");
                            confirm.setHeaderText("Course with code '" + imported.getCode() + "' already exists.");
                            confirm.setContentText("Do you want to replace it?");
                            
                            ButtonType replaceBtn = new ButtonType("Replace");
                            ButtonType skipBtn = new ButtonType("Skip", ButtonBar.ButtonData.CANCEL_CLOSE);
                            confirm.getButtonTypes().setAll(replaceBtn, skipBtn);
                            
                            confirm.getDialogPane().getStylesheets().add(
                                getClass().getResource("css/alert.css").toExternalForm());
                            
                            Optional<ButtonType> result = confirm.showAndWait();
                            if (result.isPresent() && result.get() == replaceBtn) {
                                coursesList.set(i, imported);
                                persistCoursesToFile();
                                rebuildGrid();
                            }
                            break;
                        }
                    }
                    if (!exists) {
                        coursesList.add(imported);
                        updateCourseCountBadge();
                        persistCoursesToFile();
                        rebuildGrid();
                    }
                }
            } catch (Exception ex) {
                ex.printStackTrace();
                Alert alert = new Alert(Alert.AlertType.ERROR, "Failed to import course.");
                alert.show();
            }
        }
    }

    private void handleExportCourse(Course course) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Course");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON Files", "*.json"));
        fileChooser.setInitialFileName(course.getName().replaceAll("\\s+", "_") + "_" + course.getCode() + ".json");
        java.io.File file = fileChooser.showSaveDialog(null);

        if (file != null) {
            try {
                String json = toJson(new CourseDto(course));
                Files.writeString(file.toPath(), json, StandardCharsets.UTF_8);
                
                Alert success = new Alert(Alert.AlertType.INFORMATION);
                success.setTitle("Export Successful");
                success.setHeaderText(null);
                success.setContentText("Course exported successfully.");
                success.getDialogPane().getStylesheets().add(
                        getClass().getResource("css/alert.css").toExternalForm());
                success.show();
            } catch (Exception ex) {
                ex.printStackTrace();
                Alert error = new Alert(Alert.AlertType.ERROR, "Failed to export course.");
                error.show();
            }
        }
    }

    private static String toJson(CourseDto dto) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"name\": ").append(jsonString(dto.name)).append(",\n");
        sb.append("  \"code\": ").append(jsonString(dto.code)).append(",\n");
        sb.append("  \"credits\": ").append(dto.credits).append(",\n");
        sb.append("  \"instructor\": ").append(jsonString(dto.instructor)).append(",\n");
        sb.append("  \"schedule\": ").append(jsonString(dto.schedule)).append(",\n");
        sb.append("  \"room\": ").append(jsonString(dto.room)).append(",\n");
        sb.append("  \"color\": ").append(jsonString(dto.color)).append(",\n");
        sb.append("  \"description\": ").append(jsonString(dto.description)).append(",\n");
        sb.append("  \"topics\": [");
        if (dto.topics != null && !dto.topics.isEmpty()) {
            sb.append('\n');
            for (int i = 0; i < dto.topics.size(); i++) {
                TopicDto t = dto.topics.get(i);
                sb.append("    {")
                    .append("\"name\": ").append(jsonString(t.name)).append(", ")
                    .append("\"completed\": ").append(t.completed).append(", ")
                    .append("\"comment\": ").append(jsonString(t.comment))
                    .append("}");
                if (i < dto.topics.size() - 1) sb.append(",");
                sb.append('\n');
            }
            sb.append("  ");
        }
        sb.append("]\n");
        sb.append("}\n");
        return sb.toString();
    }

    private static CourseDto fromJson(String json) {
        if (json == null || json.isBlank()) return null;
        CourseDto dto = new CourseDto();
        dto.name = extractString(json, "name");
        dto.code = extractString(json, "code");
        dto.credits = extractNumber(json, "credits");
        dto.instructor = extractString(json, "instructor");
        dto.schedule = extractString(json, "schedule");
        dto.room = extractString(json, "room");
        dto.color = extractString(json, "color");
        dto.description = extractString(json, "description");
        dto.topics = new java.util.ArrayList<>();

        String topicsArray = extractArray(json, "topics");
        if (topicsArray != null && !topicsArray.isBlank()) {
            for (String topicObj : splitObjects(topicsArray)) {
                TopicDto td = new TopicDto();
                td.name = extractString(topicObj, "name");
                td.completed = extractBoolean(topicObj, "completed");
                td.comment = extractString(topicObj, "comment");
                dto.topics.add(td);
            }
        }
        return dto;
    }

    private static String jsonString(String value) {
        String v = value == null ? "" : value;
        return "\"" + escapeJson(v) + "\"";
    }

    private static String escapeJson(String s) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\' -> out.append("\\\\");
                case '"' -> out.append("\\\"");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) out.append(String.format("\\" + "u%04x", (int) c));
                    else out.append(c);
                }
            }
        }
        return out.toString();
    }

    private static String unescapeJson(String s) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char n = s.charAt(++i);
                switch (n) {
                    case '"' -> out.append('"');
                    case '\\' -> out.append('\\');
                    case '/' -> out.append('/');
                    case 'b' -> out.append('\b');
                    case 'f' -> out.append('\f');
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case 'u' -> {
                        if (i + 4 < s.length()) {
                            String hex = s.substring(i + 1, i + 5);
                            out.append((char) Integer.parseInt(hex, 16));
                            i += 4;
                        }
                    }
                    default -> out.append(n);
                }
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static String extractString(String json, String key) {
        Matcher m = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"", Pattern.DOTALL)
            .matcher(json);
        return m.find() ? unescapeJson(m.group(1)) : "";
    }

    private static double extractNumber(String json, String key) {
        Matcher m = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)", Pattern.DOTALL)
            .matcher(json);
        if (!m.find()) return 0.0;
        try {
            return Double.parseDouble(m.group(1));
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private static boolean extractBoolean(String json, String key) {
        Matcher m = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(true|false)", Pattern.DOTALL)
            .matcher(json);
        return m.find() && Boolean.parseBoolean(m.group(1));
    }

    private static String extractArray(String json, String key) {
        Matcher m = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\\[(.*?)]", Pattern.DOTALL)
            .matcher(json);
        return m.find() ? m.group(1) : "";
    }

    private static List<String> splitObjects(String arrayContent) {
        List<String> items = new java.util.ArrayList<>();
        int depth = 0;
        int start = -1;
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < arrayContent.length(); i++) {
            char ch = arrayContent.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (ch == '\\') {
                    escaped = true;
                } else if (ch == '"') {
                    inString = false;
                }
                continue;
            }
            if (ch == '"') {
                inString = true;
            } else if (ch == '{') {
                if (depth == 0) start = i;
                depth++;
            } else if (ch == '}') {
                depth--;
                if (depth == 0 && start >= 0) {
                    items.add(arrayContent.substring(start, i + 1));
                    start = -1;
                }
            }
        }
        return items;
    }

    public void setContentLoader(javafx.util.Callback<Node, Void> loader) {
        this.contentLoader = loader;
    }

    public void setOnSyllabusOpenCallback(Runnable callback) {
        this.onSyllabusOpenCallback = callback;
    }
}
