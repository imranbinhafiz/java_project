package com.example.javaproject;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.io.IOException;
import java.util.List;

/**
 * My Stats scene — wins/losses for Swap Duel, best score for Speed, and challenge history.
 */
public class ChallengeStatsController {

    @FXML private Label winsLabel;
    @FXML private Label lossesLabel;
    @FXML private Label scoreLabel;
    @FXML private Label totalLabel;
    @FXML private Label winRateLabel;
    @FXML private Label winsSmallLabel;
    @FXML private Label lossesSmallLabel;
    @FXML private VBox  historyBox;

    private ChallengeClient client;
    private String          username;
    private BorderPane      rootPane;

    public void init(ChallengeClient client, String username, BorderPane rootPane) {
        this.client   = client;
        this.username = username;
        this.rootPane = rootPane;

        client.setPushListener(msg -> {
            String cmd     = ExamJsonUtil.parseCommand(msg);
            String payload = ExamJsonUtil.parsePayload(msg);
            javafx.application.Platform.runLater(() -> {
                if ("CH_STATS".equals(cmd) || "CH_REGISTER_OK".equals(cmd)) loadStats(payload);
            });
        });
        client.getStats(username);
    }

    @FXML public void initialize() {}

    private void loadStats(String payload) {
        int wins   = ChallengeClient.parseInt(payload, "wins");
        int losses = ChallengeClient.parseInt(payload, "losses");
        int score  = ChallengeClient.parseInt(payload, "score");
        int total  = wins + losses;

        if (winsLabel   != null) winsLabel.setText(String.valueOf(wins));
        if (lossesLabel != null) lossesLabel.setText(String.valueOf(losses));
        if (scoreLabel  != null) scoreLabel.setText(String.valueOf(score));
        if (totalLabel  != null) totalLabel.setText(String.valueOf(total));
        if (winsSmallLabel   != null) winsSmallLabel.setText(wins + " W");
        if (lossesSmallLabel != null) lossesSmallLabel.setText(losses + " L");

        if (total > 0) {
            int pct = (int) Math.round((wins * 100.0) / total);
            if (winRateLabel != null) winRateLabel.setText(pct + " %");
        } else {
            if (winRateLabel != null) winRateLabel.setText("— %");
        }

        // Challenge history
        String historyJson = ChallengeClient.parseStr(payload, "challengeHistory");
        if (historyJson == null || historyJson.isBlank() || historyJson.equals("[]")) {
            historyJson = ExamJsonUtil.extractArray(payload, "challengeHistory") + "";
        }
        loadHistory(historyJson);
    }

    private void loadHistory(String historyJson) {
        if (historyBox == null) return;
        historyBox.getChildren().clear();

        List<String> entries = ExamJsonUtil.splitObjectArray(historyJson);
        if (entries.isEmpty()) {
            Label empty = new Label("No challenge history yet.");
            empty.getStyleClass().add("ch-meta-lbl");
            empty.setStyle("-fx-padding:8 0 0 4;");
            historyBox.getChildren().add(empty);
            return;
        }

        for (String entry : entries) {
            String mode     = ExamJsonUtil.parseString(entry, "mode");
            String opponent = ExamJsonUtil.parseString(entry, "opponent");
            String result   = ExamJsonUtil.parseString(entry, "result");
            String score    = ExamJsonUtil.parseString(entry, "score");
            String rank     = ExamJsonUtil.parseString(entry, "rank");
            String date     = ExamJsonUtil.parseString(entry, "date");

            HBox row = new HBox(12);
            row.getStyleClass().add("ch-history-row");
            row.setAlignment(Pos.CENTER_LEFT);

            Label modeLbl = new Label(mode.equals("SWAP_DUEL") ? "🔄 Swap Duel" : "⚡ Speed");
            modeLbl.setStyle("-fx-font-size:13px; -fx-font-weight:bold; -fx-text-fill:#818cf8; -fx-min-width:100;");

            Label detailLbl;
            if (mode.equals("SWAP_DUEL")) {
                String resultColor = "WIN".equals(result) ? "#86efac" : ("LOSS".equals(result) ? "#fca5a5" : "#94a3b8");
                detailLbl = new Label("vs " + opponent + "  ·  " + result + "  ·  " + score + " pts");
                detailLbl.setStyle("-fx-text-fill:" + resultColor + ";");
            } else {
                detailLbl = new Label("Rank #" + rank + "  ·  " + score + " pts");
                detailLbl.setStyle("-fx-text-fill:#94a3b8;");
            }
            HBox.setHgrow(detailLbl, Priority.ALWAYS);

            Label dateLbl = new Label(date);
            dateLbl.setStyle("-fx-font-size:11px; -fx-text-fill:#64748b;");

            row.getChildren().addAll(modeLbl, detailLbl, dateLbl);
            historyBox.getChildren().add(row);
        }
    }

    @FXML private void handleBack() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("fxml files/challenge_lobby.fxml"));
            Node node = loader.load();
            ChallengeLobbyController ctrl = loader.getController();
            ctrl.setRootPane(rootPane);
            if (rootPane != null) { AnimationUtil.contentTransition(rootPane.getCenter(), node, null); rootPane.setCenter(node); }
        } catch (IOException e) { e.printStackTrace(); }
    }
}
