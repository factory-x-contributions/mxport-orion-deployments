package org.eclipse.edc.common.spi.helpers;

public class Helpers {

    public static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (var v : values) {
            if (v != null && !v.trim().isEmpty()) {
                return v;
            }
        }
        return null;
    }
}
