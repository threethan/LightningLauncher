package com.threethan.launchercore.lib;


/** @noinspection unused*/
public class StringLib {
    public static final char STAR = '★';
    public static final char NEW = '►';
    public static final char RECENT = '◄';
    public static String togglePreChar(String in, char c) {
        if (hasPreChar(in, c)) return in.substring(1);
        else return c + in;
    }
    public static boolean hasPreChar(String in, char c) {
        return !in.isEmpty() && in.charAt(0) == c;
    }
    public static String withoutPreChar(String in, char c) {
        if (hasPreChar(in, c)) return in.substring(1);
        else return in;
    }
    public static String setPreChar(String in, char c, boolean present) {
        in = in.trim();
        if (hasPreChar(in, c) != present) return togglePreChar(in, c);
        else return in;
    }

    public static String withoutPreChars(String in) {
        in = withoutPreChar(in, STAR);
        in = withoutPreChar(in, NEW);
        in = withoutPreChar(in, RECENT);
        return in;
    }


    public static boolean isInvalidUrl(String url) {
        if (url.startsWith("about:")) return false;
        if (url.startsWith("http") && !url.contains(".")) return true;
        if (url.endsWith(".") || url.endsWith("://")) return true;
        return !url.contains("://");
    }
    public static String toValidFilename(String string) {
        if (string.startsWith("json://")) // Hash json that would otherwise be too long
            return  "shortcut-json-hash-" + string.hashCode();

        string = string.replaceAll("[^A-Za-z0-9.]", "");

        if (string.length()>50) return string.substring(0, 10) + string.hashCode();
        return string;
    }
    public static String toTitleCase(String string) {
        if (string == null) return null;
        boolean whiteSpace = true;
        StringBuilder builder = new StringBuilder(string);
        for (int i = 0; i < builder.length(); ++i) {
            char c = builder.charAt(i);
            if (whiteSpace) {
                if (!Character.isWhitespace(c)) {
                    builder.setCharAt(i, Character.toTitleCase(c));
                    whiteSpace = false;
                }
            } else if (Character.isWhitespace(c)) whiteSpace = true;
            else builder.setCharAt(i, Character.toLowerCase(c));
        }
        return builder.toString();
    }

    public static String baseUrlWithScheme(String string) {
        try {
            return string.split("//")[0] + "//" + string.split("/")[2];
        } catch (Exception ignored) { return string; }
    }
    public static String baseUrl(String string) {
        try {
            return string.split("/")[2];
        } catch (Exception ignored) {
            return string; }
    }
    public static final String GOOGLE_SEARCH_PRE = "https://www.google.com/search?q=";
    public static final String YOUTUBE_SEARCH_PRE = "https://www.youtube.com/results?search_query=";
    public static final String APK_MIRROR_SEARCH_PRE = "https://www.apkmirror.com/?post_type=app_release&searchtype=apk&s=";

    public static String googleSearchForUrl(String string) {
        return GOOGLE_SEARCH_PRE+string;
    }
    public static String youTubeSearchForUrl(String string) {
        return YOUTUBE_SEARCH_PRE+string;
    }
    public static String apkMirrorSearchForUrl(String string) {
        return APK_MIRROR_SEARCH_PRE +string;
    }
    public static String playStoreSearchForUrl(String string) {
        return "https://play.google.com/store/search?q="+string+"&c=apps";
    }
    public static boolean isSearchUrl(String url) {
        return url.contains("search?q=") || url.contains("?search_query=") || url.contains("&searchtype=");
    }

    public static boolean isInteger(String str) {
        try {
            Integer.parseInt(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
