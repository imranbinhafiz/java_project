package com.example.javaproject;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.layout.BorderPane;

import java.io.IOException;

/**
 * Controller for the Exam Module Home Screen.
 *
 * ═══════════════════════════════════════════════════════════════
 * CHANGES FROM ORIGINAL:
 *   [ADDED] handleMyPublishedExams() – navigates to PublisherDashboard screen.
 *           The FXML must include a "My Published Exams" button wired to this handler.
 * ═══════════════════════════════════════════════════════════════
 */
public class ExamModuleController {

    private BorderPane rootPane;

    public void setRootPane(BorderPane rootPane) {
        this.rootPane = rootPane;
    }

    @FXML
    public void initialize() {}

    // ── NAVIGATION ────────────────────────────────────────────────────

    @FXML
    private void handlePublishExam(ActionEvent event) {
        loadCenter("fxml files/exam_publish.fxml", controller -> {
            if (controller instanceof PublishExamController pec) {
                pec.setRootPane(rootPane);
            }
        });
    }

    @FXML
    private void handleParticipateExam(ActionEvent event) {
        loadCenter("fxml files/exam_participate.fxml", controller -> {
            if (controller instanceof ParticipateExamController pec) {
                pec.setRootPane(rootPane);
            }
        });
    }

    /**
     * [ADDED] Opens the publisher dashboard showing all exams the logged-in
     * user has published, with participant counts and management actions.
     */
    @FXML
    private void handleMyPublishedExams(ActionEvent event) {
        loadCenter("fxml files/exam_publisher_dashboard.fxml", controller -> {
            if (controller instanceof PublisherDashboardController pdc) {
                pdc.setRootPane(rootPane);
            }
        });
    }

    // ── HELPERS ───────────────────────────────────────────────────────

    @FunctionalInterface
    interface ControllerConsumer {
        void accept(Object controller);
    }

    private void loadCenter(String fxmlPath, ControllerConsumer setup) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Node content = loader.load();
            setup.accept(loader.getController());
            AnimationUtil.contentTransition(rootPane.getCenter(), content, null);
            rootPane.setCenter(content);
        } catch (IOException e) {
            System.err.println("[ExamModule] Failed to load " + fxmlPath + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
}
