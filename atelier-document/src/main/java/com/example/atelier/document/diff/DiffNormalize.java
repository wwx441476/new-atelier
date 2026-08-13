package com.example.atelier.document.diff;

public final class DiffNormalize {

    private DiffNormalize() {
    }

    public static String normalizeLine(String line, boolean ignoreWhitespace) {
        if (line == null) {
            return "";
        }
        String s = line.replace("\r\n", "\n").replace('\r', '\n');
        if (ignoreWhitespace) {
            s = s.replace('\u3000', ' ').replaceAll("[ \\t\\x0B\\f]+", " ").trim();
        }
        return s;
    }

    public static String normalizeParagraph(String text, boolean ignoreWhitespace) {
        if (text == null) {
            return "";
        }
        String s = text.replace("\r\n", "\n").replace('\r', '\n');
        if (ignoreWhitespace) {
            s = s.replace('\u3000', ' ').replaceAll("[ \\t\\x0B\\f]+", " ");
            s = s.replaceAll(" *\\n *", "\n").trim();
        }
        return s;
    }

    public static double similarity(String a, String b) {
        if (a == null || b == null) {
            return 0;
        }
        if (a.equals(b)) {
            return 1.0;
        }
        if (a.isEmpty() || b.isEmpty()) {
            return 0;
        }
        int distance = levenshtein(a, b);
        int max = Math.max(a.length(), b.length());
        return 1.0 - ((double) distance / max);
    }

    private static int levenshtein(String a, String b) {
        int n = a.length();
        int m = b.length();
        if (n > 2000 || m > 2000) {
            return Math.abs(n - m) + Math.min(n, m);
        }
        int[] prev = new int[m + 1];
        int[] cur = new int[m + 1];
        for (int j = 0; j <= m; j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= n; i++) {
            cur[0] = i;
            char ca = a.charAt(i - 1);
            for (int j = 1; j <= m; j++) {
                int cost = ca == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev;
            prev = cur;
            cur = tmp;
        }
        return prev[m];
    }
}
