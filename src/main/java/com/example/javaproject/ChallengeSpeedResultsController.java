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
 * Speed mode results scene.
 * Shows ranking with player names, scores.
 */
public class ChallengeSpeedResultsController {

    @FXML private VBox rankingBox;

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

        // Parse ranking: {players:[{username, score, rank}]}
        String arr = ExamJsonUtil.extractArray(resultPayload, "players");
        List<String> players = ExamJsonUtil.splitObjectArray(arr);
        buildRanking(players);

        client.setPushListener(msg -> {});
    }

    @FXML public void initialize() {}

    private void buildRanking(List<String> players) {
        if (rankingBox == null) return;
        rankingBox.getChildren().clear();

        String[] medals = {"🥇", "🥈", "🥉"};
        for (int i = 0; i < players.size(); i++) {
            String obj   = players.get(i);
            String uname = ExamJsonUtil.parseString(obj, "username");
            int    score = ExamJsonUtil.parseInt(obj, "score");
            int    rank  = ExamJsonUtil.parseInt(obj, "rank");
            if (rank == 0) rank = i + 1;

            HBox row = new HBox(14);
            row.getStyleClass().add(uname.equals(username) ? "ch-rank-row-me" : "ch-rank-row");
            row.setAlignment(Pos.CENTER_LEFT);

            String medal = rank <= 3 ? medals[rank - 1] : "#" + rank;
            Label rankLbl = new Label(medal);
            rankLbl.setStyle("-fx-font-size:22px; -fx-min-width:40;");

            Label nameLbl = new Label(uname + (uname.equals(username) ? "  (You)" : ""));
            nameLbl.getStyleClass().add("ch-player-name");
            HBox.setHgrow(nameLbl, Priority.ALWAYS);

            Label scoreLbl = new Label(score + " pts");
            scoreLbl.setStyle("-fx-font-size:16px; -fx-font-weight:bold; -fx-text-fill:#818cf8;");

            row.getChildren().addAll(rankLbl, nameLbl, scoreLbl);
            rankingBox.getChildren().add(row);
        }
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
