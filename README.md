# ⚓ Java Battleship Duo-Player

A lightweight, **client-server** implementation of the classic Battleship game. This project features a central **Referee Server** that manages game logic and two **Network Clients** that communicate via a JSON-based protocol.

---

## 🚀 Features

* **Centralized Referee:** The server manages turns, validates coordinates, and checks win conditions.
* **Multi-threaded Architecture:** Handles concurrent player inputs and real-time game updates.
* **JSON Protocol:** Custom messaging system for actions like `JOIN`, `PLACE_SHIPS`, and `ATTACK`.
* **Visual Feedback:** Text-based grid system using:
    * `-` = Unknown/Water
    * `X` = Hit 💥
    * `O` = Miss 🌊

---

## 🛠️ Project Structure

| File | Role |
| :--- | :--- |
| **ServerMain.java** | The game coordinator. Manages the 5x5 grid logic and turn-swapping. |
| **ClientMain.java** | The player interface. Handles user input and displays the tactical grid. |
| **Player.java** | (Internal) Manages the socket connection and ship state for each user. |
| **AttackOutcome.java** | (Internal) Defines the results of a shot: `MISS`, `HIT`, or `SUNK`. |

---

## 🕹️ How to Play

### 1. Requirements
* Java JDK 8 or higher.
* Two terminal windows (one for each player) plus one for the server.

### 2. Execution
**Start the Server:**
```bash
javac server/ServerMain.java
java server.ServerMain
