package client;

import java.io.*;
import java.net.*;
import java.util.*;

/*
 CLIENT BATTAGLIA NAVALE DUO PLAYER
 - 2 giocatori reali
 - comunica con server
 - invia navi
 - invia attacchi
 - riceve risultati
 - grafica testuale
   - = sconosciuto
   X = colpito
   O = mancato
*/
public class ClientMain {

    static Socket socket;
    static BufferedReader in;
    static PrintWriter out;
    static Scanner sc = new Scanner(System.in);

    // griglia visibile al giocatore
    static char[][] grid = new char[5][5];

    // indica se è il nostro turno
    static boolean myTurn = false;

    public static void main(String[] args) {

        initGrid();

        try {
            // connessione al server
            socket = new Socket("localhost", 5000);
            System.out.println("Connesso al server!");

            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            // thread che ascolta il server
            new Thread(ClientMain::listenServer).start();

            // inserimento nome player
            System.out.print("Inserisci il tuo nome: ");
            String name = sc.nextLine();
            out.println("{\"type\":\"JOIN\",\"payload\":{\"playerName\":\"" + name + "\"}}");

            // invio navi
            sendShips();

            // loop di gioco
            while (!socket.isClosed()) {
                if (myTurn) {
                    printGrid();
                    System.out.print("\nInserisci coordinate X Y (0-4): ");
                    
                    try {

                        int x = sc.nextInt();
                        int y = sc.nextInt();

                        out.println(String.format("{\"type\":\"ATTACK\",\"payload\":{\"x\":%d,\"y\":%d}}", x, y)); // Edited: used String.format
                        myTurn = false;
                    
                    } catch (InputMismatchException e) {
                        System.out.println("Inserire solo numeri validi!");
                        sc.nextLine();
                    }
                }
                Thread.sleep(100);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static void listenServer() {
        try {
            String msg;
            while ((msg = in.readLine()) != null) {

                // start partita
                if (msg.contains("GAME_START")) {
                    myTurn = msg.contains("true");
                    System.out.println("\nPartita iniziata!");
                    if (myTurn)
                        System.out.println("È il tuo turno!");
                }

                // risultato attacco
                if (msg.contains("ATTACK_RESULT")) {
                    int x = extract(msg, "x");
                    int y = extract(msg, "y");
                    String result = extractString(msg, "result");

                    if (result.equals("HIT") || result.equals("SUNK")) {
                        grid[x][y] = 'X';
                        System.out.println("\nColpito! 💥");
                    } else {
                        grid[x][y] = 'O';
                        System.out.println("\nMancato 🌊");
                    }
                }

                // attacco ricevuto
                if (msg.contains("INCOMING_ATTACK")) {
                    int x = extract(msg, "x");
                    int y = extract(msg, "y");
                    String result = extractString(msg, "result");

                    System.out.println("\nAttacco nemico in " + x + "," + y + " → " + result);
                }

                // cambio turno
                if (msg.contains("TURN_CHANGE")) {
                    myTurn = msg.contains("true");
                    if (myTurn)
                        System.out.println("\nOra è il tuo turno!");
                }

                // fine partita
                if (msg.contains("GAME_OVER")) {
                    String winner = extractString(msg, "winner");
                    System.out.println("\nGAME OVER! Vincitore: " + winner);
                    System.exit(0);
                }

                // errori
                if (msg.contains("ERROR")) {
                    String err = extractString(msg, "message");
                    System.out.println("\nErrore: " + err);
                }
            }
        } catch (Exception e) {
            System.out.println("Connessione chiusa");
        }
    }

    static void sendShips() {
        /*
         Navi richieste dal server:
         - Destroyer-1 (2)
         - Destroyer-2 (2)
         - Submarine (3)
        */

        String json = "{\"type\":\"PLACE_SHIPS\",\"payload\":{\"ships\":[" +
                "{\"name\":\"Destroyer-1\",\"size\":2,\"positions\":[{\"x\":0,\"y\":0},{\"x\":0,\"y\":1}]} ," +
                "{\"name\":\"Destroyer-2\",\"size\":2,\"positions\":[{\"x\":2,\"y\":0},{\"x\":2,\"y\":1}]} ," +
                "{\"name\":\"Submarine\",\"size\":3,\"positions\":[{\"x\":4,\"y\":0},{\"x\":4,\"y\":1},{\"x\":4,\"y\":2}]}" +
                "]}}";

        out.println(json);
    }

    // inizializza griglia
    static void initGrid() {
        for (char[] row : grid) Arrays.fill(row, '-');
    }

    // stampa griglia
    static void printGrid() {
        System.out.println("\nGriglia:");
        for (int i = 0; i < 5; i++) {
            for (int j = 0; j < 5; j++) {
                System.out.print(grid[i][j] + " ");
            }
            System.out.println();
        }
    }

    // parsing int JSON
    static int extract(String msg, String key) {
        try {
            int i = msg.indexOf("\"" + key + "\":");
            int s = msg.indexOf(":", i) + 1;
            int e = msg.indexOf(",", s);
            if (e == -1) e = msg.indexOf("}", s);
            return Integer.parseInt(msg.substring(s, e).replace(" ", "").trim());
        } catch (Exception e) { return 0; }
    }

    // parsing string JSON
    static String extractString(String msg, String key) {
        try {
            int i = msg.indexOf("\"" + key + "\":");
            int s = msg.indexOf("\"", i + key.length() + 3) + 1;
            int e = msg.indexOf("\"", s);
            return msg.substring(s, e);
        } catch (Exception e) { return ""; }
    }
}
