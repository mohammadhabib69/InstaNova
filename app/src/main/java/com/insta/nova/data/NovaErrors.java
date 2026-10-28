package com.insta.nova.data;

import android.content.Context;

import com.insta.nova.R;

import org.json.JSONObject;

import retrofit2.Response;

/** Maps the backend's stable error codes to human, localised messages. */
public final class NovaErrors {

    private NovaErrors() {
    }

    /** Extracts the PostgREST {@code message} (which equals the documented code). */
    public static String code(Response<?> response) {
        if (response == null) return "network";
        try {
            if (response.errorBody() != null) {
                String raw = response.errorBody().string();
                JSONObject object = new JSONObject(raw);
                String message = object.optString("message", "");
                if (!message.isEmpty()) return message;
            }
        } catch (Exception ignored) {
        }
        return "http_" + response.code();
    }

    public static String friendly(Context context, String code) {
        if (code == null || code.isEmpty()) return context.getString(R.string.nova_error);
        switch (code) {
            case "not_authenticated":
                return context.getString(R.string.nova_err_not_authenticated);
            case "invalid_phone":
                return context.getString(R.string.nova_sos_invalid_phone);
            case "invalid_time":
                return context.getString(R.string.nova_sos_invalid_time);
            case "rate_limited":
                return context.getString(R.string.nova_sos_rate_limited);
            case "request_closed":
                return context.getString(R.string.nova_sos_request_closed);
            case "own_request":
                return context.getString(R.string.nova_err_own_request);
            case "donor_profile_required":
                return context.getString(R.string.nova_sos_donor_required);
            case "donor_unavailable":
                return context.getString(R.string.nova_err_donor_unavailable);
            case "donor_not_eligible":
                return context.getString(R.string.nova_err_donor_ineligible);
            case "blood_group_mismatch":
                return context.getString(R.string.nova_sos_mismatch);
            case "not_allowed":
                return context.getString(R.string.nova_err_not_allowed);
            case "pledge_not_found":
                return context.getString(R.string.nova_err_pledge_missing);
            case "verified_student_required":
                return context.getString(R.string.nova_campus_verified_needed);
            case "too_short":
                return context.getString(R.string.nova_campus_confess_short);
            case "not_found":
                return context.getString(R.string.nova_err_not_found);
            case "own_content":
                return context.getString(R.string.nova_study_own_vote);
            case "network":
                return context.getString(R.string.nova_offline);
            default:
                return context.getString(R.string.nova_error);
        }
    }
}
