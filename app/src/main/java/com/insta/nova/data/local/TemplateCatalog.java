package com.insta.nova.data.local;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The four built-in "Made for you" cards. Everything is local: each template only
 * describes its copy, accent colour and the two text fields the editor collects.
 */
public final class TemplateCatalog {

    /** Intent extra carrying {@link #id}. */
    public static final String EXTRA_TEMPLATE_ID = "EXTRA_TEMPLATE_ID";

    public static final class Template {
        public final String id;
        public final String title;
        public final String subtitle;
        public final String badge;
        public final int accentColor;
        public final String[] fieldHints;

        Template(String id, String title, String subtitle, String badge,
                 int accentColor, String[] fieldHints) {
            this.id = id;
            this.title = title;
            this.subtitle = subtitle;
            this.badge = badge;
            this.accentColor = accentColor;
            this.fieldHints = fieldHints;
        }

        /** Caption used when the rendered card is shared as a post or story. */
        public String caption(String first, String second) {
            String a = first == null ? "" : first.trim();
            String b = second == null ? "" : second.trim();
            switch (id) {
                case "birthday":
                    return "Happy Birthday, " + a + "! 🎂" + (b.isEmpty() ? "" : " " + b) + " #birthday";
                case "announcement":
                    return "New post: " + a + (b.isEmpty() ? "" : " — " + b);
                case "qa":
                    return "Q: " + a + (b.isEmpty() ? "" : "\nA: " + b);
                case "throwback":
                    return "Throwback to " + a + (b.isEmpty() ? "" : " — " + b) + " #tbt";
                default:
                    return (a + " " + b).trim();
            }
        }
    }

    private static final List<Template> TEMPLATES;

    static {
        List<Template> list = new ArrayList<>();
        list.add(new Template("birthday", "Birthday Shoutout",
                "Wish someone a big day", "BIRTHDAY", 0xFFE1306C,
                new String[]{"Name", "Your message"}));
        list.add(new Template("announcement", "New Post Announcement",
                "Tell everyone to go look", "NEW POST", 0xFF4C6FFF,
                new String[]{"Headline", "Details"}));
        list.add(new Template("qa", "Q&A Box",
                "Ask your followers anything", "Q&A", 0xFFFF8A00,
                new String[]{"Question", "Answer"}));
        list.add(new Template("throwback", "Throwback",
                "Revisit an old favourite", "THROWBACK", 0xFF1F9D75,
                new String[]{"Year", "Memory"}));
        TEMPLATES = Collections.unmodifiableList(list);
    }

    private TemplateCatalog() {}

    public static List<Template> all() {
        return TEMPLATES;
    }

    public static Template byId(String id) {
        if (id == null) return TEMPLATES.get(0);
        for (Template template : TEMPLATES) {
            if (template.id.equals(id)) return template;
        }
        return TEMPLATES.get(0);
    }
}
