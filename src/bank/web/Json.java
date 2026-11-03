package bank.web;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

// A tiny helper for building JSON text by hand, so we don't need a library
// like Gson. Use it like this:
//
//     new Json().add("name", "Ada").add("count", 3).toString()
//     -> {"name":"Ada","count":3}
//
// Money is always written as a string with 2 decimals ("12200.00") so the
// browser never turns it into a rounded floating-point number.
public class Json {

    private StringBuilder text = new StringBuilder("{");
    private boolean first = true;

    public Json add(String key, String value) {
        return addRaw(key, quote(value));
    }

    public Json add(String key, long value) {
        return addRaw(key, String.valueOf(value));
    }

    public Json add(String key, boolean value) {
        return addRaw(key, String.valueOf(value));
    }

    public Json add(String key, BigDecimal money) {
        return addRaw(key, quote(money(money)));
    }

    // adds a value that is already JSON (an object or array built elsewhere)
    public Json addRaw(String key, String json) {
        if (!first) {
            text.append(",");
        }
        text.append(quote(key)).append(":").append(json);
        first = false;
        return this;
    }

    @Override
    public String toString() {
        return text + "}";
    }

    // joins already-built JSON values into a JSON array: [a,b,c]
    public static String array(List<String> items) {
        return "[" + String.join(",", items) + "]";
    }

    // turns a list of plain text into a JSON array of strings: ["a","b"]
    public static String stringArray(List<String> texts) {
        List<String> quoted = new java.util.ArrayList<>();
        for (String text : texts) {
            quoted.add(quote(text));
        }
        return array(quoted);
    }

    public static String money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    // wraps text in quotes and escapes the characters JSON doesn't allow
    public static String quote(String value) {
        if (value == null) {
            return "null";
        }

        StringBuilder result = new StringBuilder("\"");

        for (char c : value.toCharArray()) {
            if (c == '"') {
                result.append("\\\"");
            } else if (c == '\\') {
                result.append("\\\\");
            } else if (c == '\n') {
                result.append("\\n");
            } else if (c == '\r') {
                result.append("\\r");
            } else if (c == '\t') {
                result.append("\\t");
            } else if (c < 0x20) {
                result.append(String.format("\\u%04x", (int) c));
            } else {
                result.append(c);
            }
        }

        return result.append("\"").toString();
    }
}
