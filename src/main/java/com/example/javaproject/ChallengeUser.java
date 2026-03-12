package com.example.javaproject;

/**
 * Connected user record stored inside ExamServer for Challenge Mode.
 */
public class ChallengeUser {

    private String  username;
    private String  ip;
    private boolean online;
    private boolean inRoom;
    private boolean inGame;
    private int     wins;       // swap duel wins
    private int     losses;     // swap duel losses
    private int     score;      // best speed score
    private String  currentRoomId;
    // Challenge history (simple serializable summary)
    private String  challengeHistory = "[]"; // JSON array of history entries

    public ChallengeUser() {}

    public ChallengeUser(String username, String ip) {
        this.username = username;
        this.ip       = ip;
        this.online   = true;
    }

    public String  getUsername()        { return username; }
    public void    setUsername(String v){ username = v; }
    public String  getIp()              { return ip; }
    public void    setIp(String v)      { ip = v; }
    public boolean isOnline()           { return online; }
    public void    setOnline(boolean v) { online = v; }
    public boolean isInRoom()           { return inRoom; }
    public void    setInRoom(boolean v) { inRoom = v; }
    public boolean isInGame()           { return inGame; }
    public void    setInGame(boolean v) { inGame = v; }
    public int     getWins()            { return wins; }
    public void    setWins(int v)       { wins = v; }
    public int     getLosses()          { return losses; }
    public void    setLosses(int v)     { losses = v; }
    public int     getScore()           { return score; }
    public void    setScore(int v)      { score = v; }
    public String  getCurrentRoomId()   { return currentRoomId; }
    public void    setCurrentRoomId(String v) { currentRoomId = v; }
    public String  getChallengeHistory()      { return challengeHistory; }
    public void    setChallengeHistory(String v) { challengeHistory = (v != null ? v : "[]"); }
}
