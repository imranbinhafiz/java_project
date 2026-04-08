package com.example.javaproject;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.Collections;

/**
 * Controller for the Select Participants screen.
 * Shown after a publisher successfully creates an exam.
 * Allows direct multi-selection without any request/accept flow.
 */
public class SelectParticipantsController {

    @FXML private Label         examTitleLabel;
    @FXML private TextField     searchField;
    @FXML private RadioButton   publicRadio;
    @FXML private RadioButton   protectedRadio;
    @FXML private RadioButton   manualRadio;
    @FXML private PasswordField passwordField;
    @FXML private VBox          manualSelectionBox;
    @FXML private VBox          userListContainer;
    @FXML private VBox          groupListContainer;
    @FXML private Label         statusLabel;
    @FXML private Button        confirmButton;
    @FXML private Label         selectedCountLabel;

    private BorderPane rootPane;
    private Exam       exam;
    private ExamClient client;

    private final List<String>      allUsers    = new ArrayList<>();
    private final List<CheckBox>    userChecks  = new ArrayList<>();
    /** username -> online status from challenge server */
    private final Map<String, Boolean> onlineStatus = new HashMap<>();
    /** roomId -> roomName for messenger groups the publisher belongs to */
    private final Map<String, String>       groupNames   = new LinkedHashMap<>();
    /** roomId -> member list */
    private final Map<String, List<String>> groupMembers = new LinkedHashMap<>();
    private final List<CheckBox>            groupChecks  = new ArrayList<>();

    public void setRootPane(BorderPane rp) { this.rootPane = rp; }

    public void setExam(Exam exam, ExamClient client) {
        this.exam   = exam;
        this.client = client;
        examTitleLabel.setText("Select participants for: " + exam.getTitle());
        loadUsers();
    }

    @FXML
    public void initialize() {
        if (searchField != null) {
            searchField.textProperty().addListener((obs, o, nv) -> filterUsers(nv));
        }

        // Listen to visibility toggle changes
        publicRadio.getToggleGroup().selectedToggleProperty().addListener((obs, old, newVal) -> {
            boolean isProtected = (newVal == protectedRadio);
            passwordField.setVisible(isProtected);
            passwordField.setManaged(isProtected);

            boolean isManual = (newVal == manualRadio);
            manualSelectionBox.setVisible(isManual);
            manualSelectionBox.setManaged(isManual);
        });
    }

    // ──────────────────────────────────────────────────────────────────
    // LOAD USERS + GROUPS
    // ──────────────────────────────────────────────────────────────────

    private void loadUsers() {
        setStatus("Fetching user list...", false);
        // Fetch online status from challenge server in background
        String host = UserSession.getInstance().getServerHost();
        ChallengeClient challengeClient = new ChallengeClient(host, ExamServer.PORT);
        new Thread(() -> {
            try {
                challengeClient.connect();
                // Use a one-shot response listener
                final List<ChallengeUser> onlineUsers = new ArrayList<>();
                challengeClient.setPushListener(msg -> {
                    if ("CH_USERS_LIST".equals(ExamJsonUtil.parseCommand(msg))) {
                        onlineUsers.addAll(ChallengeClient.parseUsers(ExamJsonUtil.parsePayload(msg)));
                    }
                });
                challengeClient.startListening();
                challengeClient.getUsers();
                // small wait for response
                Thread.sleep(600);
                for (ChallengeUser u : onlineUsers)
                    onlineStatus.put(u.getUsername(), u.isOnline());
            } catch (Exception ignored) {
                // challenge server might not be available — degrade gracefully
            } finally {
                try { challengeClient.disconnect(); } catch (Exception ignored) {}
            }

            // Load messenger groups via a separate MessengerClient connection
            try {
                MessengerClient msgClient = new MessengerClient(host, ExamServer.PORT);
                msgClient.connect();
                String myUser = UserSession.getInstance().getUsername();
                final Map<String, String>       fetchedNames   = new LinkedHashMap<>();
                final Map<String, List<String>> fetchedMembers = new LinkedHashMap<>();
                msgClient.setPushListener(raw -> {
                    String cmd     = ExamJsonUtil.parseCommand(raw);
                    String payload = ExamJsonUtil.parsePayload(raw);
                    if ("CHAT_ROOMS_LIST".equals(cmd)) {
                        String arr = ExamJsonUtil.extractArray(payload, "rooms");
                        for (String obj : ExamJsonUtil.splitObjectArray(arr)) {
                            String roomId   = ExamJsonUtil.parseString(obj, "roomId");
                            String roomName = ExamJsonUtil.parseString(obj, "roomName");
                            String members  = ExamJsonUtil.extractArray(obj, "members");
                            List<String> memberList = ExamJsonUtil.parseStringArray(members);
                            if (!roomId.isBlank() && memberList.contains(myUser)) {
                                fetchedNames.put(roomId, roomName);
                                fetchedMembers.put(roomId, memberList);
                            }
                        }
                    }
                });
                msgClient.startListening();
                msgClient.register(myUser);
                msgClient.getRoomList();
                Thread.sleep(700);
                Platform.runLater(() -> {
                    groupNames.putAll(fetchedNames);
                    groupMembers.putAll(fetchedMembers);
                    renderGroupList();
                });
                msgClient.disconnect();
            } catch (Exception ignored) {
                // messenger groups not critical — degrade gracefully
            }

            try {
                List<String> users = client.getAllUsers();
                String publisher = UserSession.getInstance().getUsername();
                users.remove(publisher);
                Platform.runLater(() -> {
                    allUsers.addAll(users);
                    renderUserList(users);
                    setStatus("", false);
                });
            } catch (ExamClient.ExamClientException e) {
                Platform.runLater(() -> setStatus("❌ " + e.getMessage(), true));
            }
        }, "load-users-thread").start();
    }

    // ──────────────────────────────────────────────────────────────────
    // RENDER GROUP LIST
    // ──────────────────────────────────────────────────────────────────

    private void renderGroupList() {
        if (groupListContainer == null) return;
        groupListContainer.getChildren().clear();
        groupChecks.clear();

        if (groupNames.isEmpty()) {
            Label empty = new Label("No messenger groups found.");
            empty.setStyle("-fx-text-fill: #64748b; -fx-font-size: 13px;");
            groupListContainer.getChildren().add(empty);
            return;
        }

        for (Map.Entry<String, String> entry : groupNames.entrySet()) {
            String roomId   = entry.getKey();
            String roomName = entry.getValue();
            List<String> members = groupMembers.getOrDefault(roomId, Collections.emptyList());

            CheckBox cb = new CheckBox();
            cb.getStyleClass().add("exam-participant-check");
            cb.selectedProperty().addListener((obs, o, nv) -> {
                // When a group is checked/unchecked, toggle its members in the user list
                applyGroupMembersToUserList(members, nv);
                updateSelectedCount();
            });
            groupChecks.add(cb);

            // Group icon label
            Label icon = new Label("#");
            icon.setStyle("-fx-font-size:15px; -fx-font-weight:bold; -fx-text-fill:#818cf8;"
                + " -fx-background-color:rgba(99,102,241,0.15); -fx-background-radius:8;"
                + " -fx-padding:2 7 2 7;");

            VBox nameBox = new VBox(2);
            Label nameLbl = new Label(roomName);
            nameLbl.setStyle("-fx-font-size:14px; -fx-font-weight:bold; -fx-text-fill:#e2e8f0;");
            Label memberCount = new Label(members.size() + " members");
            memberCount.setStyle("-fx-font-size:11px; -fx-text-fill:#64748b;");
            nameBox.getChildren().addAll(nameLbl, memberCount);

            javafx.scene.layout.Pane spacer = new javafx.scene.layout.Pane();
            HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);

            HBox row = new HBox(10, cb, icon, nameBox, spacer);
            row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            row.getStyleClass().add("exam-participant-row");
            row.setStyle(row.getStyle() + "; -fx-border-color: rgba(99,102,241,0.2);"
                + " -fx-border-radius:8; -fx-background-radius:8;");
            groupListContainer.getChildren().add(row);
        }
    }

    /**
     * When a group checkbox changes, select/deselect all user checkboxes
     * whose username appears in the group's member list.
     */
    private void applyGroupMembersToUserList(List<String> members, boolean select) {
        String publisher = UserSession.getInstance().getUsername();
        for (CheckBox cb : userChecks) {
            String username = cb.getText();
            if (!username.equals(publisher) && members.contains(username)) {
                cb.setSelected(select);
            }
        }
    }

    private void renderUserList(List<String> users) {
        userListContainer.getChildren().clear();
        userChecks.clear();

        if (users.isEmpty()) {
            Label empty = new Label("No other users registered.");
            empty.setStyle("-fx-text-fill: #64748b; -fx-font-size: 14px;");
            userListContainer.getChildren().add(empty);
            return;
        }

        for (String u : users) {
            CheckBox cb = new CheckBox(u);
            cb.getStyleClass().add("exam-participant-check");
            cb.selectedProperty().addListener((obs, o, nv) -> updateSelectedCount());
            userChecks.add(cb);

            // Online status dot
            javafx.scene.layout.Pane dot = new javafx.scene.layout.Pane();
            boolean isOnline = onlineStatus.getOrDefault(u, false);
            dot.setStyle("-fx-background-color: " + (isOnline ? "#22c55e" : "#ef4444")
                    + "; -fx-background-radius: 50%; -fx-min-width:9; -fx-min-height:9;"
                    + " -fx-max-width:9; -fx-max-height:9;");

            Label statusLbl = new Label(isOnline ? "Online" : "Offline");
            statusLbl.setStyle("-fx-font-size:11px; -fx-text-fill:"
                    + (isOnline ? "#34d399" : "#f87171") + "; -fx-font-weight:bold;");

            javafx.scene.layout.Pane spacer = new javafx.scene.layout.Pane();
            HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);

            HBox row = new HBox(10, dot, cb, spacer, statusLbl);
            row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            row.getStyleClass().add("exam-participant-row");
            userListContainer.getChildren().add(row);
        }
        updateSelectedCount();
    }

    private void filterUsers(String query) {
        userListContainer.getChildren().clear();
        String lower = query.toLowerCase();
        for (int i = 0; i < userChecks.size(); i++) {
            CheckBox cb = userChecks.get(i);
            if (cb.getText().toLowerCase().contains(lower)) {
                // Rebuild the full row with status dot
                String u = cb.getText();
                boolean isOnline = onlineStatus.getOrDefault(u, false);
                javafx.scene.layout.Pane dot = new javafx.scene.layout.Pane();
                dot.setStyle("-fx-background-color: " + (isOnline ? "#22c55e" : "#ef4444")
                        + "; -fx-background-radius: 50%; -fx-min-width:9; -fx-min-height:9;"
                        + " -fx-max-width:9; -fx-max-height:9;");
                Label statusLbl = new Label(isOnline ? "Online" : "Offline");
                statusLbl.setStyle("-fx-font-size:11px; -fx-text-fill:"
                        + (isOnline ? "#34d399" : "#f87171") + "; -fx-font-weight:bold;");
                javafx.scene.layout.Pane spacer = new javafx.scene.layout.Pane();
                HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);
                HBox row = new HBox(10, dot, cb, spacer, statusLbl);
                row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                row.getStyleClass().add("exam-participant-row");
                userListContainer.getChildren().add(row);
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────
    // SELECT ALL
    // ──────────────────────────────────────────────────────────────────

    @FXML
    private void handleSelectAll(ActionEvent event) {
        boolean anyUnchecked = userChecks.stream().anyMatch(cb -> !cb.isSelected());
        userChecks.forEach(cb -> cb.setSelected(anyUnchecked));
    }

    // ──────────────────────────────────────────────────────────────────
    // CONFIRM
    // ──────────────────────────────────────────────────────────────────

    @FXML
    private void handleConfirm(ActionEvent event) {
        List<String> selected = userChecks.stream()
                .filter(CheckBox::isSelected)
                .map(CheckBox::getText)
                .collect(Collectors.toList());

        boolean isPublic = publicRadio.isSelected();
        boolean isProtected = protectedRadio.isSelected();
        String password = passwordField.getText();

        List<String> usersToAssign = manualRadio.isSelected() ? selected : new ArrayList<>(allUsers);

        if (manualRadio.isSelected() && usersToAssign.isEmpty()) {
            setStatus("❌ Select at least one participant.", true);
            return;
        }

        if (isProtected && password.isEmpty()) {
            setStatus("❌ Enter a password for the protected exam.", true);
            return;
        }

        confirmButton.setDisable(true);
        setStatus("Assigning participants...", false);

        new Thread(() -> {
            try {
                client.assignExam(exam.getExamId(), usersToAssign, isPublic, isProtected, password);
                Platform.runLater(() -> {
                    setStatus("✅ Exam assigned successfully.", false);
                    confirmButton.setDisable(false);
                    goToModuleHome();
                });
            } catch (ExamClient.ExamClientException e) {
                Platform.runLater(() -> {
                    setStatus("❌ " + e.getMessage(), true);
                    confirmButton.setDisable(false);
                });
            }
        }, "assign-thread").start();
    }

    // ──────────────────────────────────────────────────────────────────
    // HELPERS
    // ──────────────────────────────────────────────────────────────────

    private void updateSelectedCount() {
        long count = userChecks.stream().filter(CheckBox::isSelected).count();
        if (selectedCountLabel != null)
            selectedCountLabel.setText(count + " selected");
    }

    private void setStatus(String msg, boolean isError) {
        if (statusLabel == null) return;
        statusLabel.setText(msg);
        statusLabel.setStyle(isError ? "-fx-text-fill: #fca5a5;" : "-fx-text-fill: #86efac;");
    }

    private void goToModuleHome() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("fxml files/exam_module.fxml"));
            Node content = loader.load();
            ExamModuleController ctrl = loader.getController();
            ctrl.setRootPane(rootPane);
            AnimationUtil.contentTransition(rootPane.getCenter(), content, null);
            rootPane.setCenter(content);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void handleBack(ActionEvent event) { goToModuleHome(); }
}



