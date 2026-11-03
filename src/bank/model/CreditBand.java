package bank.model;

import java.math.BigDecimal;

// A credit score falls into one of four bands, a bit like grades.
// The band (not the exact score) decides what a customer is allowed:
//  - the largest single loan
//  - how many loans they can have open at once (this is also their tier)
//  - the advice the system gives on their applications
//
// These numbers are starting values. To change a limit, change it here.
public enum CreditBand {

    EXCELLENT(750, "1000000", 3, CreditTier.PREMIUM, Advice.LEND),
    GOOD(670, "600000", 3, CreditTier.PREMIUM, Advice.LEND),
    FAIR(580, "300000", 2, CreditTier.STANDARD, Advice.REVIEW),
    POOR(300, "0", 0, CreditTier.STANDARD, Advice.DECLINE);

    private final int minScore;
    private final BigDecimal maxSingleLoan;
    private final int maxActiveLoans;
    private final CreditTier tier;
    private final Advice advice;

    CreditBand(int minScore, String maxSingleLoan, int maxActiveLoans, CreditTier tier, Advice advice) {
        this.minScore = minScore;
        this.maxSingleLoan = new BigDecimal(maxSingleLoan);
        this.maxActiveLoans = maxActiveLoans;
        this.tier = tier;
        this.advice = advice;
    }

    public int getMinScore() {
        return minScore;
    }

    public BigDecimal getMaxSingleLoan() {
        return maxSingleLoan;
    }

    public int getMaxActiveLoans() {
        return maxActiveLoans;
    }

    // Premium or Standard. A POOR customer can't borrow at all (0 active loans allowed).
    public CreditTier getTier() {
        return tier;
    }

    public Advice getAdvice() {
        return advice;
    }

    // picks the band for a score by checking the best band first
    public static CreditBand fromScore(int score) {
        for (CreditBand band : values()) {
            if (score >= band.minScore) {
                return band;
            }
        }
        return POOR;
    }
}
