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
    // Aggiornato a 8x8 per coincidere con la costante SIZE del Client e del ServerMain
    public boolean[][] shots = new boolean[8][8];

    List<Ship> ships = new ArrayList<>();

    public Player(Socket s, int id) throws IOException {
        this.socket = s;
        this.id = id;
        this.in = new BufferedReader(new InputStreamReader(s.getInputStream()));
        this.out = new PrintWriter(s.getOutputStream(), true);
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
                    // Pulizia lista navi per evitare duplicati in caso di reinvio
                    ships.clear();

                    List<int[]> coords = new ArrayList<>();
                    int idx = 0;
                    while (true) {
                        int ix = msg.indexOf("\"x\":", idx);
                        if (ix == -1) break;

                        // Utilizzo i metodi helper per un parsing più pulito
                        int x = extract(msg.substring(ix), "x");
                        int iy = msg.indexOf("\"y\":", ix);
                        int y = extract(msg.substring(iy), "y");

                        coords.add(new int[]{x, y});
                        idx = iy + 5; // Sposta l'indice avanti per cercare la coordinata successiva
                    }

                    // Assegnazione navi basata sulle dimensioni concordate
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
                    try {
                        int x = extract(msg, "x");
                        int y = extract(msg, "y");
                        ServerMain.handleAttack(this.id, x, y);
                    } catch (Exception e) {
                        // Invia errore al client se il formato ATTACK non è valido per evitare blocchi
                        send("{\"type\":\"ERROR\",\"payload\":{\"message\":\"Formato attacco non valido\"}}");
                    }
                }
            }
        } catch (Exception e) {
            // Gestione chiusura silenziosa della connessione
        } finally {
            try { socket.close(); } catch (Exception ex) {}
        }
    }

    public AttackOutcome receiveAttack(int x, int y) {
        for (Ship s : ships) {
            for (int i = 0; i < s.positions.size(); i++) {
                int[] p = s.positions.get(i);
                if (p[0] == x && p[1] == y) {
                    if (s.hits.contains(i)) {
                        return new AttackOutcome("MISS"); // Già colpita in precedenza
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
        if (ships.isEmpty()) return false;
        for (Ship s : ships) if (!s.isSunk()) return false;
        return true;
    }

    // Helper migliorati per estrarre valori dal finto JSON
    int extract(String msg, String key) {
        try {
            int i = msg.indexOf("\"" + key + "\":");
            int s = msg.indexOf(":", i) + 1;
            int e = msg.indexOf(",", s);
            int e2 = msg.indexOf("}", s);
            if (e == -1 || (e2 != -1 && e2 < e)) e = e2;
            return Integer.parseInt(msg.substring(s, e).trim());
        } catch (Exception e) {
            return -1; // Ritorna valore invalido in caso di errore di parsing
        }
    }

    String extractString(String msg, String key) {
        try {
            int i = msg.indexOf("\"" + key + "\":");
            int s = msg.indexOf("\"", i + key.length() + 3) + 1;
            int e = msg.indexOf("\"", s);
            return msg.substring(s, e);
        } catch (Exception e) {
            return "";
        }
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
