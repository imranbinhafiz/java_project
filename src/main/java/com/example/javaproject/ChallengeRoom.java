package com.example.javaproject;

import java.util.ArrayList;
import java.util.List;

/**
 * Model representing a Challenge Mode room.
 * Modes: SWAP_DUEL (2 players, descriptive Q&A per round) and SPEED (multiplayer, first correct wins).
 */
public class ChallengeRoom {

    public enum GameMode {
        SWAP_DUEL("🔄 Swap Duel", 2),
        SPEED("⚡ Speed", 8);

        private final String displayName;
        private final int    maxPlayers;

        GameMode(String displayName, int maxPlayers) {
            this.displayName = displayName;
            this.maxPlayers  = maxPlayers;
        }

        public String getDisplayName() { return displayName; }
        public int    getMaxPlayers()  { return maxPlayers;  }
    }

    public enum RoomStatus { WAITING, PLAYING, FINISHED }
    public enum RoomType   { PUBLIC, PRIVATE }

    // ── Core fields ───────────────────────────────────────────────────
    private String     roomId;
    private String     host;
    private GameMode   mode;
    private int        maxPlayers;
    private List<String>  players;
    private List<Boolean> readyStatus;
    private RoomStatus status;
    private RoomType   type;
    private String     password;

    // ── Speed mode ────────────────────────────────────────────────────
    private String selectedExamId;
    private String selectedExamTitle;

    // ── Swap Duel mode ────────────────────────────────────────────────
    private int    totalRounds      = 3;    // host-configured
    private int    currentRound     = 0;    // 1-based when game starts
    private int    roundTimerSeconds = 120; // exam phase timer per round
    /** Round phase: QUESTION_CREATION, EXAM, EVALUATION, ROUND_INTRO, FINISHED */
    private String roundPhase       = "WAITING";

    public ChallengeRoom() {
        players     = new ArrayList<>();
        readyStatus = new ArrayList<>();
        status      = RoomStatus.WAITING;
        type        = RoomType.PUBLIC;
    }

    public ChallengeRoom(String roomId, String host, GameMode mode, int maxPlayers, RoomType type) {
        this();
        this.roomId     = roomId;
        this.host       = host;
        this.mode       = mode;
        this.maxPlayers = maxPlayers;
        this.type       = type;
        players.add(host);
        readyStatus.add(false);
    }

    // ── Getters & Setters ─────────────────────────────────────────────
    public String     getRoomId()   { return roomId; }
    public void       setRoomId(String v)  { roomId = v; }
    public String     getHost()     { return host; }
    public void       setHost(String v)    { host = v; }
    public GameMode   getMode()     { return mode; }
    public void       setMode(GameMode v)  { mode = v; }
    public int        getMaxPlayers()      { return maxPlayers; }
    public void       setMaxPlayers(int v) { maxPlayers = v; }
    public List<String>  getPlayers()      { return players; }
    public void       setPlayers(List<String> v) { players = v; }
    public List<Boolean> getReadyStatus()  { return readyStatus; }
    public void       setReadyStatus(List<Boolean> v) { readyStatus = v; }
    public RoomStatus getStatus()   { return status; }
    public void       setStatus(RoomStatus v) { status = v; }
    public RoomType   getType()     { return type; }
    public void       setType(RoomType v)  { type = v; }
    public String     getPassword() { return password; }
    public void       setPassword(String v) { password = v; }
    public String     getSelectedExamId()    { return selectedExamId; }
    public void       setSelectedExamId(String v)    { selectedExamId = v; }
    public String     getSelectedExamTitle() { return selectedExamTitle; }
    public void       setSelectedExamTitle(String v) { selectedExamTitle = v; }
    public int        getTotalRounds()  { return totalRounds; }
    public void       setTotalRounds(int v)  { totalRounds = v; }
    public int        getCurrentRound() { return currentRound; }
    public void       setCurrentRound(int v) { currentRound = v; }
    public int        getRoundTimerSeconds()  { return roundTimerSeconds; }
    public void       setRoundTimerSeconds(int v) { roundTimerSeconds = v; }
    public String     getRoundPhase()   { return roundPhase; }
    public void       setRoundPhase(String v) { roundPhase = v; }

    public int     getPlayerCount() { return players == null ? 0 : players.size(); }
    public boolean isFull()         { return players != null && players.size() >= maxPlayers; }

    public boolean isPlayerReady(String username) {
        int idx = players.indexOf(username);
        if (idx < 0 || idx >= readyStatus.size()) return false;
        return readyStatus.get(idx);
    }

    public boolean allReady() {
        if (readyStatus == null || readyStatus.isEmpty()) return false;
        for (boolean r : readyStatus) if (!r) return false;
        return true;
    }
}
