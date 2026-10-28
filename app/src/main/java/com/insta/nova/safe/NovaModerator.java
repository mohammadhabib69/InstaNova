package com.insta.nova.safe;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Deterministic, fully offline moderation.
 *
 * <p>Pipeline: NFKC → lowercase → strip zero-width/diacritics → leetspeak map →
 * collapse 3+ repeats → join single-letter chains (f u c k). Matching is whole-word
 * first, then guarded substrings, with a whitelist for harmless look-alikes and
 * context reducers that clamp jokes to severity 1.
 *
 * <p>Pure Java (no Android APIs except the asset loader) so the 60+ JVM tests exercise
 * exactly the code that ships. Typical 300-character input: well under 5 ms.
 */
public final class NovaModerator {

    private static final int SCORE_BASE[] = {0, 26, 52, 78};
    private static final Map<String, Integer> CATEGORY_RANK = new HashMap<>();
    private static volatile NovaModerator instance;

    static {
        CATEGORY_RANK.put("threat", 6);
        CATEGORY_RANK.put("hate", 5);
        CATEGORY_RANK.put("sexual", 4);
        CATEGORY_RANK.put("harassment", 3);
        CATEGORY_RANK.put("insult", 2);
        CATEGORY_RANK.put("spam", 1);
    }

    private final List<Lexeme> lexemes = new ArrayList<>();
    private final List<String> whitelist = new ArrayList<>();
    private final List<String> reducers = new ArrayList<>();

    private NovaModerator() {
    }

    public static NovaModerator get(Context context) {
        if (instance == null) {
            synchronized (NovaModerator.class) {
                if (instance == null) {
                    instance = fromJson(readAsset(context));
                }
            }
        }
        return instance;
    }

    public static NovaModerator fromJson(String json) {
        NovaModerator moderator = new NovaModerator();
        try {
            JSONObject root = new JSONObject(json);
            JSONArray whitelistArray = root.optJSONArray("whitelist");
            if (whitelistArray != null) {
                for (int i = 0; i < whitelistArray.length(); i++) {
                    String value = normalizeText(whitelistArray.getString(i));
                    if (!value.isEmpty()) moderator.whitelist.add(value);
                }
            }
            JSONArray reducersArray = root.optJSONArray("context_reducers");
            if (reducersArray != null) {
                for (int i = 0; i < reducersArray.length(); i++) {
                    String value = normalizeText(reducersArray.getString(i));
                    if (!value.isEmpty()) moderator.reducers.add(value);
                }
            }
            JSONArray terms = root.getJSONArray("terms");
            for (int i = 0; i < terms.length(); i++) {
                JSONObject object = terms.getJSONObject(i);
                Lexeme lexeme = new Lexeme();
                lexeme.term = object.getString("term");
                lexeme.category = object.getString("category");
                lexeme.severity = object.getInt("severity");
                lexeme.script = object.optString("script", "english");
                JSONArray variants = object.optJSONArray("variants");
                Set<String> forms = new HashSet<>();
                forms.add(normalizeText(lexeme.term));
                if (variants != null) {
                    for (int v = 0; v < variants.length(); v++) {
                        forms.add(normalizeText(variants.getString(v)));
                    }
                }
                for (String form : forms) {
                    if (!form.isEmpty()) lexeme.forms.add(form);
                }
                if (!lexeme.forms.isEmpty()) {
                    lexeme.forms.sort((a, b) -> Integer.compare(b.length(), a.length()));
                    moderator.lexemes.add(lexeme);
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Nova lexicon is invalid: " + e.getMessage(), e);
        }
        moderator.lexemes.sort((a, b) -> Integer.compare(b.maxFormLength(), a.maxFormLength()));
        return moderator;
    }

    private static String readAsset(Context context) {
        try (InputStream input = context.getAssets().open("nova_lexicon.json")) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Nova lexicon asset missing", e);
        }
    }

    /* ------------------------------------------------------------------ */
    /* Public API                                                          */
    /* ------------------------------------------------------------------ */

    public Result moderate(String text) {
        if (text == null || text.trim().isEmpty()) return Result.clean();
        Norm norm = normalize(text);
        List<int[]> whitelisted = findRanges(norm.text, whitelist);
        List<Span> spans = new ArrayList<>();
        int severity = 0;
        Map<String, Integer> categoryHits = new HashMap<>();
        int reducerRadius = 40;
        List<Integer> reducerPositions = positions(norm.text, reducers);

        for (Lexeme lexeme : lexemes) {
            for (String form : lexeme.forms) {
                int from = 0;
                while (from <= norm.text.length() - form.length()) {
                    int start = norm.text.indexOf(form, from);
                    if (start < 0) break;
                    int end = start + form.length();
                    from = start + 1;
                    if (!boundaryOk(norm.text, start, end)) continue;
                    if (insideAny(start, end, whitelisted)) continue;
                    boolean reduced = nearReducer(start, end, reducerPositions, reducerRadius);
                    int effective = reduced ? Math.min(1, lexeme.severity) : lexeme.severity;
                    if (effective <= 0) continue;
                    if (overlaps(spans, start, end)) continue;
                    spans.add(new Span(originalIndex(norm.map, start),
                            originalIndex(norm.map, end - 1) + 1,
                            lexeme.term, lexeme.category, effective));
                    severity = Math.max(severity, effective);
                    Integer count = categoryHits.get(lexeme.category);
                    categoryHits.put(lexeme.category, count == null ? 1 : count + 1);
                }
            }
        }

        if (spans.isEmpty()) return Result.clean();
        Collections.sort(spans, (a, b) -> Integer.compare(a.start, b.start));
        String category = dominantCategory(categoryHits, spans);
        int score = SCORE_BASE[Math.min(3, severity)] + Math.min(22, (spans.size() - 1) * 6);
        return new Result(category, severity, Math.min(100, score), spans);
    }

    /** Exposed for tests and the playground's "normalised text" line. */
    public static String normalizeText(String input) {
        return normalize(input).text;
    }

    /* ------------------------------------------------------------------ */
    /* Normalisation                                                       */
    /* ------------------------------------------------------------------ */

    private static final class Norm {
        final String text;
        final int[] map;

        Norm(String text, int[] map) {
            this.text = text;
            this.map = map;
        }
    }

    private static Norm normalize(String input) {
        String nfkc = Normalizer.normalize(input, Normalizer.Form.NFKC);
        int[] nfkcToOriginal = alignToOriginal(input, nfkc);
        boolean[] skip = singleLetterChainSeparators(nfkc);

        StringBuilder out = new StringBuilder(nfkc.length());
        List<Integer> map = new ArrayList<>(nfkc.length());
        boolean collapsing = false;
        for (int i = 0; i < nfkc.length(); i++) {
            if (skip[i]) continue;
            char c = nfkc.charAt(i);
            if (isZeroWidth(c)) continue;
            char lower = Character.toLowerCase(c);
            if (lower >= '\u0300' && lower <= '\u036F') continue; // Latin combining marks only
            if (lower == '*') continue; // f*ck → fck
            // A run of "!" is punctuation, not leetspeak: "idiot!!!!" must stay
            // "idiot" plus a boundary, while a single "!" inside a word still
            // decodes ("b!tch" → "bitch").
            if (lower == '!') {
                boolean inRun = (i > 0 && nfkc.charAt(i - 1) == '!')
                        || (i + 1 < nfkc.length() && nfkc.charAt(i + 1) == '!');
                if (inRun) {
                    out.append(' ');
                    map.add(nfkcToOriginal[i]);
                    collapsing = false;
                    continue;
                }
            }
            char mapped = leet(lower);
            // Collapse runs of 3+ identical letters to one: loooser → loser.
            // A plain double letter (good, need) is preserved.
            int n = out.length();
            if (n >= 1 && out.charAt(n - 1) == mapped && (collapsing || hasSameLater(nfkc, i, mapped))) {
                collapsing = true;
                continue;
            }
            collapsing = false;
            out.append(mapped);
            map.add(nfkcToOriginal[i]);
        }
        int[] array = new int[map.size()];
        for (int i = 0; i < array.length; i++) array[i] = map.get(i);
        return new Norm(out.toString(), array);
    }

    private static int[] alignToOriginal(String original, String nfkc) {
        int[] map = new int[nfkc.length()];
        int oi = 0;
        for (int i = 0; i < nfkc.length(); i++) {
            char target = Character.toLowerCase(nfkc.charAt(i));
            while (oi < original.length() && Character.toLowerCase(original.charAt(oi)) != target) {
                oi++;
            }
            map[i] = Math.min(oi, Math.max(0, original.length() - 1));
            if (oi < original.length()) oi++;
        }
        return map;
    }

    private static boolean[] singleLetterChainSeparators(String text) {
        boolean[] skip = new boolean[text.length()];
        int i = 0;
        while (i < text.length()) {
            boolean leftIsolated = i == 0 || !Character.isLetterOrDigit(text.charAt(i - 1));
            if (Character.isLetter(text.charAt(i)) && leftIsolated) {
                int j = i;
                int letters = 1;
                List<Integer> separators = new ArrayList<>();
                while (true) {
                    int sepStart = j + 1;
                    if (sepStart >= text.length() || !isSeparator(text.charAt(sepStart))) break;
                    int k = sepStart;
                    while (k < text.length() && isSeparator(text.charAt(k))) k++;
                    if (k >= text.length() || !Character.isLetter(text.charAt(k))) break;
                    boolean rightIsolated = k + 1 >= text.length()
                            || !Character.isLetterOrDigit(text.charAt(k + 1));
                    if (!rightIsolated) break;
                    for (int index = sepStart; index < k; index++) separators.add(index);
                    j = k;
                    letters++;
                }
                if (letters >= 3) {
                    for (int index : separators) skip[index] = true;
                    i = j + 1;
                    continue;
                }
            }
            i++;
        }
        return skip;
    }

    /** True when the same normalised letter occurs again immediately after {@code index}. */
    private static boolean hasSameLater(String text, int index, char mapped) {
        for (int j = index + 1; j < text.length(); j++) {
            char c = text.charAt(j);
            if (isZeroWidth(c)) continue;
            char candidate = leet(Character.toLowerCase(c));
            if (candidate == mapped) return true;
            return false;
        }
        return false;
    }

    private static char leet(char c) {
        switch (c) {
            case '0': return 'o';
            case '1': return 'i';
            case '3': return 'e';
            case '4': return 'a';
            case '5': return 's';
            case '7': return 't';
            case '@': return 'a';
            case '$': return 's';
            case '!': return 'i';
            default: return c;
        }
    }

    private static boolean isZeroWidth(char c) {
        return c == '\u200B' || c == '\u200C' || c == '\u200D' || c == '\uFEFF'
                || c == '\u00AD' || c == '\u2060';
    }

    private static boolean isSeparator(char c) {
        return c == ' ' || c == '.' || c == '-' || c == '_' || c == '\n' || c == '\t' || c == '*';
    }

    private static boolean isLetter(char c) {
        return Character.isLetter(c);
    }

    /* ------------------------------------------------------------------ */
    /* Matching helpers                                                    */
    /* ------------------------------------------------------------------ */

    private static boolean boundaryOk(String text, int start, int end) {
        boolean left = start == 0 || !Character.isLetterOrDigit(text.charAt(start - 1));
        boolean right = end >= text.length() || !Character.isLetterOrDigit(text.charAt(end));
        return left && right;
    }

    private static int originalIndex(int[] map, int index) {
        if (map.length == 0) return 0;
        int clamped = Math.max(0, Math.min(map.length - 1, index));
        return map[clamped];
    }

    private static List<int[]> findRanges(String text, List<String> needles) {
        List<int[]> ranges = new ArrayList<>();
        for (String needle : needles) {
            if (needle.isEmpty()) continue;
            int from = 0;
            while (from <= text.length() - needle.length()) {
                int start = text.indexOf(needle, from);
                if (start < 0) break;
                ranges.add(new int[]{start, start + needle.length()});
                from = start + 1;
            }
        }
        return ranges;
    }

    private static List<Integer> positions(String text, List<String> needles) {
        List<Integer> positions = new ArrayList<>();
        for (String needle : needles) {
            if (needle.isEmpty()) continue;
            int from = 0;
            while (from <= text.length() - needle.length()) {
                int start = text.indexOf(needle, from);
                if (start < 0) break;
                positions.add(start);
                from = start + 1;
            }
        }
        return positions;
    }

    private static boolean insideAny(int start, int end, List<int[]> ranges) {
        for (int[] range : ranges) {
            if (start >= range[0] && end <= range[1]) return true;
        }
        return false;
    }

    private static boolean nearReducer(int start, int end, List<Integer> positions, int radius) {
        for (int position : positions) {
            if (Math.abs(position - start) <= radius || Math.abs(position - end) <= radius) return true;
        }
        return false;
    }

    private static boolean overlaps(List<Span> spans, int start, int end) {
        for (Span span : spans) {
            if (start < span.end && end > span.start) return true;
        }
        return false;
    }

    private static String dominantCategory(Map<String, Integer> hits, List<Span> spans) {
        String best = null;
        int bestSeverity = -1;
        int bestRank = -1;
        for (Span span : spans) {
            int rank = CATEGORY_RANK.containsKey(span.category) ? CATEGORY_RANK.get(span.category) : 0;
            if (span.severity > bestSeverity || (span.severity == bestSeverity && rank > bestRank)) {
                bestSeverity = span.severity;
                bestRank = rank;
                best = span.category;
            }
        }
        return best;
    }

    public int lexiconSize() {
        return lexemes.size();
    }

    /* ------------------------------------------------------------------ */
    /* Result types                                                        */
    /* ------------------------------------------------------------------ */

    private static final class Lexeme {
        String term;
        String category;
        int severity;
        String script;
        final List<String> forms = new ArrayList<>();

        int maxFormLength() {
            int max = 0;
            for (String form : forms) max = Math.max(max, form.length());
            return max;
        }
    }

    public static final class Span {
        public final int start;
        public final int end;
        public final String term;
        public final String category;
        public final int severity;

        Span(int start, int end, String term, String category, int severity) {
            this.start = start;
            this.end = end;
            this.term = term;
            this.category = category;
            this.severity = severity;
        }
    }

    public static final class Result {
        public final String category;
        public final int severity;
        public final int score;
        public final List<Span> spans;

        Result(String category, int severity, int score, List<Span> spans) {
            this.category = category;
            this.severity = severity;
            this.score = score;
            this.spans = spans;
        }

        static Result clean() {
            return new Result(null, 0, 0, new ArrayList<>());
        }

        public boolean isClean() {
            return severity == 0;
        }

        public String matchedTerms() {
            StringBuilder builder = new StringBuilder();
            for (Span span : spans) {
                if (builder.length() > 0) builder.append(", ");
                builder.append(span.term);
            }
            return builder.toString();
        }

        public String label() {
            if (category == null) return "clean";
            return category + " (severity " + severity + ")";
        }
    }
}
