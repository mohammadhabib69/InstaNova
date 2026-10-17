package com.insta.nova.ui.chat;

/**
 * PAGE 17 — data shown by the conversation intro block.
 *
 * <p>Every optional line is nullable: a hidden line is {@code null}, never an empty
 * string, so the item layout can collapse it without measuring.
 */
public class ChatIntro {

    public String fullName = "";
    public String username = "";
    public String avatarUrl = "";
    public int followers;
    public int posts;

    /** "You follow each other on Instagram" / "You follow x" / "x follows you". */
    public String followLine;
    /** "You both follow a and N others" — only when mutual_followings() exists. */
    public String mutualLine;

    public boolean isEmpty() {
        return (fullName == null || fullName.isEmpty())
                && (username == null || username.isEmpty());
    }

    public String displayName() {
        if (fullName != null && !fullName.trim().isEmpty()) return fullName;
        if (username != null && !username.isEmpty()) return username;
        return com.insta.nova.util.Constants.APP_NAME + " user";
    }
}
