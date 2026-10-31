package com.recordingportal.backend.recording;

import java.util.Locale;

/** Turns a User-Agent into the short label shown on the admin's viewing timeline ("Mobile · Chrome"). */
final class DeviceLabel {

    private DeviceLabel() {
    }

    static String from(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) return null;
        String ua = userAgent.toLowerCase(Locale.ROOT);
        String device = ua.contains("ipad") || ua.contains("tablet") ? "Tablet"
                : ua.contains("mobi") || ua.contains("android") || ua.contains("iphone") ? "Mobile"
                : "Computer";
        String browser = ua.contains("edg/") ? "Edge"
                : ua.contains("samsungbrowser") ? "Samsung Internet"
                : ua.contains("firefox") || ua.contains("fxios") ? "Firefox"
                : ua.contains("chrome") || ua.contains("crios") ? "Chrome"
                : ua.contains("safari") ? "Safari"
                : null;
        return browser == null ? device : device + " · " + browser;
    }
}
