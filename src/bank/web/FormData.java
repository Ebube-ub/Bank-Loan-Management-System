package bank.web;

import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

// Reads "name=Ada&amount=100" style text (what an HTML form or a URL query
// string sends) into a map, with helpers that check each field.
//
// Every helper throws IllegalArgumentException with a friendly message,
// which ApiHandler turns into a 400 response the page can show.
public class FormData {

    private Map<String, String> values = new HashMap<>();

    public FormData(String encoded) {
        if (encoded == null || encoded.isEmpty()) {
            return;
        }

        for (String pair : encoded.split("&")) {
            int equals = pair.indexOf('=');
            String key = equals >= 0 ? pair.substring(0, equals) : pair;
            String value = equals >= 0 ? pair.substring(equals + 1) : "";

            values.put(decode(key), decode(value).trim());
        }
    }

    // returns the value, or null if the field wasn't sent or is empty
    public String get(String name) {
        String value = values.get(name);
        if (value == null || value.isEmpty()) {
            return null;
        }
        return value;
    }

    public String requireText(String name, String label) {
        String value = get(name);
        if (value == null) {
            throw new IllegalArgumentException(label + " is required.");
        }
        return value;
    }

    public BigDecimal requireDecimal(String name, String label) {
        String value = requireText(name, label);
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(label + " must be a number.");
        }
    }

    public int requireInt(String name, String label) {
        String value = requireText(name, label);
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(label + " must be a whole number.");
        }
    }

    public static String decode(String text) {
        return URLDecoder.decode(text, StandardCharsets.UTF_8);
    }
}
