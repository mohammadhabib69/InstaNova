package com.insta.nova.ui.chat;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import java.util.ArrayList;
import java.util.List;

public class ChatTheme {

    public final String id;
    public final String name;
    public final int backgroundColor;
    public final int sentBubbleStart;
    public final int sentBubbleEnd;
    public final int receivedBubbleColor;
    public final int previewColor1;
    public final int previewColor2;

    public ChatTheme(String id, String name, int backgroundColor,
                     int sentBubbleStart, int sentBubbleEnd,
                     int receivedBubbleColor, int previewColor1, int previewColor2) {
        this.id = id;
        this.name = name;
        this.backgroundColor = backgroundColor;
        this.sentBubbleStart = sentBubbleStart;
        this.sentBubbleEnd = sentBubbleEnd;
        this.receivedBubbleColor = receivedBubbleColor;
        this.previewColor1 = previewColor1;
        this.previewColor2 = previewColor2;
    }

    public GradientDrawable createSentBubbleDrawable(float cornerRadiusPx) {
        GradientDrawable gd = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{sentBubbleStart, sentBubbleEnd});
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setCornerRadius(cornerRadiusPx);
        return gd;
    }

    public GradientDrawable createReceivedBubbleDrawable(float cornerRadiusPx) {
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setColor(receivedBubbleColor);
        gd.setCornerRadius(cornerRadiusPx);
        return gd;
    }

    public static List<ChatTheme> getThemes() {
        List<ChatTheme> list = new ArrayList<>();
        list.add(new ChatTheme(
                "default",
                "Default",
                Color.parseColor("#000000"),
                Color.parseColor("#3797F0"),
                Color.parseColor("#7B3FE4"),
                Color.parseColor("#262626"),
                Color.parseColor("#3797F0"),
                Color.parseColor("#7B3FE4")
        ));
        list.add(new ChatTheme(
                "berry",
                "Berry",
                Color.parseColor("#120517"),
                Color.parseColor("#FA2D83"),
                Color.parseColor("#9B51E0"),
                Color.parseColor("#2A162F"),
                Color.parseColor("#FA2D83"),
                Color.parseColor("#9B51E0")
        ));
        list.add(new ChatTheme(
                "ocean",
                "Ocean",
                Color.parseColor("#040D1A"),
                Color.parseColor("#00D2FF"),
                Color.parseColor("#3A7BD5"),
                Color.parseColor("#152233"),
                Color.parseColor("#00D2FF"),
                Color.parseColor("#3A7BD5")
        ));
        list.add(new ChatTheme(
                "sunset",
                "Sunset",
                Color.parseColor("#14080B"),
                Color.parseColor("#FA709A"),
                Color.parseColor("#FEE140"),
                Color.parseColor("#2A181C"),
                Color.parseColor("#FA709A"),
                Color.parseColor("#FEE140")
        ));
        list.add(new ChatTheme(
                "lavender",
                "Lavender",
                Color.parseColor("#0F071A"),
                Color.parseColor("#B621FE"),
                Color.parseColor("#6A11CB"),
                Color.parseColor("#251833"),
                Color.parseColor("#B621FE"),
                Color.parseColor("#6A11CB")
        ));
        list.add(new ChatTheme(
                "mint",
                "Mint",
                Color.parseColor("#051412"),
                Color.parseColor("#00B09B"),
                Color.parseColor("#96C93D"),
                Color.parseColor("#142824"),
                Color.parseColor("#00B09B"),
                Color.parseColor("#96C93D")
        ));
        list.add(new ChatTheme(
                "monochrome",
                "Monochrome",
                Color.parseColor("#000000"),
                Color.parseColor("#333333"),
                Color.parseColor("#333333"),
                Color.parseColor("#1F1F1F"),
                Color.parseColor("#333333"),
                Color.parseColor("#1F1F1F")
        ));
        return list;
    }

    public static ChatTheme findById(String id) {
        if (id != null) {
            for (ChatTheme t : getThemes()) {
                if (t.id.equalsIgnoreCase(id)) return t;
            }
        }
        return getThemes().get(0);
    }
}
