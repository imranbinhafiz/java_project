package com.example.javaproject;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.io.IOException;

/**
 * Round Intro scene — shows "ROUND X" for a few seconds, then auto-navigates.
 */
public class ChallengeRoundIntroController {

    @FXML private Label roundLabel;
    @FXML private Label subLabel;
    @FXML private VBox  contentBox;

    private ChallengeClient client;
    private String          username;
    private String          roomId;
    private int             round;
    private int             totalRounds;
    private int             roundTimerSeconds;
    private BorderPane      rootPane;

    public void init(ChallengeClient client, String username, String roomId,
                     int round, int totalRounds, int roundTimerSeconds, BorderPane rootPane) {
        this.client           = client;
        this.username         = username;
        this.roomId           = roomId;
        this.round            = round;
        this.totalRounds      = totalRounds;
        this.roundTimerSeconds = roundTimerSeconds;
        this.rootPane         = rootPane;

        roundLabel.setText("ROUND " + round);
        subLabel.setText("of " + totalRounds + "  ·  Get ready!");

        // Animate in then auto-navigate after 2.5s
        AnimationUtil.fadeIn(contentBox, 600, null);

        PauseTransition pause = new PauseTransition(Duration.millis(2500));
        pause.setOnFinished(e -> Platform.runLater(this::goToQuestionCreation));
        pause.play();
    }

    @FXML public void initialize() {}

    private void goToQuestionCreation() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("fxml files/challenge_swap_question.fxml"));
            Node node = loader.load();
            ChallengeSwapQuestionController ctrl = loader.getController();
            ctrl.init(client, username, roomId, round, totalRounds, roundTimerSeconds, rootPane);
            if (rootPane != null) {
                AnimationUtil.contentTransition(rootPane.getCenter(), node, null);
                rootPane.setCenter(node);
            }
        } catch (IOException e) { e.printStackTrace(); }
    }
}
