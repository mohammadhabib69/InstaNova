package com.insta.nova.hub;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.insta.nova.R;
import com.insta.nova.campus.NovaCampusActivity;
import com.insta.nova.data.NovaBadgeStore;
import com.insta.nova.data.NovaDtos;
import com.insta.nova.insights.NovaInsightsActivity;
import com.insta.nova.lite.NovaLiteActivity;
import com.insta.nova.safe.NovaSafeActivity;
import com.insta.nova.sos.NovaSosActivity;
import com.insta.nova.study.NovaStudyActivity;
import com.insta.nova.ui.nova.NovaComposerActivity;

/**
 * The sheet behind the Nova top-bar '+': one Nova-post entry plus every Nova tool.
 *
 * <p>Deliberately Nova-only. Insta posting lives on the Home '+' — mixing the two
 * spaces in this list is what made the tab feel like a second Instagram.
 *
 * <p>Presentation only — each row launches a screen that already exists. Module rows
 * carry a live one-line summary from the hub payload.
 */
public final class NovaSheet {

    private NovaSheet() {
    }

    /** The create menu: posting plus the four everyday spaces. */
    public static void showCreate(Fragment fragment, NovaDtos.HubSummary summary) {
        show(fragment, summary, false);
    }

    /** The tools menu: insights, safe and lite. */
    public static void showTools(Fragment fragment, NovaDtos.HubSummary summary) {
        show(fragment, summary, true);
    }

    private static void show(Fragment fragment, NovaDtos.HubSummary summary, boolean tools) {
        if (fragment == null || !fragment.isAdded()) return;
        Context context = fragment.requireContext();

        BottomSheetDialog dialog = new BottomSheetDialog(context, R.style.InstaNovaBottomSheetDialog);
        View sheet = LayoutInflater.from(context).inflate(R.layout.sheet_nova_create, null);
        dialog.setContentView(sheet);

        // Edge-to-edge: without the navigation-bar inset the last tool row sits
        // flush against the gesture pill and looks cut off.
        final int basePadding = sheet.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(sheet, (v, insets) -> {
            int bottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(),
                    basePadding + bottom);
            return insets;
        });

        // -------------------------------------------------------- nova posting
        // Nova is a separate space: this is the only create action here, and it
        // writes nova_posts, so the post never appears on the Insta home feed.
        bind(dialog, sheet, R.id.row_new_nova_post, () ->
                NovaComposerActivity.start(context));

        // ---------------------------------------------------------------- tools
        bind(dialog, sheet, R.id.row_nova_sos, () ->
                context.startActivity(new Intent(context, NovaSosActivity.class)));
        bind(dialog, sheet, R.id.row_nova_safe, () ->
                context.startActivity(new Intent(context, NovaSafeActivity.class)));
        bind(dialog, sheet, R.id.row_nova_campus, () ->
                context.startActivity(new Intent(context, NovaCampusActivity.class)));
        bind(dialog, sheet, R.id.row_nova_study, () ->
                context.startActivity(new Intent(context, NovaStudyActivity.class)));
        bind(dialog, sheet, R.id.row_nova_lite, () ->
                context.startActivity(new Intent(context, NovaLiteActivity.class)));
        bind(dialog, sheet, R.id.row_nova_insights, () ->
                context.startActivity(new Intent(context, NovaInsightsActivity.class)));

        // One layout, two menus. Hiding the rows keeps the subtitles inside them out of
        // the way too, so the create menu shows exactly four entries.
        if (tools) {
            hide(sheet, R.id.row_new_nova_post, R.id.row_nova_campus, R.id.row_nova_study,
                    R.id.row_nova_sos);
            hide(sheet, R.id.hdr_nova_create, R.id.hdr_nova_community);
        } else {
            hide(sheet, R.id.row_nova_insights, R.id.row_nova_safe, R.id.row_nova_lite);
            // Section captions belong to the rows under them, so the create menu must not
            // leave YOU and SETTINGS sitting above nothing.
            hide(sheet, R.id.hdr_nova_you, R.id.hdr_nova_settings);
        }

        bindSummary(sheet, summary);

        dialog.show();
    }

    private static void hide(View sheet, int... ids) {
        for (int id : ids) {
            View row = sheet.findViewById(id);
            if (row != null) row.setVisibility(View.GONE);
        }
    }

    private static void bind(BottomSheetDialog dialog, View sheet, int id, Runnable action) {
        View row = sheet.findViewById(id);
        if (row == null) return;
        row.setOnClickListener(v -> {
            dialog.dismiss();
            action.run();
        });
    }

    private static void bindSummary(View sheet, NovaDtos.HubSummary summary) {
        if (summary == null) return;

        setSubtitle(sheet, R.id.sub_nova_sos, summary.sosOpen > 0
                ? summary.sosOpen + " open request" + (summary.sosOpen == 1 ? "" : "s")
                + (summary.sosCritical > 0 ? " · " + summary.sosCritical + " critical" : "")
                : "No open requests near you");

        setSubtitle(sheet, R.id.sub_nova_safe, summary.safeEvents7d > 0
                ? summary.safeEvents7d + " checks this week"
                : "Guard your words and feed");

        setSubtitle(sheet, R.id.sub_nova_campus, summary.campusJoined
                ? (summary.eventsNext7d > 0
                    ? summary.eventsNext7d + " events in the next 7 days"
                    : "Your campus is connected")
                : "Join your university");

        setSubtitle(sheet, R.id.sub_nova_study, summary.studyTodayMinutes > 0
                ? summary.studyTodayMinutes + " min focused today"
                : "Focus, streak and Q&A");

        setSubtitle(sheet, R.id.sub_nova_insights, "See how you grow");
    }

    private static void setSubtitle(View root, int id, String text) {
        TextView view = root.findViewById(id);
        if (view == null) return;
        if (text == null || text.trim().isEmpty()) {
            view.setVisibility(View.GONE);
            return;
        }
        view.setText(text);
        view.setVisibility(View.VISIBLE);
    }

}
