package server;

public class AttackOutcome {
    public String result; // "HIT", "MISS", "SUNK"
    public String shipName; // optional when SUNK

    public AttackOutcome(String result) {
        this.result = result;
        this.shipName = null;
    }

    public AttackOutcome(String result, String shipName) {
        this.result = result;
        this.shipName = shipName;
    }
}
