package bank.model;

import java.util.List;

// The result of scoring a customer: the number, the band it falls into,
// and the lines that added up to it (so the admin can see why).
public class CreditScore {

    private final int score;
    private final CreditBand band;
    private final List<ScoreFactor> factors;

    public CreditScore(int score, CreditBand band, List<ScoreFactor> factors) {
        this.score = score;
        this.band = band;
        this.factors = factors;
    }

    public int getScore() {
        return score;
    }

    public CreditBand getBand() {
        return band;
    }

    public List<ScoreFactor> getFactors() {
        return factors;
    }
}
