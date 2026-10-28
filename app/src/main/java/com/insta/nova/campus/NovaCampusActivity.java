package com.insta.nova.campus;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.insta.nova.R;
import com.insta.nova.data.CampusRepository;
import com.insta.nova.data.NovaDtos;
import com.insta.nova.databinding.ActivityNovaCampusBinding;
import com.insta.nova.hub.NovaScreen;
import com.insta.nova.util.Resource;
import com.insta.nova.util.TimeUtils;
import com.insta.nova.widget.NovaUi;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Locale;

/**
 * Campus membership and the event board.
 *
 * <p>Joining is real: a row is written through {@code nova_campus_join} and the verified
 * flag comes back from the server (a university email domain is what grants it).
 */
public class NovaCampusActivity extends AppCompatActivity {

    private ActivityNovaCampusBinding binding;
    private CampusRepository repository;
    private final List<NovaDtos.University> universities = new ArrayList<>();
    private Integer selectedUniversityId;
    private boolean thisWeekOnly;
    private boolean member;
    private int memberUniversityId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityNovaCampusBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        NovaScreen.bindHeader(this, R.drawable.ic_nova_campus,
                R.string.nova_campus_title, R.color.nova_campus);

        repository = new CampusRepository(this);
        binding.etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) {
                loadUniversities(s.toString().trim());
            }
        });
        binding.btnJoin.setOnClickListener(v -> join());
        binding.btnLeave.setOnClickListener(v -> leave());
        binding.btnThisWeek.setOnClickListener(v -> {
            thisWeekOnly = !thisWeekOnly;
            renderFilter();
            loadEvents();
        });
        binding.btnCreateEvent.setOnClickListener(v -> createEvent());
        binding.btnLostFound.setOnClickListener(v ->
                startActivity(new android.content.Intent(this, CampusLostActivity.class)));
        binding.btnConfessions.setOnClickListener(v ->
                startActivity(new android.content.Intent(this, CampusConfessionsActivity.class)));

        renderFilter();
        loadMembership();
        loadUniversities("");
    }

    private void renderFilter() {
        binding.btnThisWeek.setBackgroundResource(thisWeekOnly
                ? R.drawable.bg_nova_chip_on : R.drawable.bg_nova_chip);
        binding.btnThisWeek.setTextColor(getColor(thisWeekOnly
                ? R.color.color_background : R.color.nova_text_3));
    }

    private void loadMembership() {
        repository.membership().observe(this, resource -> {
            if (binding == null || resource == null) return;
            if (resource.status == Resource.Status.LOADING) return;
            List<NovaDtos.CampusMembership> rows = resource.data;
            NovaDtos.CampusMembership membership =
                    rows == null || rows.isEmpty() ? null : rows.get(0);
            member = membership != null && membership.universityId > 0;
            memberUniversityId = member ? membership.universityId : 0;
            binding.joinPanel.setVisibility(member ? View.GONE : View.VISIBLE);
            binding.memberPanel.setVisibility(member ? View.VISIBLE : View.GONE);
            if (!member) return;
            binding.tvUniversity.setText(nameFor(membership.universityId));
            String dept = membership.department == null ? "" : membership.department;
            String batch = membership.batch == null ? "" : membership.batch;
            binding.tvDept.setText(dept.isEmpty() && batch.isEmpty()
                    ? getString(R.string.nova_campus_title)
                    : (dept + (batch.isEmpty() ? "" : " · " + batch)).trim());
            binding.tvVerified.setText(membership.verified
                    ? R.string.nova_campus_verified : R.string.nova_campus_not_verified);
            binding.tvVerifiedNote.setVisibility(membership.verified ? View.GONE : View.VISIBLE);
            // A badge that cannot be acted on is just a complaint. Both the badge and the
            // note below it open the same email prompt, and the note reads as an action so
            // there is something obvious to tap when the account is not verified yet.
            binding.tvVerified.setClickable(!membership.verified);
            binding.tvVerified.setOnClickListener(membership.verified ? null : v -> askForStudentEmail());
            if (!membership.verified) {
                binding.tvVerifiedNote.setText(R.string.nova_campus_add_email_cta);
                binding.tvVerifiedNote.setTextColor(getColor(R.color.nova_cyan));
                binding.tvVerifiedNote.setClickable(true);
                binding.tvVerifiedNote.setOnClickListener(v -> askForStudentEmail());
            } else {
                binding.tvVerifiedNote.setOnClickListener(null);
            }
            loadEvents();
        });
    }

    private void loadUniversities(String search) {
        repository.universities(search).observe(this, resource -> {
            if (binding == null || resource == null) return;
            if (resource.status != Resource.Status.SUCCESS || resource.data == null) return;
            universities.clear();
            universities.addAll(resource.data);
            renderUniversities();
            // The membership row arrives before the directory; resolve the name once it does.
            if (member) binding.tvUniversity.setText(nameFor(memberUniversityId));
        });
    }

    private void renderUniversities() {
        binding.universityList.removeAllViews();
        if (universities.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(R.string.nova_campus_no_universities);
            empty.setTextColor(getColor(R.color.nova_text_3));
            empty.setTextSize(12f);
            binding.universityList.addView(empty);
            return;
        }
        for (NovaDtos.University university : universities) {
            boolean selected = selectedUniversityId != null
                    && selectedUniversityId == university.id;
            TextView row = new TextView(this);
            row.setText(university.shortName == null || university.shortName.isEmpty()
                    ? university.name : university.shortName + " · " + university.name);
            row.setTextSize(13f);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackgroundResource(selected
                    ? R.drawable.bg_nova_chip_on : R.drawable.bg_nova_card);
            row.setTextColor(getColor(selected ? R.color.color_background : R.color.nova_text_2));
            row.setPadding(NovaUi.dp(this, 14), NovaUi.dp(this, 14),
                    NovaUi.dp(this, 14), NovaUi.dp(this, 14));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            params.bottomMargin = NovaUi.dp(this, 6);
            row.setLayoutParams(params);
            row.setOnClickListener(v -> {
                selectedUniversityId = university.id;
                renderUniversities();
            });
            binding.universityList.addView(row);
        }
    }

    private String nameFor(int universityId) {
        for (NovaDtos.University university : universities) {
            if (university.id == universityId) {
                return university.name;
            }
        }
        return getString(R.string.nova_campus_title);
    }

    private void join() {
        binding.tvJoinError.setVisibility(View.GONE);
        if (selectedUniversityId == null) {
            joinError(getString(R.string.nova_campus_choose));
            return;
        }
        binding.btnJoin.setEnabled(false);
        repository.join(selectedUniversityId,
                        text(binding.etDepartment), text(binding.etBatch))
                .observe(this, resource -> {
                    if (binding == null || resource == null) return;
                    if (resource.status == Resource.Status.LOADING) return;
                    binding.btnJoin.setEnabled(true);
                    if (resource.status == Resource.Status.SUCCESS) {
                        NovaUi.haptic(binding.getRoot(), true);
                        loadMembership();
                    } else {
                        joinError(getString(R.string.nova_error)
                                + (resource.message == null ? "" : " (" + resource.message + ")"));
                    }
                });
    }

    private String text(EditText input) {
        return input.getText() == null ? "" : input.getText().toString().trim();
    }

    private void leave() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.nova_campus_leave)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.nova_campus_leave, (d, w) ->
                        repository.leave().observe(this, resource -> {
                            if (resource != null && resource.status == Resource.Status.SUCCESS) {
                                member = false;
                                binding.memberPanel.setVisibility(View.GONE);
                                binding.joinPanel.setVisibility(View.VISIBLE);
                                binding.eventList.removeAllViews();
                            }
                        }))
                .show();
    }

    private void loadEvents() {
        if (!member) return;
        repository.events(thisWeekOnly, null).observe(this, resource -> {
            if (binding == null || resource == null) return;
            if (resource.status != Resource.Status.SUCCESS || resource.data == null) return;
            renderEvents(resource.data);
        });
    }

    private void renderEvents(List<NovaDtos.EventItem> events) {
        binding.eventList.removeAllViews();
        binding.tvNoEvents.setVisibility(events.isEmpty() ? View.VISIBLE : View.GONE);
        for (NovaDtos.EventItem event : events) {
            binding.eventList.addView(eventRow(event));
        }
    }

    private View eventRow(NovaDtos.EventItem event) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_nova_card);
        card.setPadding(NovaUi.dp(this, 16), NovaUi.dp(this, 14),
                NovaUi.dp(this, 16), NovaUi.dp(this, 14));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = NovaUi.dp(this, 10);
        card.setLayoutParams(params);

        TextView title = new TextView(this);
        title.setText(event.title == null ? "" : event.title);
        title.setTextColor(getColor(R.color.nova_text));
        title.setTextSize(15f);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        card.addView(title);

        TextView meta = new TextView(this);
        StringBuilder line = new StringBuilder();
        if (event.startsAt != null) {
            long start = TimeUtils.parseTimestamp(event.startsAt);
            if (start > 0) {
                line.append(new SimpleDateFormat("d MMM · h:mm a", Locale.getDefault())
                        .format(new Date(start)));
            }
        }
        if (event.venue != null && !event.venue.isEmpty()) {
            if (line.length() > 0) line.append(" · ");
            line.append(event.venue);
        }
        meta.setText(line);
        meta.setTextColor(getColor(R.color.nova_text_3));
        meta.setTextSize(12f);
        card.addView(meta);

        TextView counts = new TextView(this);
        counts.setText(getString(R.string.nova_campus_going, event.goingCount,
                event.interestedCount));
        counts.setTextColor(getColor(R.color.nova_campus));
        counts.setTextSize(12f);
        card.addView(counts);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        actionParams.topMargin = NovaUi.dp(this, 10);
        actions.setLayoutParams(actionParams);

        actions.addView(rsvpChip(R.string.nova_campus_rsvp_going, "going", event));
        actions.addView(rsvpChip(R.string.nova_campus_rsvp_interested, "interested", event));
        card.addView(actions);
        return card;
    }

    private View rsvpChip(int label, String status, NovaDtos.EventItem event) {
        boolean active = status.equals(event.myRsvp);
        TextView chip = new TextView(this);
        chip.setText(label);
        chip.setTextSize(12f);
        chip.setGravity(Gravity.CENTER);
        chip.setBackgroundResource(active ? R.drawable.bg_nova_chip_on : R.drawable.bg_nova_chip);
        chip.setTextColor(getColor(active ? R.color.color_background : R.color.nova_text_3));
        chip.setPadding(NovaUi.dp(this, 14), NovaUi.dp(this, 8),
                NovaUi.dp(this, 14), NovaUi.dp(this, 8));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMarginEnd(NovaUi.dp(this, 8));
        chip.setLayoutParams(params);
        chip.setOnClickListener(v -> repository.rsvp(event.id, active ? "none" : status)
                .observe(this, resource -> {
                    if (resource != null && resource.status == Resource.Status.SUCCESS) {
                        loadEvents();
                    }
                }));
        return chip;
    }

    /** Values accepted by nova_events_category_check, in display order. */
    private static final String[] EVENT_CATEGORIES =
            {"academic", "cultural", "sports", "tech", "club", "other"};

    /** Human label for a stored category key. */
    private String categoryLabel(String key) {
        switch (key) {
            case "academic": return getString(R.string.nova_campus_cat_academic);
            case "cultural": return getString(R.string.nova_campus_cat_cultural);
            case "sports": return getString(R.string.nova_campus_cat_sports);
            case "tech": return getString(R.string.nova_campus_cat_tech);
            case "club": return getString(R.string.nova_campus_cat_club);
            default: return getString(R.string.nova_campus_cat_other);
        }
    }

    /**
     * Runs the university-email check again and explains the outcome either way.
     *
     * <p>Verification is an email-domain match against the university's own domains; it
     * cannot be granted by tapping. Re-checking is what makes the badge honest: the user
     * learns whether their account email qualifies, and what the university accepts.
     */
    /**
     * Asks for the student mailbox and verifies it server-side.
     *
     * <p>The rule: the domain after the @ must be exactly "stud." + the university domain,
     * which is how these mailboxes are issued (…@stud.kuet.ac.bd).
     */
    private void toast(String message) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show();
    }

    private void askForStudentEmail() {
        android.widget.EditText input = new android.widget.EditText(this);
        input.setHint(getString(R.string.nova_campus_email_hint));
        input.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        int pad = Math.round(20 * getResources().getDisplayMetrics().density);
        input.setPadding(pad, pad, pad, pad);

        new AlertDialog.Builder(this)
                .setTitle(R.string.nova_campus_verify_email_title)
                .setMessage(R.string.nova_campus_verify_email_body)
                .setView(input)
                .setNegativeButton(R.string.nova_campus_cancel, null)
                .setPositiveButton(R.string.nova_campus_verify_action, (d, w) -> {
                    String email = input.getText().toString().trim();
                    if (email.isEmpty()) return;
                    submitStudentEmail(email);
                })
                .show();
    }

    private void submitStudentEmail(String email) {
        repository.verifyEmail(email).observe(this, resource -> {
            if (resource == null) return;
            if (resource.status == Resource.Status.ERROR) {
                toast(getString(R.string.nova_campus_verify_failed, String.valueOf(resource.message)));
                return;
            }
            if (resource.status != Resource.Status.SUCCESS || resource.data == null) return;
            Map<String, Object> data = resource.data;
            boolean verified = Boolean.TRUE.equals(data.get("verified"));
            if (verified) {
                toast(getString(R.string.nova_campus_verified_ok));
                loadMembership();
                return;
            }
            Object expected = data.get("expected");
            String list = expected instanceof java.util.List
                    ? TextUtils.join(", ", (java.util.List<?>) expected)
                    : String.valueOf(expected);
            toast(getString(R.string.nova_campus_verify_domain, list));
        });
    }

    private void recheckVerification(NovaDtos.CampusMembership membership) {
        String dept = membership.department == null ? null : membership.department;
        String batch = membership.batch == null ? null : membership.batch;
        repository.join(membership.universityId, dept, batch).observe(this, resource -> {
            if (binding == null || resource == null) return;
            if (resource.status == Resource.Status.LOADING) return;
            boolean verified = resource.status == Resource.Status.SUCCESS
                    && resource.data != null && resource.data.verified;
            if (verified) {
                binding.tvVerified.setText(R.string.nova_campus_verified);
                binding.tvVerified.setClickable(false);
                binding.tvVerifiedNote.setVisibility(View.GONE);
                loadMembership();
                return;
            }
            String domains = domainsFor(membership.universityId);
            String email = com.google.firebase.auth.FirebaseAuth.getInstance()
                    .getCurrentUser() == null ? "" : String.valueOf(
                    com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getEmail());
            String message = getString(R.string.nova_campus_verify_failed, email, domains);
            new AlertDialog.Builder(this)
                    .setTitle(R.string.nova_campus_not_verified)
                    .setMessage(message)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
        });
    }

    /** Comma-joined list of the domains this university accepts. */
    private String domainsFor(int universityId) {
        for (NovaDtos.University university : universities) {
            if (university.id == universityId && university.emailDomains != null
                    && !university.emailDomains.isEmpty()) {
                return TextUtils.join(", ", university.emailDomains);
            }
        }
        return getString(R.string.nova_campus_verify_domains_unknown);
    }

    private void createEvent() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        int pad = NovaUi.dp(this, 20);
        form.setPadding(pad, pad, pad, 0);

        EditText title = dialogField(form, getString(R.string.nova_campus_event_title));
        EditText venue = dialogField(form, getString(R.string.nova_campus_venue));
        EditText details = dialogField(form, getString(R.string.nova_campus_details));

        // nova_events.category is NOT NULL with a fixed vocabulary, and the dialog used
        // to pass null — so every event insert failed on a constraint the user could
        // not see or influence.
        final String[] category = {"other"};
        TextView categoryButton = new TextView(this);
        categoryButton.setTextColor(getColor(R.color.nova_cyan));
        categoryButton.setTextSize(13f);
        categoryButton.setPadding(0, NovaUi.dp(this, 10), 0, 0);
        categoryButton.setText(getString(R.string.nova_campus_category) + " · "
                + categoryLabel(category[0]));
        form.addView(categoryButton);
        categoryButton.setOnClickListener(v -> {
            String[] keys = EVENT_CATEGORIES;
            String[] labels = new String[keys.length];
            for (int i = 0; i < keys.length; i++) labels[i] = categoryLabel(keys[i]);
            new AlertDialog.Builder(this)
                    .setTitle(R.string.nova_campus_category)
                    .setItems(labels, (d, which) -> {
                        category[0] = keys[which];
                        categoryButton.setText(getString(R.string.nova_campus_category)
                                + " · " + labels[which]);
                    })
                    .show();
        });

        final long[] start = {System.currentTimeMillis() + 24 * 60 * 60 * 1000L};
        TextView when = new TextView(this);
        when.setTextColor(getColor(R.color.nova_cyan));
        when.setTextSize(13f);
        when.setPadding(0, NovaUi.dp(this, 10), 0, 0);
        when.setText(getString(R.string.nova_campus_starts) + " · "
                + new SimpleDateFormat("d MMM · h:mm a", Locale.getDefault())
                .format(new Date(start[0])));
        form.addView(when);
        when.setOnClickListener(v -> {
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(start[0]);
            new DatePickerDialog(this, (view, year, month, day) -> {
                Calendar picked = Calendar.getInstance();
                picked.set(year, month, day, calendar.get(Calendar.HOUR_OF_DAY),
                        calendar.get(Calendar.MINUTE));
                start[0] = picked.getTimeInMillis();
                when.setText(getString(R.string.nova_campus_starts) + " · "
                        + new SimpleDateFormat("d MMM · h:mm a", Locale.getDefault())
                        .format(new Date(start[0])));
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH)).show();
        });

        new AlertDialog.Builder(this)
                .setTitle(R.string.nova_campus_create_event)
                .setView(form)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.nova_campus_create_event, (d, w) -> {
                    String titleText = text(title);
                    if (titleText.isEmpty()) {
                        joinError(getString(R.string.nova_campus_event_title));
                        return;
                    }
                    long end = start[0] + 2 * 60 * 60 * 1000L;
                    repository.createEvent(titleText, text(details), text(venue),
                                    TimeUtils.toSupabaseTimestamp(start[0]),
                                    TimeUtils.toSupabaseTimestamp(end), category[0], null,
                                    memberUniversityId)
                            .observe(this, resource -> {
                                if (resource != null && resource.status == Resource.Status.SUCCESS) {
                                    loadEvents();
                                } else if (resource != null) {
                                    joinError(getString(R.string.nova_error));
                                }
                            });
                })
                .show();
    }

    private EditText dialogField(LinearLayout parent, String hint) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setTextSize(14f);
        input.setTextColor(getColor(R.color.nova_text));
        input.setHintTextColor(getColor(R.color.nova_text_3));
        parent.addView(input);
        return input;
    }

    private void joinError(String message) {
        binding.tvJoinError.setText(message);
        binding.tvJoinError.setVisibility(View.VISIBLE);
        NovaUi.haptic(binding.getRoot(), false);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
