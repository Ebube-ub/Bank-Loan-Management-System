package bank.model;

// One line of a credit score breakdown, e.g. "12 installments paid on time: +120".
public class ScoreFactor {

    private final String label;
    private final int points;

    public ScoreFactor(String label, int points) {
        this.label = label;
        this.points = points;
    }

    public String getLabel() {
        return label;
    }

    public int getPoints() {
        return points;
    }
}
