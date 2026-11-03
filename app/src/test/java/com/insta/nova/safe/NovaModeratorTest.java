package com.insta.nova.safe;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** 70 deterministic cases over the shipped lexicon: English, Bangla, Banglish, obfuscated. */
public class NovaModeratorTest {

    private static NovaModerator moderator;

    @BeforeClass
    public static void load() throws Exception {
        File file = new File("src/main/assets/nova_lexicon.json");
        if (!file.exists()) file = new File("app/src/main/assets/nova_lexicon.json");
        assertTrue("lexicon asset must exist", file.exists());
        String json = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        moderator = NovaModerator.fromJson(json);
        assertTrue("lexicon should load terms", moderator.lexiconSize() > 50);
    }

    private static NovaModerator.Result check(String text) {
        return moderator.moderate(text);
    }

    /* ------------------------------ clean ---------------------------------- */

    @Test public void cleanGreeting() { assertTrue(check("Hello, how are you?").isClean()); }
    @Test public void cleanBengali() { assertTrue(check("আজ অনেক ভালো লাগছে").isClean()); }
    @Test public void cleanStudy() { assertTrue(check("Let us finish the database assignment before Friday.").isClean()); }
    @Test public void cleanEmpty() { assertTrue(check("").isClean()); }
    @Test public void cleanNull() { assertTrue(check(null).isClean()); }
    @Test public void cleanWhitespace() { assertTrue(check("      ").isClean()); }
    @Test public void whitelistSussex() { assertTrue(check("I study at Sussex University").isClean()); }
    @Test public void whitelistSexton() { assertTrue(check("Sexton is a surname").isClean()); }
    @Test public void whitelistSkill() { assertTrue(check("This skill is useful").isClean()); }
    @Test public void whitelistDeadline() { assertTrue(check("The deadline is tomorrow").isClean()); }
    @Test public void whitelistClassic() { assertTrue(check("classic music night").isClean()); }
    @Test public void whitelistAssassin() { assertTrue(check("Assassin is a game character").isClean()); }
    @Test public void repeatedLetterCollapse() { assertEquals("god", NovaModerator.normalizeText("goooood")); }
    @Test public void doubleLetterPreserved() { assertEquals("good", NovaModerator.normalizeText("good")); }

    /* ------------------------------ insult --------------------------------- */

    @Test public void insultIdiot() { assertSeverity("you are an idiot", 1, "insult"); }
    @Test public void insultUpperCase() { assertSeverity("IDIOT", 1, "insult"); }
    @Test public void insultLeetIdiot() { assertSeverity("id10t", 1, "insult"); }
    @Test public void insultSingleLetters() { assertSeverity("i d i o t", 1, "insult"); }
    @Test public void insultShit() { assertSeverity("this is shit", 1, "insult"); }
    @Test public void insultShitLeet() { assertSeverity("sh1t", 1, "insult"); }
    @Test public void insultShitSymbol() { assertSeverity("$hit", 1, "insult"); }
    @Test public void insultBitchSymbol() { assertSeverity("b!tch", 2, "insult"); }
    @Test public void insultFuckMasked() { assertSeverity("f*ck you", 2, "insult"); }
    @Test public void insultElongated() { assertSeverity("you loooser", 1, "insult"); }
    @Test public void insultFool() { assertSeverity("do not be a fool", 1, "insult"); }
    @Test public void insultWorthless() { assertSeverity("you are worthless", 2, "insult"); }
    @Test public void insultBanglaBoka() { assertSeverity("তুই একটা বোকা", 1, "insult"); }
    @Test public void insultBanglaGadha() { assertSeverity("তুই একটা গাধা", 1, "insult"); }
    @Test public void insultBanglaPagol() { assertSeverity("তুই পাগল", 1, "insult"); }
    @Test public void insultBanglaShala() { assertSeverity("শালা কোথায় ছিলি", 1, "insult"); }
    @Test public void insultBanglaHarami() { assertSeverity("হারামি কোথাকার", 2, "insult"); }
    @Test public void insultBanglaKutta() { assertSeverity("তুই কুত্তা", 2, "insult"); }
    @Test public void insultBanglaMadarchod() { assertSeverity("মাদারচোদ", 3, "insult"); }
    @Test public void insultBanglaKhanki() { assertSeverity("খানকি", 3, "insult"); }
    @Test public void insultBanglishBoka() { assertSeverity("tui ekta boka", 1, "insult"); }
    @Test public void insultBanglishGadha() { assertSeverity("tui gadha", 1, "insult"); }
    @Test public void insultBanglishPagol() { assertSeverity("tui pagol", 1, "insult"); }
    @Test public void insultBanglishShala() { assertSeverity("shala kothay chhili", 1, "insult"); }
    @Test public void insultBanglishHarami() { assertSeverity("harami kothakar", 2, "insult"); }
    @Test public void insultBanglishKutta() { assertSeverity("tui kutta", 2, "insult"); }
    @Test public void insultBanglishChagol() { assertSeverity("chagol", 1, "insult"); }
    @Test public void insultBanglishChutiya() { assertSeverity("tui chutiya", 2, "insult"); }
    @Test public void insultBanglishBhenchod() { assertSeverity("bhenchod", 3, "insult"); }
    @Test public void insultBanglishRandi() { assertSeverity("randi", 3, "insult"); }

    /* ------------------------------ threat --------------------------------- */

    @Test public void threatKillYou() { assertSeverity("I will kill you", 3, "threat"); }
    @Test public void threatKillYouCaps() { assertSeverity("KILL YOU", 3, "threat"); }
    @Test public void threatKillYouSpaced() { assertSeverity("k i l l  y o u", 3, "threat"); }
    @Test public void threatKillYourself() { assertSeverity("kill yourself", 3, "threat"); }
    @Test public void threatMurder() { assertSeverity("I will murder you", 3, "threat"); }
    @Test public void threatBeat() { assertSeverity("I will beat you", 2, "threat"); }
    @Test public void threatBones() { assertSeverity("I will break your bones", 3, "threat"); }
    @Test public void threatRegret() { assertSeverity("you will regret this", 2, "threat"); }
    @Test public void threatWatchBack() { assertSeverity("watch your back", 2, "threat"); }
    @Test public void threatKnowWhere() { assertSeverity("I know where you live", 3, "threat"); }
    @Test public void threatBanglaMarbo() { assertSeverity("তোমাকে মেরে ফেলব", 3, "threat"); }
    @Test public void threatBanglaKhun() { assertSeverity("তোমাকে খুন করব", 3, "threat"); }
    @Test public void threatBanglaGala() { assertSeverity("গলা টিপে মারব", 3, "threat"); }
    @Test public void threatBanglish() { assertSeverity("toke mere felbo", 3, "threat"); }

    /* ---------------------------- harassment ------------------------------- */

    @Test public void harassmentNudes() { assertSeverity("send nudes", 3, "harassment"); }
    @Test public void harassmentPictures() { assertSeverity("send me pictures", 2, "harassment"); }
    @Test public void harassmentStalk() { assertSeverity("I will stalk you", 2, "harassment"); }
    @Test public void harassmentCreep() { assertSeverity("you creep", 1, "harassment"); }
    /** "creepy" describes a thing ("scarecrow"), so it must not read as harassment. */
    @Test public void cleanCreepy() { assertTrue(check("that scarecrow looks creepy").isClean()); }
    @Test public void insultWithExclamationRun() { assertSeverity("you are such an idiot!!!!!!!!", 1, "insult"); }
    @Test public void spamBengaliLottery() { assertSeverity("লটারিতে জিতুন", 1, "spam"); }
    @Test public void harassmentHarass() { assertSeverity("stop harassing me", 2, "harassment"); }
    @Test public void harassmentFollow() { assertSeverity("I will follow you everywhere", 2, "harassment"); }
    @Test public void harassmentBangla() { assertSeverity("তুমি হয়রানি করছ", 2, "harassment"); }
    @Test public void harassmentBanglaNirjaton() { assertSeverity("নির্যাতন বন্ধ কর", 2, "harassment"); }
    @Test public void harassmentBanglaJor() { assertSeverity("জোর করে ধরব", 2, "harassment"); }

    /* -------------------------------- hate --------------------------------- */

    @Test public void hateSubhuman() { assertSeverity("they are subhuman", 3, "hate"); }
    @Test public void hateVermin() { assertSeverity("vermin", 3, "hate"); }
    @Test public void hateInferiorRace() { assertSeverity("an inferior race", 3, "hate"); }
    @Test public void hateGoBack() { assertSeverity("go back to your country", 2, "hate"); }
    @Test public void hateRefugee() { assertSeverity("filthy refugee", 3, "hate"); }
    @Test public void hateBanglaDeshChhar() { assertSeverity("দেশ ছাড়", 2, "hate"); }
    @Test public void hateBanglaGhrina() { assertSeverity("আমি ঘৃণা করি", 2, "hate"); }

    /* -------------------------------- sexual ------------------------------- */

    @Test public void sexualNude() { assertSeverity("nude", 2, "sexual"); }
    @Test public void sexualPorn() { assertSeverity("porn", 2, "sexual"); }
    @Test public void sexualChat() { assertSeverity("sex chat", 2, "sexual"); }
    @Test public void sexualRape() { assertSeverity("rape", 3, "sexual"); }
    @Test public void sexualChild() { assertSeverity("child porn", 3, "sexual"); }
    @Test public void sexualBanglaBeshya() { assertSeverity("বেশ্যা", 3, "sexual"); }
    @Test public void sexualBanglaMagi() { assertSeverity("মাগী", 2, "sexual"); }
    @Test public void sexualBanglaDhorhon() { assertSeverity("ধর্ষণ", 3, "sexual"); }
    @Test public void sexualBanglaChoda() { assertSeverity("চোদা", 3, "sexual"); }

    /* -------------------------------- spam --------------------------------- */

    @Test public void spamFollowers() { assertSeverity("free followers here", 1, "spam"); }
    @Test public void spamClick() { assertSeverity("click here to win", 1, "spam"); }
    @Test public void spamMoney() { assertSeverity("earn money fast", 1, "spam"); }
    @Test public void spamWhatsapp() { assertSeverity("whatsapp me", 1, "spam"); }
    @Test public void spamSubscribe() { assertSeverity("subscribe", 1, "spam"); }
    @Test public void spamBanglaLottery() { assertSeverity("লটারি জিতেছেন", 1, "spam"); }
    @Test public void spamBanglaMoney() { assertSeverity("টাকা আয়", 1, "spam"); }
    @Test public void spamBanglaLink() { assertSeverity("লিংকে ক্লিক", 1, "spam"); }

    /* --------------------------- behaviour/edges --------------------------- */

    @Test public void contextReducerClamps() { assertSeverity("I will kill you jk", 1, "threat"); }
    @Test public void contextReducerLol() { assertSeverity("kill you lol", 1, "threat"); }
    @Test public void contextReducerBangla() { assertSeverity("মেরে ফেলব মজা করছি", 1, "threat"); }
    @Test public void contextReducerDoesNotHideSeverityThreeWithoutCue() {
        assertEquals(3, check("I will kill you").severity);
    }
    @Test public void multipleSpans() {
        assertEquals(2, check("stupid and worthless").spans.size());
    }
    @Test public void threatBeatsInsult() {
        assertEquals("threat", check("you idiot, I will kill you").category);
    }
    @Test public void scoreIncreasesWithSeverity() {
        int mild = check("idiot").score;
        int harmful = check("worthless").score;
        int severe = check("kill you").score;
        assertTrue(mild < harmful);
        assertTrue(harmful < severe);
    }
    @Test public void scoreNeverExceedsHundred() {
        assertTrue(check("kill you kill yourself murder you rape").score <= 100);
    }
    @Test public void spansWithinOriginal() {
        String text = "You idiot, I will kill you.";
        NovaModerator.Result result = check(text);
        for (NovaModerator.Span span : result.spans) {
            assertTrue(span.start >= 0);
            assertTrue(span.end <= text.length());
            assertTrue(span.start < span.end);
        }
    }
    @Test public void spanPointsAtWord() {
        String text = "you are an idiot";
        NovaModerator.Result result = check(text);
        assertEquals("idiot", text.substring(result.spans.get(0).start, result.spans.get(0).end));
    }
    @Test public void zeroWidthNormalised() {
        assertEquals("idiot", NovaModerator.normalizeText("id\u200Biot"));
    }
    @Test public void fullWidthNormalised() {
        assertEquals("idiot", NovaModerator.normalizeText("ｉｄｉｏｔ"));
    }
    @Test public void leetNormalised() {
        assertEquals("shit", NovaModerator.normalizeText("$h1t"));
    }
    @Test public void deterministic() {
        NovaModerator.Result first = check("you idiot");
        NovaModerator.Result second = check("you idiot");
        assertEquals(first.severity, second.severity);
        assertEquals(first.score, second.score);
        assertEquals(first.spans.size(), second.spans.size());
    }
    @Test public void performanceUnderFiveMilliseconds() {
        StringBuilder builder = new StringBuilder();
        while (builder.length() < 300) builder.append("this is a normal sentence about study and campus. ");
        String text = builder.substring(0, 300);
        for (int i = 0; i < 20; i++) check(text); // warm up
        long started = System.nanoTime();
        for (int i = 0; i < 100; i++) check(text);
        long perRunNanos = (System.nanoTime() - started) / 100;
        assertTrue("300 chars took " + perRunNanos / 1_000_000.0 + " ms", perRunNanos < 5_000_000L);
    }

    private static void assertSeverity(String text, int severity, String category) {
        NovaModerator.Result result = check(text);
        assertNotNull(result);
        assertEquals("category for: " + text, category, result.category);
        assertEquals("severity for: " + text, severity, result.severity);
        assertFalse(result.spans.isEmpty());
        assertTrue(result.score > 0);
    }
}
