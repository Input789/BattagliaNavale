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

    List<Ship> ships = new ArrayList<>();

    public Player(Socket s, int id) throws IOException {
        this.socket = s;
        this.id = id;
        this.in = new BufferedReader(new InputStreamReader(s.getInputStream()));
        this.out = new PrintWriter(s.getOutputStream(), true);
        // shots default false
    }

    public void send(String msg) {
        out.println(msg);
    }

    @Override
    public void run() {
        try {
            String msg;
            while ((msg = in.readLine()) != null) {
                if (msg.contains("JOIN")) {
                    this.name = extractString(msg, "playerName");
                }

                if (msg.contains("PLACE_SHIPS")) {
                    // parse positions in order and assign to ships sizes [2,2,3]
                    List<int[]> coords = new ArrayList<>();
                    int idx = 0;
                    while (true) {
                        int ix = msg.indexOf("\"x\":", idx);
                        if (ix == -1) break;
                        int s = msg.indexOf(":", ix) + 1;
                        int e = msg.indexOf("", s);
                        // simpler parse: read until comma or brace
                        int e2 = msg.indexOf(",", s);
                        int e3 = msg.indexOf("}", s);
                        if (e2 == -1 || (e3 != -1 && e3 < e2)) e2 = e3;
                        String xs = msg.substring(s, e2).trim();
                        int x = Integer.parseInt(xs);
                        // find y after this
                        int iy = msg.indexOf("\"y\":", e2);
                        int sy = msg.indexOf(":", iy) + 1;
                        int ey = msg.indexOf(",", sy);
                        int ey2 = msg.indexOf("}", sy);
                        if (ey == -1 || (ey2 != -1 && ey2 < ey)) ey = ey2;
                        String ys = msg.substring(sy, ey).trim();
                        int y = Integer.parseInt(ys);
                        coords.add(new int[]{x, y});
                        idx = ey + 1;
                    }

                    // expected sizes
                    int[] sizes = {2, 2, 3};
                    int p = 0;
                    for (int i = 0; i < sizes.length; i++) {
                        int size = sizes[i];
                        List<int[]> pos = new ArrayList<>();
                        for (int k = 0; k < size && p < coords.size(); k++) {
                            pos.add(coords.get(p++));
                        }
                        String nm = (i == 0) ? "Destroyer-1" : (i == 1) ? "Destroyer-2" : "Submarine";
                        ships.add(new Ship(nm, pos));
                    }

                    this.ready = true;
                    ServerMain.tryStartGame();
                }

                if (msg.contains("ATTACK")) {
                    int x = extract(msg, "x");
                    int y = extract(msg, "y");
                    ServerMain.handleAttack(this.id, x, y);
                }
            }
        } catch (Exception e) {
            // connection closed or error
        } finally {
            try { socket.close(); } catch (Exception ex) {}
        }
    }

    // called by server when this player is attacked
    public AttackOutcome receiveAttack(int x, int y) {
        for (Ship s : ships) {
            for (int i = 0; i < s.positions.size(); i++) {
                int[] p = s.positions.get(i);
                if (p[0] == x && p[1] == y) {
                    if (s.hits.contains(i)) {
                        return new AttackOutcome("MISS");
                    }
                    s.hits.add(i);
                    if (s.isSunk()) return new AttackOutcome("SUNK", s.name);
                    return new AttackOutcome("HIT");
                }
            }
        }
        return new AttackOutcome("MISS");
    }

    public boolean allShipsSunk() {
        for (Ship s : ships) if (!s.isSunk()) return false;
        return true;
    }

    // helpers to parse simple JSON-ish messages
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

    static class Ship {
        String name;
        List<int[]> positions;
        Set<Integer> hits = new HashSet<>();

        Ship(String name, List<int[]> pos) {
            this.name = name;
            this.positions = pos;
        }

        boolean isSunk() {
            return hits.size() >= positions.size();
        }
    }
}
