package bank;

import bank.model.OverdueBand;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

// The colour bands for late installments: 1-10 yellow, 11-20 orange, 21-30 red,
// 31-60 dark red, over 60 maroon.
class OverdueBandTest {

    @Test
    void eachBandStartsOnItsFirstDay() {
        assertEquals(OverdueBand.YELLOW, OverdueBand.forDays(1));
        assertEquals(OverdueBand.YELLOW, OverdueBand.forDays(10));
        assertEquals(OverdueBand.ORANGE, OverdueBand.forDays(11));
        assertEquals(OverdueBand.ORANGE, OverdueBand.forDays(20));
        assertEquals(OverdueBand.RED, OverdueBand.forDays(21));
        assertEquals(OverdueBand.RED, OverdueBand.forDays(30));
        assertEquals(OverdueBand.DARK_RED, OverdueBand.forDays(31));
        assertEquals(OverdueBand.DARK_RED, OverdueBand.forDays(60));
        assertEquals(OverdueBand.MAROON, OverdueBand.forDays(61));
        assertEquals(OverdueBand.MAROON, OverdueBand.forDays(400));
    }

    @Test
    void theExamplesFromThePlan() {
        assertEquals(OverdueBand.ORANGE, OverdueBand.forDays(18));
        assertEquals(OverdueBand.ORANGE, OverdueBand.forDays(16));
        assertEquals(OverdueBand.MAROON, OverdueBand.forDays(75));
        assertEquals(OverdueBand.DARK_RED, OverdueBand.forDays(44));
        assertEquals(OverdueBand.ORANGE, OverdueBand.forDays(14));
    }

    @Test
    void theLabelsAreShownBesideTheColour() {
        assertEquals("Just late", OverdueBand.YELLOW.getLabel());
        assertEquals("Late", OverdueBand.ORANGE.getLabel());
        assertEquals("Seriously late", OverdueBand.RED.getLabel());
        assertEquals("Very late", OverdueBand.DARK_RED.getLabel());
        assertEquals("Critical", OverdueBand.MAROON.getLabel());
    }
}
