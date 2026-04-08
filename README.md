<div align="center">

<h1>🎓 Smart Exam System</h1>

<p><em>A full-featured, LAN-based academic desktop application built entirely in Java 21 + JavaFX</em></p>

![Java](https://img.shields.io/badge/Java-21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![JavaFX](https://img.shields.io/badge/JavaFX-21-0078D7?style=for-the-badge&logo=java&logoColor=white)
![Gemini](https://img.shields.io/badge/Gemini_AI-Powered-4285F4?style=for-the-badge&logo=google&logoColor=white)
![Platform](https://img.shields.io/badge/Platform-LAN%20%2F%20Desktop-success?style=for-the-badge)
![License](https://img.shields.io/badge/License-Academic-blueviolet?style=for-the-badge)

<br/>

> Runs entirely on your **local network** — no cloud infrastructure required (except the optional AI feature).

</div>

---

## 📑 Table of Contents

- [What Is This Project?](#-what-is-this-project)
- [Features](#-features)
  - [Authentication & User Management](#-authentication--user-management)
  - [Personal Dashboard](#-personal-dashboard)
  - [Courses & Syllabus Viewer](#-courses--syllabus-viewer)
  - [Exam Module](#-exam-module--full-lifecycle-management)
  - [Challenge Mode](#️-challenge-mode--real-time-academic-duels)
  - [Real-Time Messenger](#-real-time-messenger)
  - [AI Study Assistant](#-ai-study-assistant-gemini-powered)
- [Technology Stack](#️-technology-stack)
- [Installation](#️-installation)
- [Quick Start](#-quick-start)
- [Troubleshooting](#-troubleshooting)

---

## 🌟 What Is This Project?

**Smart Exam System** is a full-featured, LAN-based academic desktop application built entirely in **Java 21 + JavaFX**. It runs on your local network with zero cloud dependency (except the optional AI feature), making it perfect for classrooms, study groups, or any environment where you want a self-contained, real-time academic platform.

---

## ✨ Features

### 🔐 Authentication & User Management

Getting started is effortless. Users sign up with a username and password, then log in by entering the server's IP address (by default `localhost`) — making multi-user sessions across a LAN instant and painless.

- **Sign Up / Login** with credential validation and animated transitions
- **Profile Management** — change your display name or password at any time from the dashboard
- **Persistent Sessions** — your data and results are saved across sessions
- **Server Discovery** — just type in the host machine's local IP and you're connected

> 💡 **Tip:** On a shared Wi-Fi, the host machine's IP (e.g., `192.168.1.105`) is all that students need to join the session.

---

### 🏠 Personal Dashboard

Your command center. The moment you log in, you land on a clean, maximized dashboard with a collapsible sidebar — giving you instant access to every module without clutter.

- **Collapsible Sidebar Navigation** — toggle to icon-only mode for more screen space
- **To-Do List** — add personal tasks, mark them complete, track pending count with a live badge, and bulk-clear finished items
- **Deadline Tracker** — pick a date with the built-in date picker, title your deadline, and get a scrollable list of everything coming up
- **Smooth Animations** — fade-ins, scale transitions, and slide effects make every interaction feel polished
- **Profile Dropdown** — quick access to account settings directly from the top bar

> The dashboard is designed so that you never feel overwhelmed. Everything is one click away.

---

### 📚 Courses & Syllabus Viewer

Stay organized with a structured overview of your academic content.

- **Course Browser** — view all available courses in a clean list layout
- **Syllabus Reader** — drill into any course to see its topics, with a live **topic count badge**
- **Topic Management** — add, remove, or clear syllabus topics on the fly
- **Back Navigation** — seamlessly return to the course list without losing state
- **Import & Export** — share your course structure with others and receive theirs in return

> Perfect for students who want to track what's been covered, or educators who want to share course structures.

---

### 📝 Exam Module — Full Lifecycle Management

The exam system supports the complete lifecycle: **Creation → Assignment → Taking → Grading → Results** — all in real time over the network.

#### For Publishers:
| Feature | Description |
|---|---|
| Multi-Question Exams | Write questions, define multiple-choice options, mark correct answers |
| Image Attachments | Upload visual content alongside any question for richer assessments |
| Targeted Assignment | Choose exactly who receives each exam — no one else can see it |
| Publisher Dashboard | Monitor all published exams, see who attempted them, review scores |
| Time Limits | Define per-exam countdown timers; exam auto-submits when time runs out |

#### For Participants (Students):
| Feature | Description |
|---|---|
| Exam Inbox | See only exams assigned to you, with clear status indicators |
| Timed Interface | Live countdown timer at the top — every second counts |
| Smart Navigation | Jump between questions using dot-based navigation; answered questions are visually marked |
| MCQ Toggle Buttons | Stylized option buttons give immediate visual feedback when selected |
| Auto-Submit | If time expires, current answers are submitted automatically |
| Instant Results | See your score, correct answers, and performance summary immediately |
| Result History | Revisit any past exam result at any time |

> 💡 **Tip:** Publishers can also browse question-by-question breakdowns to see where the class struggled most.

---

### ⚔️ Challenge Mode — Real-Time Academic Duels

Challenge Mode turns exam content into a live, real-time multiplayer experience — like a quiz game show running entirely on your local network.

#### Creating & Joining Rooms:
- **Create a Room** — choose your game mode, optionally link an existing exam, and get a shareable **Room ID**
- **Room Lobby** — see all connected players and their ready status in real time; the host controls when the game starts
- **Join Any Room** — enter a Room ID to jump into an existing session instantly
- **Browse Open Rooms** — a live room list shows all active challenge rooms on the network

#### 🚀 Speed Mode
The classic quiz race. All players face the **same question at the same time** — but **only the first correct answer earns the point**. This rewards both accuracy *and* speed.

- Live per-question **countdown timer** keeps the pressure high
- After each question, a **round summary** shows who answered correctly and who was fastest
- Final **leaderboard and stats screen** ranks all players by score

#### 🔄 Swap Duel Mode
The unique twist — players don't just answer questions, they **write questions for their opponents**.

1. **Question Writing Phase** — each player writes a question (with optional image) for their opponent within the time limit
2. **Answer Phase** — players receive their opponent's question and submit a written answer (also with optional image)
3. **Evaluation Phase** — players **rate each other's answers** with a score, acting as peer evaluators
4. **Round Results** — see both answers side by side with awarded scores
5. Repeat for as many rounds as configured

> This mode builds **critical thinking**, **writing skills**, and **peer learning** simultaneously.

---

### 💬 Real-Time Messenger

A fully functional instant messaging system built directly into the platform — no third-party app needed.

- **Direct Messages** — start a one-on-one conversation with any online user; messages arrive instantly
- **Group Chat Rooms** — create named rooms, invite multiple users, and have group discussions
- **Live Online/Offline Status** — green dot indicators show who's currently active
- **Animated Message Bubbles** — sent and received messages appear with smooth pop-in animations
- **Persistent Chat History** — DM and room history is saved so you can scroll back through past conversations
- **Clean Split-Panel Layout** — user list on the left, active chat on the right; simple and intuitive

> Whether you're coordinating a study group or asking a classmate a quick question during an exam break — the messenger has you covered.

---

### 🤖 AI Study Assistant (Gemini-Powered)

An intelligent, conversational AI tutor is built right into the app — powered by **Google Gemini**, one of the most capable AI models available.

- **Multi-Turn Conversations** — the AI remembers everything said earlier in your session, allowing natural follow-up questions
- **Markdown Rendering** — responses are beautifully formatted with headers, bullet points, bold text, numbered steps, and syntax-highlighted code blocks
- **Image Input** — attach an image (a textbook diagram, a handwritten note, a graph) and ask the AI to explain or analyze it
- **Suggested Prompts** — starter chips help you quickly ask common study questions
- **Typing Indicator** — an animated indicator shows when the AI is generating a response
- **Copy to Clipboard** — copy any AI response with a single click
- **Clear Chat** — reset the conversation and start fresh whenever you need

---

## 🛠️ Technology Stack

| Layer | Technology |
|---|---|
| **Language** | Java 21 |
| **UI Framework** | JavaFX 21 (FXML + CSS) |
| **Icons** | Ikonli + FontAwesome 5 |
| **AI Backend** | Google Gemini REST API |
| **Networking** | Java TCP Sockets (LAN) |

---

## ⚙️ Installation

1. Download the **`1-2 project.zip`** file from the [Releases](#) section
2. Extract the archive to your desired directory
3. Run **`java_project.exe`** to launch the application

> Make sure **Java 21** and **JavaFX 21** are installed and correctly configured on your machine before running.

---

## 🚀 Quick Start

Once installed, launch the application and you'll arrive at the **Login** screen.

```
Server (Host Machine):   Run the app — the server starts automatically on port 5000
Clients:                 Enter the host machine's local IP address on the login screen to connect
```

**First time?** Click **Sign Up** to create your account, then log back in.

---

## ❓ Troubleshooting

| Problem | Fix |
|---|---|
| Cannot connect to server | Ensure the host has launched the app first; verify the IP address and that both machines are on the same Wi-Fi / LAN network |
| Port 5000 already in use | Kill the conflicting process or restart the host machine |
| UI looks broken or blank | Ensure Java 21+ and JavaFX 21 are correctly installed and version-matched |

---

<div align="center">

Made with ❤️ using Java 21 + JavaFX

</div>
