// Server = arbitro, 2 client reali

package server;

import java.io.*;
import java.net.*;

/*
 SERVER BATTAGLIA NAVALE DUO PLAYER
 - 2 client reali
 - server fa da arbitro
 - gestisce turni
 - valida mosse
 - gestisce navi
 - comunica con JSON
*/
public class ServerMain {

    // Porta di ascolto del server
    private static final int PORT = 5000;

    // Array dei due giocatori
    static Player[] players = new Player[2];

    // Indica di chi è il turno (0 o 1)
    static int currentTurn = 0;

    public static void main(String[] args) {

        System.out.println("Server avviato...");
        System.out.println("In attesa di 2 player...");

        try (ServerSocket ss = new ServerSocket(PORT)) {

            // loop infinito: server sempre attivo
            while (true) {
                Socket s = ss.accept(); // attende connessione client

                int slot = freeSlot(); // trova slot libero

                // se partita piena
                if (slot == -1) {
                    rejectConnection(s, "Partita già piena");
                    continue;
                }

                // crea player
                Player p = new Player(s, slot);
                players[slot] = p;

                // thread per player
                new Thread(p).start();

                System.out.println("Player connesso: " + (slot + 1));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void rejectConnection(Socket s, String message) throws IOException {
        PrintWriter out = new PrintWriter(s.getOutputStream(), true);
        out.println(error(message));
        s.close();
    }

    // trova slot libero
    static int freeSlot() {
        if (players[0] == null) return 0;
        if (players[1] == null) return 1;
        return -1;
    }

    // prova ad avviare la partita
    static synchronized void tryStartGame() {
        // se entrambi connessi e pronti
        if (players[0] != null && players[1] != null &&
            players[0].ready && players[1].ready) {

            currentTurn = 0; // parte player 1

            // invio start
            players[0].send(gameStart(true));
            players[1].send(gameStart(false));
        }
    }

    // gestione attacco
    static synchronized void handleAttack(int attackerId, int x, int y) {

        Player atk = players[attackerId];       // attaccante
        Player def = players[1 - attackerId];   // difensore

        // controllo turno
        if (currentTurn != attackerId) {
            atk.send(error("Non è il tuo turno"));
            return;
        }

        // controllo coordinate
        if (!inBounds(x, y)) {
            atk.send(errorDetails("Mossa non valida", "Coordinate fuori griglia"));
            return;
        }

        // controllo colpo già fatto
        if (atk.shots[x][y]) {
            atk.send(errorDetails("Mossa non valida", "Coordinate già utilizzate"));
            return;
        }

        // segna colpo
        atk.shots[x][y] = true;

        // ricezione attacco
        AttackOutcome out = def.receiveAttack(x, y);

        // invio risultato
        atk.send(attackResult(x, y, out));
        def.send(incomingAttack(x, y, out));

        // controllo vittoria
        if (def.allShipsSunk()) {
            String winner = atk.name.isBlank() ? "Player" + (attackerId + 1) : atk.name;
            String endMsg = gameOver(winner);
            atk.send(endMsg);
            def.send(endMsg);
            return;
        }

        // cambio turno
        currentTurn = 1 - currentTurn;
        players[0].send(turnChange(currentTurn == 0));
        players[1].send(turnChange(currentTurn == 1));
    }

    // controllo coordinate
    static boolean inBounds(int x, int y) {
        return x >= 0 && x < 5 && y >= 0 && y < 5;
    }

    static String gameStart(boolean yourTurn) {
        return "{\"type\":\"GAME_START\",\"payload\":{\"yourTurn\":" + yourTurn + "}}";
    }

    static String turnChange(boolean yourTurn) {
        return "{\"type\":\"TURN_CHANGE\",\"payload\":{\"yourTurn\":" + yourTurn + "}}";
    }

    static String gameOver(String winner) {
        return "{\"type\":\"GAME_OVER\",\"payload\":{\"winner\":\"" + winner + "\"}}";
    }

    static String error(String msg) {
        return "{\"type\":\"ERROR\",\"payload\":{\"message\":\"" + msg + "\"}}";
    }

    static String errorDetails(String msg, String details) {
        return "{\"type\":\"ERROR\",\"payload\":{\"message\":\"" + msg + "\",\"details\":\"" + details + "\"}}";
    }

    static String attackResult(int x, int y, AttackOutcome o) {
        if ("SUNK".equals(o.result)) {
            return "{\"type\":\"ATTACK_RESULT\",\"payload\":{\"x\":" + x + ",\"y\":" + y + ",\"result\":\"SUNK\",\"ship\":\"" + o.shipName + "\"}}";
        }
        return "{\"type\":\"ATTACK_RESULT\",\"payload\":{\"x\":" + x + ",\"y\":" + y + ",\"result\":\"" + o.result + "\"}}";
    }

    static String incomingAttack(int x, int y, AttackOutcome o) {
        return "{\"type\":\"INCOMING_ATTACK\",\"payload\":{\"x\":" + x + ",\"y\":" + y + ",\"result\":\"" + o.result + "\"}}";
    }
}


