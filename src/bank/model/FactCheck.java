package bank.model;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

// A guard on the AI's wording: it is told to use only the facts it was given, and this
// checks it. Any number in its sentence (amounts, dates, counts, scores) must appear
// somewhere in the facts. An invented figure means the AI's sentence is thrown away
// and the system's own sentence is shown instead.
public class FactCheck {

    private static final Pattern NUMBER = Pattern.compile("\\d+");

    public static boolean numbersAreKnown(String sentence, String facts) {
        Matcher numbers = NUMBER.matcher(sentence);

        while (numbers.find()) {
            if (!containsNumber(facts, numbers.group())) {
                return false;
            }
        }
        return true;
    }

    // true if the facts contain this run of digits as a whole number
    // (so "20" is found in "20%" but not in "2000")
    private static boolean containsNumber(String facts, String digits) {
        Matcher found = NUMBER.matcher(facts);

        while (found.find()) {
            if (found.group().equals(digits)) {
                return true;
            }
        }
        return false;
    }
}
