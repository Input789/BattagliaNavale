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

    // Griglia avversario
    static char[][] grid = new char[SIZE][SIZE];
    // Griglia utente
    static char[][] myShipsGrid = new char[SIZE][SIZE];

    static boolean myTurn = false;

    public static void main(String[] args) {
        initGrids();

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
                    printDoubleGrids(); // Stampa entrambe le griglie affiancate
                    handleAttackInput();
                }
                Thread.sleep(100);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static void handleAttackInput() {
        try {
            System.out.print("\nInserisci coordinate ATTACCO X Y (1-" + SIZE + "): ");

            if (!sc.hasNextInt()) {
                System.out.println("Input non valido! Usa numeri.");
                sc.nextLine();
                return;
            }
            int x = sc.nextInt() - 1;

            if (!sc.hasNextInt()) {
                System.out.println("Input non valido! Usa numeri.");
                sc.nextLine();
                return;
            }
            int y = sc.nextInt() - 1;
            sc.nextLine();

            if (!inBounds(x, y)) {
                System.out.println("Coordinate fuori griglia! Riprova.");
                return;
            }

            out.println(String.format("{\"type\":\"ATTACK\",\"payload\":{\"x\":%d,\"y\":%d}}", x, y));
            myTurn = false;

        } catch (Exception e) {
            System.out.println("Errore nell'inserimento.");
            sc.nextLine();
        }
    }

    static void placeShipsManual() {
        String[] names = {"Destroyer-1", "Destroyer-2", "Submarine"};
        int[] sizes = {2, 2, 3};
        List<String> shipsJson = new ArrayList<>();
        Set<String> totalUsedCoords = new HashSet<>();

        System.out.println("\nPOSIZIONAMENTO NAVI (Le coordinate devono essere in linea e adiacenti)");

        for (int i = 0; i < names.length; i++) {
            List<int[]> shipPositions;
            boolean validPlacement = false;

            while (!validPlacement) {
                shipPositions = new ArrayList<>();
                System.out.println("\nPosiziona " + names[i] + " (dimensione " + sizes[i] + ")");

                for (int k = 0; k < sizes[i]; k++) {
                    System.out.print("  Blocco " + (k + 1) + " (X Y): ");
                    try {
                        int x = sc.nextInt() - 1;
                        int y = sc.nextInt() - 1;
                        sc.nextLine();

                        if (!inBounds(x, y) || totalUsedCoords.contains(x + "," + y)) {
                            System.out.println("Coordinata non valida o già occupata. Ricomincia la nave.");
                            shipPositions.clear();
                            break;
                        }
                        shipPositions.add(new int[]{x, y});
                    } catch (Exception e) {
                        System.out.println("Errore input. Ricomincia la nave.");
                        sc.nextLine();
                        shipPositions.clear();
                        break;
                    }
                }

                if (shipPositions.size() == sizes[i] && areAdjacent(shipPositions)) {
                    validPlacement = true;
                    for (int[] p : shipPositions) {
                        totalUsedCoords.add(p[0] + "," + p[1]);
                        myShipsGrid[p[0]][p[1]] = 'N'; // Segna la Tua Nave sulla tua griglia
                    }
                    shipsJson.add(buildShipJson(names[i], sizes[i], shipPositions));
                } else if (shipPositions.size() == sizes[i]) {
                    System.out.println("Errore: Le coordinate devono essere adiacenti e in linea!");
                }
            }
        }
        out.println("{\"type\":\"PLACE_SHIPS\",\"payload\":{\"ships\":[" + String.join(",", shipsJson) + "]}}");
        System.out.println("\nNavi inviate! In attesa dell'avversario...");
    }

    static void listenServer() {
        try {
            String msg;
            while ((msg = in.readLine()) != null) {
                if (msg.contains("GAME_START") || msg.contains("TURN_CHANGE")) {
                    myTurn = msg.contains("\"yourTurn\":true");
                    if (msg.contains("GAME_START")) System.out.println("\nPartita iniziata!");
                    if (myTurn) System.out.println("\n--> TOCCA A TE!");
                } else if (msg.contains("ATTACK_RESULT")) {
                    updateAttackGrid(msg);
                } else if (msg.contains("INCOMING_ATTACK")) {
                    updateMyGrid(msg);
                } else if (msg.contains("ERROR")) {
                    System.out.println("\nERRORE: " + extractString(msg, "message"));
                    myTurn = true;
                } else if (msg.contains("GAME_OVER")) {
                    System.out.println("\nGAME OVER! Vince: " + extractString(msg, "winner"));
                    System.exit(0);
                }
            }
        } catch (Exception e) {
            System.out.println("Connessione chiusa.");
        }
    }

    // Aggiorna la griglia
    static void updateAttackGrid(String msg) {
        int x = extract(msg, "x");
        int y = extract(msg, "y");
        String res = extractString(msg, "result");

        if (res.equals("HIT")) {
            grid[x][y] = 'X';
            System.out.println("\nColpito!");
        } else if (res.equals("SUNK")) {
            grid[x][y] = 'S';
            String shipName = extractString(msg, "ship");
            System.out.println("\nAFFONDATA! Hai distrutto: " + shipName);
        } else {
            grid[x][y] = 'O';
            System.out.println("\nMancato!");
        }
    }

    // Aggiorna la griglia utente quando ricevi un attacco
    static void updateMyGrid(String msg) {
        int x = extract(msg, "x");
        int y = extract(msg, "y");
        String res = extractString(msg, "result");

        if (res.equals("HIT") || res.equals("SUNK")) {
            myShipsGrid[x][y] = 'X'; // Nave colpita
            System.out.println("\n[DIFESA] Sei stato colpito in (" + (x+1) + "," + (y+1) + ")!");
        } else {
            myShipsGrid[x][y] = 'O'; // Acqua colpita
            System.out.println("\n[DIFESA] L'avversario ha mancato in (" + (x+1) + "," + (y+1) + ").");
        }
        // Se non è il tuo turno, stampa le griglie per vedere il danno
        if(!myTurn) printDoubleGrids();
    }

    static void initGrids() {
        for (int i = 0; i < SIZE; i++) {
            Arrays.fill(grid[i], '·');
            Arrays.fill(myShipsGrid[i], '·');
        }
    }

    static void printDoubleGrids() {
        System.out.println("\n      GRIGLIA ATTACCO                TUA GRIGLIA");
        System.out.print("    ");
        for (int i = 1; i <= SIZE; i++) System.out.print(i + " ");
        System.out.print("          ");
        for (int i = 1; i <= SIZE; i++) System.out.print(i + " ");
        System.out.println();

        for (int i = 0; i < SIZE; i++) {
            // Parte Sinistra (Attacco)
            System.out.printf("%2d  ", i + 1);
            for (int j = 0; j < SIZE; j++) System.out.print(grid[i][j] + " ");

            System.out.print("        "); // Spazio tra le tabelle

            // Parte Destra (Difesa)
            System.out.printf("%2d  ", i + 1);
            for (int j = 0; j < SIZE; j++) System.out.print(myShipsGrid[i][j] + " ");
            System.out.println();
        }
    }

    //Metodi aggiuntivi
    static boolean areAdjacent(List<int[]> pos) {
        if (pos.size() < 2) return true;
        boolean sameX = true, sameY = true;
        for (int i = 1; i < pos.size(); i++) {
            if (pos.get(i)[0] != pos.get(0)[0]) sameX = false;
            if (pos.get(i)[1] != pos.get(0)[1]) sameY = false;
        }
        if (!sameX && !sameY) return false;
        if (sameX) {
            pos.sort(Comparator.comparingInt(a -> a[1]));
            for (int i = 0; i < pos.size() - 1; i++) if (pos.get(i+1)[1] - pos.get(i)[1] != 1) return false;
        } else {
            pos.sort(Comparator.comparingInt(a -> a[0]));
            for (int i = 0; i < pos.size() - 1; i++) if (pos.get(i+1)[0] - pos.get(i)[0] != 1) return false;
        }
        return true;
    }

    static String buildShipJson(String name, int size, List<int[]> positions) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"name\":\"").append(name).append("\",\"size\":").append(size).append(",\"positions\":[");
        for (int p = 0; p < positions.size(); p++) {
            sb.append("{\"x\":").append(positions.get(p)[0]).append(",\"y\":").append(positions.get(p)[1]).append("}");
            if (p < positions.size() - 1) sb.append(",");
        }
        sb.append("]}");
        return sb.toString();
    }

    static boolean inBounds(int x, int y) { return x >= 0 && x < SIZE && y >= 0 && y < SIZE; }

    static int extract(String msg, String key) {
        try {
            int i = msg.indexOf("\"" + key + "\":");
            int s = msg.indexOf(":", i) + 1;
            int e = msg.indexOf(",", s);
            if (e == -1) e = msg.indexOf("}", s);
            return Integer.parseInt(msg.substring(s, e).trim());
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
