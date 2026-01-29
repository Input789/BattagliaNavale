package server;

import java.io.*;
import java.net.*;
import java.util.*;

/**
 * Rappresenta un giocatore sul server.
 * Gestisce la comunicazione socket, la griglia delle navi e lo stato di prontezza.
 */
public class Player implements Runnable {

    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;

    // ID del giocatore (0 o 1)
    public int id;
    public String name = "";
    
    // Griglia interna delle navi: true = nave presente
    public boolean[][] shipGrid = new boolean[5][5];
    
    // Registro dei colpi effettuati per evitare duplicati
    public boolean[][] shots = new boolean[5][5];
    
    // Stato del giocatore
    public boolean ready = false;

    public Player(Socket s, int id) {
        this.socket = s;
        this.id = id;
        try {
            this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            this.out = new PrintWriter(socket.getOutputStream(), true);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        try {
            String line;
            // Resta in ascolto dei messaggi JSON provenienti dal client
            while ((line = in.readLine()) != null) {
                
                // Gestione del JOIN (ricezione nome)
                if (line.contains("JOIN")) {
                    this.name = extractString(line, "playerName");
                }

                // Gestione del posizionamento navi
                if (line.contains("PLACE_SHIPS")) {
                    parseShips(line);
                    this.ready = true;
                    // Tenta di avviare la partita tramite il main server
                    ServerMain.tryStartGame();
                }

                // Gestione dell'attacco
                if (line.contains("ATTACK")) {
                    int x = extractInt(line, "x");
                    int y = extractInt(line, "y");
                    ServerMain.handleAttack(this.id, x, y);
                }
            }
        } catch (IOException e) {
            System.out.println("Player " + (id + 1) + " disconnesso.");
        } finally {
            closeConnection();
        }
    }

    /**
     * Invia un messaggio stringa (JSON) al client.
     */
    public void send(String msg) {
        if (out != null) {
            out.println(msg);
        }
    }

    /**
     * Riceve un attacco e restituisce l'esito (COLPITO, MANCATO, AFFONDATO).
     */
    public AttackOutcome receiveAttack(int x, int y) {
        AttackOutcome outcome = new AttackOutcome();
        
        if (shipGrid[x][y]) {
            // Segna il colpo sulla nave (false = parte distrutta)
            shipGrid[x][y] = false; 
            outcome.result = "HIT";
            
            // Logica semplificata: se dopo il colpo è affondata, potresti mappare il nome della nave
            // Qui manteniamo la compatibilità con il tuo protocollo
            if (allShipsSunk()) {
                outcome.result = "SUNK";
            }
        } else {
            outcome.result = "MISS";
        }
        
        return outcome;
    }

    /**
     * Controlla se tutte le navi sono state distrutte.
     */
    public boolean allShipsSunk() {
        for (int i = 0; i < 5; i++) {
            for (int j = 0; j < 5; j++) {
                if (shipGrid[i][j]) return false;
            }
        }
        return true;
    }

    /**
     * Estrae le navi dal JSON e popola la shipGrid.
     */
    private void parseShips(String json) {
        // Pulizia griglia prima del posizionamento
        for (int i = 0; i < 5; i++) Arrays.fill(shipGrid[i], false);

        // Parsing manuale semplificato per le coordinate x,y presenti nel JSON
        // Nota: Questo parser cerca tutte le occorrenze di x e y nel payload
        String[] parts = json.split("\\{");
        for (String part : parts) {
            if (part.contains("\"x\"") && part.contains("\"y\"")) {
                int x = extractInt("{" + part, "x");
                int y = extractInt("{" + part, "y");
                if (x >= 0 && x < 5 && y >= 0 && y < 5) {
                    shipGrid[x][y] = true;
                }
            }
        }
    }

    private void closeConnection() {
        try {
            if (socket != null) socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // --- Utility di Parsing JSON manuale (stessa logica del client) ---

    private int extractInt(String msg, String key) {
        try {
            int i = msg.indexOf("\"" + key + "\":");
            int s = msg.indexOf(":", i) + 1;
            int e = msg.indexOf(",", s);
            if (e == -1) e = msg.indexOf("}", s);
            if (e == -1) e = msg.indexOf("]", s);
            return Integer.parseInt(msg.substring(s, e).trim());
        } catch (Exception e) { return 0; }
    }

    private String extractString(String msg, String key) {
        try {
            int i = msg.indexOf("\"" + key + "\":");
            int s = msg.indexOf("\"", i + key.length() + 3) + 1;
            int e = msg.indexOf("\"", s);
            return msg.substring(s, e);
        } catch (Exception e) { return ""; }
    }
}
