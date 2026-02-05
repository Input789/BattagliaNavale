package client;

import java.io.*;
import java.net.*;
import java.util.*;

public class ClientMain {

    static final int SIZE = 8; 

    static Socket socket;
    static BufferedReader in;
    static PrintWriter out;
    static Scanner sc = new Scanner(System.in);

    static char[][] grid = new char[SIZE][SIZE];
    static boolean myTurn = false;

    public static void main(String[] args) {

        initGrid();

        try {
            socket = new Socket("localhost", 5000);
            System.out.println("\nConnesso al server!");

            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            new Thread(ClientMain::listenServer).start();

            System.out.print("Inserisci il tuo nome: ");
            String name = sc.nextLine();
            out.println("{\"type\":\"JOIN\",\"payload\":{\"playerName\":\"" + name + "\"}}");

            placeShipsManual();

            while (!socket.isClosed()) {
                if (myTurn) {
                    printGrid();

                    try {
                        System.out.print("\nInserisci coordinate ATTACCO X Y (1-"+SIZE+"): ");

                        if(!sc.hasNextInt()){
                            System.out.println("Input non valido! Usa numeri.");
                            sc.nextLine();
                            continue;
                        }
                        int x = sc.nextInt() - 1;

                        if(!sc.hasNextInt()){
                            System.out.println("Input non valido! Usa numeri.");
                            sc.nextLine();
                            continue;
                        }
                        int y = sc.nextInt() - 1;

                        sc.nextLine(); // pulizia buffer

                        if (!inBounds(x,y)){
                            System.out.println("Coordinate non valide! Usa numeri da 1 a " + SIZE);
                            continue;
                        }

                        out.println(String.format(
                            "{\"type\":\"ATTACK\",\"payload\":{\"x\":%d,\"y\":%d}}", x, y
                        ));

                        myTurn = false;

                    } catch (Exception e) {
                        System.out.println("Errore input!");
                        sc.nextLine(); // sicurezza
                    }
                }
                Thread.sleep(100);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static void initGrid() {
        for (char[] row : grid) Arrays.fill(row, '·');
    }

    static void printGrid() {
        System.out.println("\n     ");
        System.out.print("    ");
        for (int i = 1; i <= SIZE; i++) System.out.print(i + " ");
        System.out.println();

        for (int i = 0; i < SIZE; i++) {
            System.out.printf("%2d  ", i+1);
            for (int j = 0; j < SIZE; j++) {
                System.out.print(grid[i][j] + " ");
            }
            System.out.println();
        }
    }

    static void placeShipsManual() {

        String[] names = {"Destroyer-1", "Destroyer-2", "Submarine"};
        int[] sizes = {2, 2, 3};

        List<String> shipsJson = new ArrayList<>();
        Set<String> usedCoords = new HashSet<>(); // controllo duplicati

        System.out.println("\nPOSIZIONAMENTO NAVI");
        System.out.println("Formato: X Y  (da 1 a " + SIZE + ")");

        for (int i = 0; i < names.length; i++) {
            System.out.println("\nPosiziona " + names[i] + " (dimensione " + sizes[i] + ")");

            List<int[]> positions = new ArrayList<>();

            for (int k = 0; k < sizes[i]; k++) {
                while (true) {
                    try {
                        System.out.print("  Blocco " + (k+1) + ": ");
                        int x = sc.nextInt() - 1; // 1->0
                        int y = sc.nextInt() - 1;

                        if (!inBounds(x,y)) {
                            System.out.println("Coordinate fuori griglia!");
                            continue;
                        }

                        String key = x + "," + y;
                        if (usedCoords.contains(key)) {
                            System.out.println("Coordinate già usate! Inserisci altre coordinate.");
                            continue;
                        }

                        usedCoords.add(key);
                        positions.add(new int[]{x,y});
                        break;

                    } catch(Exception e) {
                        System.out.println("Input non valido!");
                        sc.nextLine();
                    }
                }
            }

            StringBuilder sb = new StringBuilder();
            sb.append("{\"name\":\"").append(names[i]).append("\",\"size\":").append(sizes[i]).append(",\"positions\":[");

            for (int p = 0; p < positions.size(); p++) {
                int[] c = positions.get(p);
                sb.append("{\"x\":").append(c[0]).append(",\"y\":").append(c[1]).append("}");
                if (p < positions.size()-1) sb.append(",");
            }
            sb.append("]}");

            shipsJson.add(sb.toString());
        }

        String finalJson = "{\"type\":\"PLACE_SHIPS\",\"payload\":{\"ships\":[" + String.join(",", shipsJson) + "]}}";

        out.println(finalJson);
        System.out.println("\nNavi posizionate correttamente!");
    }

    static void listenServer() {
        try {
            String msg;
            while ((msg = in.readLine()) != null) {

                if (msg.contains("GAME_START")) {
                    myTurn = msg.contains("true");
                    System.out.println("\nPartita iniziata!");
                    if (myTurn) System.out.println("--> Tocca a te!");
                }

                if (msg.contains("ATTACK_RESULT")) {
                    int x = extract(msg, "x");
                    int y = extract(msg, "y");
                    String result = extractString(msg, "result");

                    if (result.equals("HIT") || result.equals("SUNK")) {
                        grid[x][y] = 'X';
                        System.out.println("\nColpito!");
                    } else {
                        grid[x][y] = 'O';
                        System.out.println("\nMancato!");
                    }
                }

                if (msg.contains("TURN_CHANGE")) {
                    myTurn = msg.contains("true");
                    if (myTurn) System.out.println("\n--> È il tuo turno!");
                }

                if (msg.contains("GAME_OVER")) {
                    String winner = extractString(msg, "winner");
                    System.out.println("\nGAME OVER! Vince: " + winner);
                    System.exit(0);
                }
            }
        } catch (Exception e) {
            System.out.println("Connessione chiusa");
        }
    }

    static boolean inBounds(int x, int y){
        return x>=0 && x<SIZE && y>=0 && y<SIZE;
    }

    static int extract(String msg, String key) {
        try {
            int i = msg.indexOf("\"" + key + "\":");
            int s = msg.indexOf(":", i) + 1;
            int e = msg.indexOf(",", s);
            if (e == -1) e = msg.indexOf("}", s);
            return Integer.parseInt(msg.substring(s, e).replace(" ", "").trim());
        } catch (Exception e) { return 0; }
    }

    static String extractString(String msg, String key) {
        try {
            int i = msg.indexOf("\"" + key + "\":");
            int s = msg.indexOf("\"", i + key.length() + 3) + 1;
            int e = msg.indexOf("\"", s);
            return msg.substring(s, e);
        } catch (Exception e) { return ""; }
    }
}
