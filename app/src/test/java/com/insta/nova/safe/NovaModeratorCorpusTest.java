package com.insta.nova.safe;

import static org.junit.Assert.assertTrue;

import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

/**
 * Measures the shipped lexicon against a hand-labelled corpus.
 *
 * <p>The corpus is 100 lines of label + tab + comment (1 = must be flagged, 0 = must stay
 * clean). The test prints the confusion matrix and fails only if precision or recall drops
 * below the documented floor, so a lexicon edit cannot silently make the engine worse.
 */
public class NovaModeratorCorpusTest {

    /** Floors the shipped lexicon must keep. Documented in docs/NOVA_REPORT.md. */
    private static final double MIN_PRECISION = 0.90;
    private static final double MIN_RECALL = 0.85;

    private static NovaModerator moderator;
    private static String corpus;

    @BeforeClass
    public static void load() throws Exception {
        File lex = new File("src/main/assets/nova_lexicon.json");
        if (!lex.exists()) lex = new File("app/src/main/assets/nova_lexicon.json");
        assertTrue("lexicon asset must exist", lex.exists());
        moderator = NovaModerator.fromJson(
                new String(Files.readAllBytes(lex.toPath()), StandardCharsets.UTF_8));

        File tsv = new File("src/test/resources/moderator_corpus.tsv");
        if (!tsv.exists()) tsv = new File("app/src/test/resources/moderator_corpus.tsv");
        assertTrue("corpus must exist", tsv.exists());
        corpus = new String(Files.readAllBytes(tsv.toPath()), StandardCharsets.UTF_8);
    }

    @Test
    public void precisionAndRecall() {
        int tp = 0, fp = 0, fn = 0, tn = 0, total = 0;
        StringBuilder misses = new StringBuilder();

        for (String raw : corpus.split("\n")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            int tab = line.indexOf('\t');
            if (tab <= 0) continue;
            boolean expected = "1".equals(line.substring(0, tab).trim());
            String text = line.substring(tab + 1).trim();
            if (text.isEmpty()) continue;
            total++;

            NovaModerator.Result result = moderator.moderate(text);
            boolean flagged = !result.isClean();
            if (expected && flagged) tp++;
            else if (!expected && flagged) {
                fp++;
                misses.append("FP ").append(text).append(" -> ")
                        .append(result.category).append('/').append(result.severity).append('\n');
            } else if (expected) {
                fn++;
                misses.append("FN ").append(text).append('\n');
            } else tn++;
        }

        double precision = (tp + fp) == 0 ? 0 : (double) tp / (tp + fp);
        double recall = (tp + fn) == 0 ? 0 : (double) tp / (tp + fn);
        double accuracy = (double) (tp + tn) / total;

        System.out.println("NOVA_MODERATION_CORPUS total=" + total
                + " tp=" + tp + " fp=" + fp + " fn=" + fn + " tn=" + tn
                + String.format(" precision=%.3f recall=%.3f accuracy=%.3f",
                precision, recall, accuracy));
        if (misses.length() > 0) System.out.println("NOVA_MODERATION_MISSES\n" + misses);

        assertTrue("precision " + precision + " below floor " + MIN_PRECISION,
                precision >= MIN_PRECISION);
        assertTrue("recall " + recall + " below floor " + MIN_RECALL,
                recall >= MIN_RECALL);
    }

    /** Every harmful sample must be scored, never skipped, so spans are deterministic. */
    @Test
    public void flaggedSamplesCarryCategoryAndSpan() {
        List<String> harmful = java.util.Arrays.asList(
                "you are such an idiot", "i will kill you tonight", "send nudes please");
        for (String text : harmful) {
            NovaModerator.Result result = moderator.moderate(text);
            assertTrue(text, !result.isClean());
            assertTrue(text, result.category != null && !result.category.isEmpty());
            assertTrue(text, !result.spans.isEmpty());
        }
    }
}
