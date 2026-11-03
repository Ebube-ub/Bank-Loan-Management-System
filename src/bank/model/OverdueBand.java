package bank.model;

// How late an installment is, as a colour band. The screen shows the colour, and
// the label beside it so it still reads for anyone who can't tell colours apart.
// To change a band, change its first day here.
//
//   1 to 10 days    yellow    Just late
//   11 to 20 days   orange    Late
//   21 to 30 days   red       Seriously late
//   31 to 60 days   dark red  Very late
//   over 60 days    maroon    Critical
public enum OverdueBand {

    YELLOW(1, "Just late"),
    ORANGE(11, "Late"),
    RED(21, "Seriously late"),
    DARK_RED(31, "Very late"),
    MAROON(61, "Critical");

    private final int firstDay;
    private final String label;

    OverdueBand(int firstDay, String label) {
        this.firstDay = firstDay;
        this.label = label;
    }

    public int getFirstDay() {
        return firstDay;
    }

    public String getLabel() {
        return label;
    }

    // the band for this many days late (anything under 1 day counts as the first band)
    public static OverdueBand forDays(long daysOverdue) {
        OverdueBand result = YELLOW;

        for (OverdueBand band : values()) {
            if (daysOverdue >= band.firstDay) {
                result = band;
            }
        }

        return result;
    }
}
