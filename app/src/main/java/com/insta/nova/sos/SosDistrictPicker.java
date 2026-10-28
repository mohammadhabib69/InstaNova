package com.insta.nova.sos;

import android.app.AlertDialog;
import android.content.Context;
import android.view.View;
import android.widget.EditText;

import com.insta.nova.R;
import com.insta.nova.data.NovaDtos;
import com.insta.nova.data.SosRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns the district field into a picker backed by {@code nova_districts}.
 *
 * <p>The column is a foreign key to that table, so a typed value only works when it
 * matches a row exactly — same spelling and same case. Typing "khulna" or "Khulna "
 * was answered with a 23503 foreign-key error the user could not act on. Picking from
 * the real list makes a mismatch impossible.
 */
final class SosDistrictPicker {

    private final Context context;
    private final EditText field;
    private final SosRepository repository;
    private final List<String> names = new ArrayList<>();
    private boolean loaded;

    private SosDistrictPicker(Context context, EditText field, SosRepository repository) {
        this.context = context;
        this.field = field;
        this.repository = repository;
    }

    static void attach(Context context, EditText field, SosRepository repository) {
        new SosDistrictPicker(context, field, repository).bind();
    }

    private void bind() {
        field.setFocusable(false);
        field.setCursorVisible(false);
        field.setOnClickListener(this::open);
        load();
    }

    private void load() {
        repository.districts().observeForever(resource -> {
            if (resource == null || resource.status != com.insta.nova.util.Resource.Status.SUCCESS
                    || resource.data == null) {
                return;
            }
            names.clear();
            for (NovaDtos.District district : resource.data) {
                if (district.name != null && !district.name.trim().isEmpty()) {
                    names.add(district.name);
                }
            }
            loaded = true;
        });
    }

    private void open(View view) {
        if (!loaded || names.isEmpty()) {
            // Loading or offline: let the user retry rather than typing a value that
            // the database will reject.
            new AlertDialog.Builder(context)
                    .setMessage(R.string.nova_sos_district_loading)
                    .setPositiveButton(android.R.string.ok, (d, w) -> load())
                    .show();
            return;
        }
        String[] items = names.toArray(new String[0]);
        new AlertDialog.Builder(context)
                .setTitle(R.string.nova_sos_district)
                .setItems(items, (d, which) -> field.setText(items[which]))
                .show();
    }
}
