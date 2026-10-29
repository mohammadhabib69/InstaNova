package com.insta.nova.ui.settings;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import com.insta.nova.R;

/**
 * Help Center — expandable FAQ about using InstaNova. Static content, real
 * expand/collapse behaviour, no dead chevrons.
 */
public class HelpCenterActivity extends SettingsPageActivity {

    private static final String[][] FAQ = {
            {"How do I make my account private?",
                    "Open Settings and activity → Account privacy, then turn on Private account. "
                            + "New followers will need your approval before they can see your posts and stories."},
            {"Who can message me?",
                    "Messages from people you don't follow arrive in Message Requests. You can change who is "
                            + "allowed to send requests in Settings → Message request controls."},
            {"How do I hide my story from someone?",
                    "Go to Settings → Story hide and add the person to your hide list. They won't see any "
                            + "story you post while they're on that list."},
            {"What happens when I block someone?",
                    "They can't find your profile, message you or see your posts. Blocking is private: the other "
                            + "person isn't notified. You can unblock them from Settings → Blocked accounts."},
            {"How do I delete a post?",
                    "Open the post, tap the three dots, and choose Delete. You can also archive a post instead: "
                            + "archived posts leave your profile but stay in Settings → Archive."},
            {"Why can't I see someone's posts?",
                    "Their account may be private and you may not be an approved follower yet, or they may have "
                            + "restricted or blocked you."},
            {"How do I change my password?",
                    "Settings → Velkyn Account → Change password. You can send yourself a reset email or change it "
                            + "in the app by confirming your current password."},
            {"How do I download my information?",
                    "Settings → Download your data creates a JSON file with your profile, posts, saved posts and a "
                            + "call history summary, then opens the Android share sheet."},
            {"What does Data Saver do?",
                    "Data Saver loads images at a lower quality and stops videos from autoplaying on mobile data. "
                            + "You can turn it off in Settings → Data Saver."},
            {"How do I report a problem?",
                    "Settings → Report a problem. Describe what happened and pick a category; reports go straight "
                            + "to the InstaNova team."}
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setPageTitle(R.string.st_help_title);
        addParagraph("Answers to common questions about using InstaNova.");

        for (String[] item : FAQ) {
            final TextView[] answer = new TextView[1];
            View row = addRow(item[0], null, R.drawable.ic_help, v -> {
                if (answer[0] == null) return;
                answer[0].setVisibility(answer[0].getVisibility() == View.VISIBLE
                        ? View.GONE : View.VISIBLE);
            });
            TextView body = addParagraph(item[1]);
            body.setVisibility(View.GONE);
            answer[0] = body;
            // Keep the row above its answer when the list is rebuilt.
            row.setTag(body);
        }
    }
}
