package server;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.regex.*;

public class Player implements Runnable {
    private static final int[] SHIP_SIZES = {2, 2, 3};
    private static final String[] SHIP_NAMES = {"Destroyer-1", "Destroyer-2", "Submarine"};
    private static final Pattern JSON_PATTERN = Pattern.compile("\"([^\"]+)\":\\s*([^,}\\s]+|\"[^\"]*\")");
    
    private final Socket socket;
    private final int id;
    private BufferedReader in;
    private PrintWriter out;
    private volatile boolean ready = false;
    private String name = "";
    private final boolean[][] shots = new boolean[5][5];
    private final List<Ship> ships = new ArrayList<>();
    private final Object lock = new Object();

    public Player(Socket s, int id) throws IOException {
        this.socket = s;
        this.id = id;
        this.in = new BufferedReader(new InputStreamReader(s.getInputStream()));
        this.out = new PrintWriter(s.getOutputStream(), true);
    }

    public void send(String msg) {
        synchronized (lock) {
            out.println(msg);
        }
    }

    @Override
    public void run() {
        try {
            String msg;
            while ((msg = in.readLine()) != null && !socket.isClosed()) {
                processMessage(msg);
            }
        } catch (IOException e) {
            System.err.println("Player " + id + " (" + name + ") disconnected: " + e.getMessage());
        } finally {
            cleanup();
        }
    }

    private void processMessage(String msg) {
        try {
            if (msg.contains("\"JOIN\"")) {
                handleJoin(msg);
            } else if (msg.contains("\"PLACE_SHIPS\"")) {
                handlePlaceShips(msg);
            } else if (msg.contains("\"ATTACK\"")) {
                handleAttack(msg);
            } else {
                System.err.println("Unknown message from player " + id + ": " + msg);
            }
        } catch (Exception e) {
            System.err.println("Error processing message from player " + id + ": " + e.getMessage());
            send("{\"error\":\"Invalid message format\"}");
        }
    }

    private void handleJoin(String msg) {
        Map<String, String> data = parseSimpleJson(msg);
        this.name = data.getOrDefault("playerName", "Player" + id);
        System.out.println("Player " + id + " joined as: " + name);
    }

    private void handlePlaceShips(String msg) {
        Map<String, String> data = parseSimpleJson(msg);
        
        // Parse ship positions
        List<int[]> coords = parseCoordinates(data.getOrDefault("positions", ""));
        
        if (coords.size() != 7) { // 2+2+3 = 7 total coordinates
            send("{\"error\":\"Invalid number of ship coordinates\"}");
            return;
        }

        // Create ships from coordinates
        ships.clear();
        int coordIndex = 0;
        
        for (int i = 0; i < SHIP_SIZES.length; i++) {
            int size = SHIP_SIZES[i];
            List<int[]> positions = new ArrayList<>();
            
            for (int j = 0; j < size && coordIndex < coords.size(); j++) {
                positions.add(coords.get(coordIndex++));
            }
            
            if (positions.size() != size) {
                send("{\"error\":\"Invalid ship placement\"}");
                ships.clear();
                return;
            }
            
            ships.add(new Ship(SHIP_NAMES[i], positions));
        }

        synchronized (this) {
            this.ready = true;
        }
        
        System.out.println("Player " + id + " (" + name + ") placed ships successfully");
        ServerMain.tryStartGame();
    }

    private void handleAttack(String msg) {
        Map<String, String> data = parseSimpleJson(msg);
        
        try {
            int x = Integer.parseInt(data.getOrDefault("x", "-1"));
            int y = Integer.parseInt(data.getOrDefault("y", "-1"));
            
            if (x < 0 || x >= 5 || y < 0 || y >= 5) {
                send("{\"error\":\"Invalid attack coordinates\"}");
                return;
            }
            
            synchronized (lock) {
                if (shots[x][y]) {
                    send("{\"error\":\"Already attacked this position\"}");
                    return;
                }
                shots[x][y] = true;
            }
            
            ServerMain.handleAttack(this.id, x, y);
            
        } catch (NumberFormatException e) {
            send("{\"error\":\"Invalid coordinate format\"}");
        }
    }

    public AttackOutcome receiveAttack(int x, int y) {
        // Validate coordinates
        if (x < 0 || x >= 5 || y < 0 || y >= 5) {
            return new AttackOutcome("INVALID");
        }

        for (Ship ship : ships) {
            AttackOutcome outcome = ship.checkHit(x, y);
            if (outcome != null) {
                return outcome;
            }
        }
        
        return new AttackOutcome("MISS");
    }

    public boolean allShipsSunk() {
        for (Ship ship : ships) {
            if (!ship.isSunk()) {
                return false;
            }
        }
        return true;
    }

    public synchronized boolean isReady() {
        return ready;
    }

    public String getName() {
        return name;
    }

    public int getId() {
        return id;
    }

    public boolean[][] getShots() {
        return shots;
    }

    public List<Ship> getShips() {
        return new ArrayList<>(ships);
    }

    private List<int[]> parseCoordinates(String positionsStr) {
        List<int[]> coords = new ArrayList<>();
        
        if (positionsStr == null || positionsStr.isEmpty()) {
            return coords;
        }

        // Remove brackets and split by coordinate pairs
        positionsStr = positionsStr.replaceAll("[\\[\\]{}]", "");
        String[] pairs = positionsStr.split(",\\s*");
        
        for (String pair : pairs) {
            String[] xy = pair.split(":");
            if (xy.length == 2) {
                try {
                    int x = Integer.parseInt(xy[0].trim());
                    int y = Integer.parseInt(xy[1].trim());
                    coords.add(new int[]{x, y});
                } catch (NumberFormatException e) {
                    // Skip invalid coordinate
                }
            }
        }
        
        return coords;
    }

    private Map<String, String> parseSimpleJson(String json) {
        Map<String, String> result = new HashMap<>();
        Matcher matcher = JSON_PATTERN.matcher(json);
        
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = matcher.group(2);
            
            // Remove surrounding quotes if present
            if (value.startsWith("\"") && value.endsWith("\"")) {
                value = value.substring(1, value.length() - 1);
            }
            
            result.put(key, value);
        }
        
        return result;
    }

    private void cleanup() {
        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) {
            System.err.println("Error cleaning up player " + id + ": " + e.getMessage());
        }
    }

    static class Ship {
        private final String name;
        private final List<int[]> positions;
        private final Set<Integer> hits;
        private final Object shipLock = new Object();

        Ship(String name, List<int[]> positions) {
            this.name = name;
            this.positions = new ArrayList<>(positions);
            this.hits = new HashSet<>();
        }

        private AttackOutcome checkHit(int x, int y) {
            synchronized (shipLock) {
                for (int i = 0; i < positions.size(); i++) {
                    int[] pos = positions.get(i);
                    if (pos[0] == x && pos[1] == y) {
                        if (hits.contains(i)) {
                            return new AttackOutcome("MISS"); // Already hit this spot
                        }
                        
                        hits.add(i);
                        
                        if (hits.size() == positions.size()) {
                            return new AttackOutcome("SUNK", name);
                        }
                        
                        return new AttackOutcome("HIT");
                    }
                }
            }
            return null;
        }

        boolean isSunk() {
            synchronized (shipLock) {
                return hits.size() >= positions.size();
            }
        }

        String getName() {
            return name;
        }

        List<int[]> getPositions() {
            return new ArrayList<>(positions);
        }
    }

    static class AttackOutcome {
        private final String result;
        private final String shipName;

        AttackOutcome(String result) {
            this(result, null);
        }

        AttackOutcome(String result, String shipName) {
            this.result = result;
            this.shipName = shipName;
        }

        public String getResult() {
            return result;
        }

        public String getShipName() {
            return shipName;
        }
    }
}
