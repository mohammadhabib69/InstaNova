package com.insta.nova.ui.story2;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

/**
 * Builds the {@code stories.editor_meta} payload.
 *
 * <p>Photo stories are flattened, so the meta is a record of what was baked. Video stories
 * are uploaded as the original file, so the same meta is what the viewer needs to reproduce
 * the trim, the mute state and the text elements at their normalized transforms.
 */
final class StoryMeta {

    private StoryMeta() { }

    static String forPhoto(boolean fit, int filter, List<StoryEditorActivity.El> elements,
                           String pollQuestion, List<String> pollOptions) {
        try {
            JSONObject root = new JSONObject();
            root.put("v", 1);
            root.put("kind", "photo");
            root.put("fit", fit);
            root.put("filter", filter);
            root.put("elements", elements(elements));
            putPoll(root, pollQuestion, pollOptions);
            return root.toString();
        } catch (Exception e) {
            return "{\"v\":1,\"kind\":\"photo\"}";
        }
    }

    static String forVideo(long trimStart, long trimEnd, boolean muted,
                           boolean fit, int filter, List<StoryEditorActivity.El> elements,
                           String overlayUrl, String pollQuestion, List<String> pollOptions) {
        try {
            JSONObject root = new JSONObject();
            root.put("v", 1);
            root.put("kind", "video");
            root.put("fit", fit);
            root.put("filter", filter);
            root.put("trimStartMs", trimStart);
            root.put("trimEndMs", trimEnd);
            root.put("muted", muted);
            if (overlayUrl != null) root.put("overlay", overlayUrl);
            root.put("elements", elements(elements));
            putPoll(root, pollQuestion, pollOptions);
            return root.toString();
        } catch (Exception e) {
            return "{\"v\":1,\"kind\":\"video\"}";
        }
    }

    private static void putPoll(JSONObject root, String question, List<String> options)
            throws org.json.JSONException {
        if (question == null || options == null || options.size() < 2) return;
        JSONObject poll = new JSONObject();
        poll.put("question", question);
        JSONArray arr = new JSONArray();
        for (String option : options) arr.put(option);
        poll.put("options", arr);
        root.put("poll", poll);
    }

    private static JSONArray elements(List<StoryEditorActivity.El> list) {
        JSONArray arr = new JSONArray();
        if (list == null) return arr;
        for (StoryEditorActivity.El el : list) {
            try {
                JSONObject o = new JSONObject();
                boolean gif = el.gif != null;
                o.put("type", gif ? "gif" : "text");
                if (gif) o.put("gif", el.gif);
                o.put("text", el.text == null ? "" : el.text);
                o.put("x", el.cx);
                o.put("y", el.cy);
                o.put("scale", el.scale);
                o.put("rot", el.rot);
                o.put("color", el.color);
                o.put("bg", el.bg);
                o.put("font", el.font);
                o.put("sp", el.sp);
                arr.put(o);
            } catch (Exception ignored) {
            }
        }
        return arr;
    }
}
