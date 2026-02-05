package server;

import java.io.*;
import java.net.*;
import java.util.*;

public class Player implements Runnable {

    Socket socket;
    int id;
    BufferedReader in;
    PrintWriter out;

    public boolean ready = false;
    public String name = "";

    public boolean[][] shots = new boolean[5][5];
    char[][] board = new char[5][5];

    List<Ship> ships = new ArrayList<>();
    int shipsPlaced = 0;

    public Player(Socket s, int id) throws IOException {
        this.socket = s;
        this.id = id;
        this.in = new BufferedReader(new InputStreamReader(s.getInputStream()));
        this.out = new PrintWriter(s.getOutputStream(), true);

        for (int i = 0; i < 5; i++)
            Arrays.fill(board[i], '~');
    }

    public void send(String msg) {
        out.println(msg);
    }

    @Override
    public void run() {
        try {
            String msg;
            while ((msg = in.readLine()) != null) {

                // JOIN
                if (msg.contains("JOIN")) {
                    this.name = extractString(msg, "playerName");
                }

                // PLACE SHIP
                if (msg.contains("PLACE_SHIP")) {

                    int x = extract(msg, "x");
                    int y = extract(msg, "y");
                    String orientation = extractString(msg, "orientation");

                    int[] shipSizes = {2, 2, 3};

                    if (shipsPlaced >= shipSizes.length) {
                        send(ServerMain.error("Hai già piazzato tutte le navi"));
                        send(ServerMain.turnChange(true));
                        continue;
                    }

                    boolean ok = placeShip(
                            x,
                            y,
                            orientation,
                            shipSizes[shipsPlaced]
                    );

                    if (!ok) {
                        send(ServerMain.errorDetails(
                                "Posizionamento non valido",
                                "Nave fuori griglia, sovrapposta o orientamento errato"
                        ));
                        send(ServerMain.turnChange(true));
                        continue;
                    }

                    if (shipsPlaced == shipSizes.length) {
                        ready = true;
                        ServerMain.tryStartGame();
                    }
                }

                // ATTACK
                if (msg.contains("ATTACK")) {
                    int x = extract(msg, "x");
                    int y = extract(msg, "y");
                    ServerMain.handleAttack(this.id, x, y);
                }
            }

        } catch (Exception ignored) {
        } finally {
            try { socket.close(); } catch (Exception ignored) {}
        }
    }

    // ===============================
    // LOGICA PIAZZAMENTO NAVI
    // ===============================
    public boolean placeShip(int x, int y, String orientation, int length) {

        // controllo cella iniziale
        if (!ServerMain.inBounds(x, y)) return false;
        if (board[x][y] != '~') return false;

        List<int[]> positions = new ArrayList<>();

        for (int i = 0; i < length; i++) {
            int nx = x;
            int ny = y;

            if ("H".equalsIgnoreCase(orientation)) {
                ny += i;
            } else if ("V".equalsIgnoreCase(orientation)) {
                nx += i;
            } else {
                return false;
            }

            if (!ServerMain.inBounds(nx, ny)) return false;
            if (board[nx][ny] != '~') return false;

            positions.add(new int[]{nx, ny});
        }

        // piazzamento effettivo
        for (int[] p : positions) {
            board[p[0]][p[1]] = 'S';
        }

        String shipName =
                (length == 2 && shipsPlaced == 0) ? "Destroyer-1" :
                        (length == 2 && shipsPlaced == 1) ? "Destroyer-2" :
                                "Submarine";

        ships.add(new Ship(shipName, positions));
        shipsPlaced++;

        return true;
    }

    // ===============================
    // ATTACCHI
    // ===============================
    public AttackOutcome receiveAttack(int x, int y) {
        for (Ship s : ships) {
            for (int i = 0; i < s.positions.size(); i++) {
                int[] p = s.positions.get(i);
                if (p[0] == x && p[1] == y) {
                    if (s.hits.contains(i))
                        return new AttackOutcome("MISS");

                    s.hits.add(i);
                    if (s.isSunk())
                        return new AttackOutcome("SUNK", s.name);

                    return new AttackOutcome("HIT");
                }
            }
        }
        return new AttackOutcome("MISS");
    }

    public boolean allShipsSunk() {
        for (Ship s : ships)
            if (!s.isSunk()) return false;
        return true;
    }

    // ===============================
    // PARSER JSON SEMPLICE
    // ===============================
    int extract(String msg, String key) {
        int i = msg.indexOf("\"" + key + "\":");
        int s = msg.indexOf(":", i) + 1;
        int e = msg.indexOf(",", s);
        if (e == -1) e = msg.indexOf("}", s);
        return Integer.parseInt(msg.substring(s, e).trim());
    }

    String extractString(String msg, String key) {
        int i = msg.indexOf("\"" + key + "\":");
        int s = msg.indexOf("\"", i + key.length() + 3) + 1;
        int e = msg.indexOf("\"", s);
        return msg.substring(s, e);
    }

    // ===============================
    // CLASSE SHIP
    // ===============================
    static class Ship {
        String name;
        List<int[]> positions;
        Set<Integer> hits = new HashSet<>();

        Ship(String name, List<int[]> pos) {
            this.name = name;
            this.positions = pos;
        }

        boolean isSunk() {
            return hits.size() == positions.size();
        }
    }
}
