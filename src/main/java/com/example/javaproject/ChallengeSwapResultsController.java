package com.example.javaproject;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.io.IOException;
import java.util.List;

/**
 * Final Results scene for Swap Duel.
 * Shows winner, total marks per player, and per-round breakdown.
 */
public class ChallengeSwapResultsController {

    @FXML private Label  winnerLabel;
    @FXML private Label  winnerSubLabel;
    @FXML private Label  player1Label;
    @FXML private Label  player1ScoreLabel;
    @FXML private Label  player2Label;
    @FXML private Label  player2ScoreLabel;
    @FXML private VBox   roundBreakdownBox;

    private ChallengeClient client;
    private String          username;
    private String          roomId;
    private BorderPane      rootPane;

    public void init(ChallengeClient client, String username, String roomId,
                     String resultPayload, BorderPane rootPane) {
        this.client   = client;
        this.username = username;
        this.roomId   = roomId;
        this.rootPane = rootPane;

        // Parse result payload
        // Expected: winner, players array [{username, totalMarks, roundMarks:[]}]
        String winner = ChallengeClient.parseStr(resultPayload, "winner");
        List<String> playerObjs = ExamJsonUtil.splitObjectArray(ExamJsonUtil.extractArray(resultPayload, "players"));

        winnerLabel.setText(winner.isEmpty() ? "🤝 Draw!" : "🏆 " + winner + " Wins!");
        if (winner.equals(username)) {
            winnerLabel.setStyle("-fx-text-fill:#fcd34d; -fx-font-size:32px; -fx-font-weight:bold;");
            winnerSubLabel.setText("Congratulations! You won the duel.");
            winnerSubLabel.setStyle("-fx-text-fill:#86efac;");
        } else if (winner.isEmpty()) {
            winnerLabel.setStyle("-fx-text-fill:#94a3b8; -fx-font-size:28px; -fx-font-weight:bold;");
            winnerSubLabel.setText("It's a tie!");
            winnerSubLabel.setStyle("-fx-text-fill:#94a3b8;");
        } else {
            winnerLabel.setStyle("-fx-text-fill:#94a3b8; -fx-font-size:28px; -fx-font-weight:bold;");
            winnerSubLabel.setText("Better luck next time!");
            winnerSubLabel.setStyle("-fx-text-fill:#fca5a5;");
        }

        if (playerObjs.size() >= 1) {
            String p1name   = ExamJsonUtil.parseString(playerObjs.get(0), "username");
            int    p1total  = ExamJsonUtil.parseInt(playerObjs.get(0), "totalMarks");
            player1Label.setText(p1name);
            player1ScoreLabel.setText(p1total + " pts");
        }
        if (playerObjs.size() >= 2) {
            String p2name  = ExamJsonUtil.parseString(playerObjs.get(1), "username");
            int    p2total = ExamJsonUtil.parseInt(playerObjs.get(1), "totalMarks");
            player2Label.setText(p2name);
            player2ScoreLabel.setText(p2total + " pts");
        }

        buildRoundBreakdown(playerObjs);

        // Re-register push for lobby navigation
        client.setPushListener(msg -> {
            // nothing to handle here — just in case of room closed
        });
    }

    @FXML public void initialize() {}

    private void buildRoundBreakdown(List<String> playerObjs) {
        if (roundBreakdownBox == null) return;
        roundBreakdownBox.getChildren().clear();

        if (playerObjs.isEmpty()) return;
        String p1 = playerObjs.size() > 0 ? ExamJsonUtil.parseString(playerObjs.get(0), "username") : "";
        String p2 = playerObjs.size() > 1 ? ExamJsonUtil.parseString(playerObjs.get(1), "username") : "";

        // round marks: stored as roundMarks arrays in each player object
        String p1RoundsRaw = ExamJsonUtil.extractArray(playerObjs.size() > 0 ? playerObjs.get(0) : "", "roundMarks");
        String p2RoundsRaw = ExamJsonUtil.extractArray(playerObjs.size() > 1 ? playerObjs.get(1) : "", "roundMarks");

        List<String> p1Rounds = splitSimpleArray(p1RoundsRaw);
        List<String> p2Rounds = splitSimpleArray(p2RoundsRaw);

        int maxRounds = Math.max(p1Rounds.size(), p2Rounds.size());
        for (int i = 0; i < maxRounds; i++) {
            int m1 = i < p1Rounds.size() ? safeInt(p1Rounds.get(i)) : 0;
            int m2 = i < p2Rounds.size() ? safeInt(p2Rounds.get(i)) : 0;

            HBox row = new HBox(0);
            row.getStyleClass().add("ch-result-round-row");
            row.setAlignment(Pos.CENTER);

            Label roundLbl = new Label("Round " + (i+1));
            roundLbl.getStyleClass().add("ch-result-round-lbl");
            roundLbl.setPrefWidth(90);

            Label p1lbl = new Label(p1 + ": " + m1 + " pts");
            p1lbl.getStyleClass().add("ch-result-player-score");
            p1lbl.setStyle(m1 > m2 ? "-fx-text-fill:#86efac;" : "-fx-text-fill:#94a3b8;");
            p1lbl.setPrefWidth(160);

            Label p2lbl = new Label(p2 + ": " + m2 + " pts");
            p2lbl.getStyleClass().add("ch-result-player-score");
            p2lbl.setStyle(m2 > m1 ? "-fx-text-fill:#86efac;" : "-fx-text-fill:#94a3b8;");
            p2lbl.setPrefWidth(160);

            row.getChildren().addAll(roundLbl, p1lbl, p2lbl);
            roundBreakdownBox.getChildren().add(row);
        }
    }

    private List<String> splitSimpleArray(String raw) {
        List<String> result = new java.util.ArrayList<>();
        if (raw == null || raw.isBlank()) return result;
        for (String s : raw.split(",")) {
            s = s.trim();
            if (!s.isEmpty()) result.add(s);
        }
        return result;
    }

    private int safeInt(String s) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return 0; }
    }

    @FXML private void handleBackToLobby() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("fxml files/challenge_lobby.fxml"));
            Node node = loader.load();
            ChallengeLobbyController ctrl = loader.getController();
            ctrl.setRootPane(rootPane);
            if (rootPane != null) { AnimationUtil.contentTransition(rootPane.getCenter(), node, null); rootPane.setCenter(node); }
        } catch (IOException e) { e.printStackTrace(); }
    }
}
