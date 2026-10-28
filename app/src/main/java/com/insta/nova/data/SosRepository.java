package com.insta.nova.data;

import android.content.Context;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.gson.JsonElement;
import com.insta.nova.data.local.SessionManager;
import com.insta.nova.data.remote.RetrofitClient;
import com.insta.nova.util.Resource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Blood requests, pledges, donors and districts. */
public class SosRepository {

    private static final String TAG = "SosRepository";

    private final NovaApi api;
    private final SessionManager session;

    public SosRepository(Context context) {
        Context app = context.getApplicationContext();
        api = RetrofitClient.getInstance(app).getNovaApi();
        session = SessionManager.getInstance(app);
    }

    /* --------------------------------- feed --------------------------------- */

    public LiveData<Resource<List<NovaDtos.BloodRequest>>> feed(String group, String district,
                                                                int limit, int offset) {
        MutableLiveData<Resource<List<NovaDtos.BloodRequest>>> out = new MutableLiveData<>(Resource.loading());
        Map<String, Object> body = new HashMap<>();
        body.put("p_group", group == null || group.isEmpty() ? null : group);
        body.put("p_district", district == null || district.isEmpty() ? null : district);
        body.put("p_limit", limit);
        body.put("p_offset", offset);
        api.bloodFeed(body).enqueue(new Callback<List<NovaDtos.BloodRequest>>() {
            @Override public void onResponse(Call<List<NovaDtos.BloodRequest>> call,
                                             Response<List<NovaDtos.BloodRequest>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    out.postValue(Resource.success(response.body()));
                } else {
                    String code = NovaErrors.code(response);
                    Log.w(TAG, "feed failed " + code);
                    out.postValue(Resource.error(code));
                }
            }

            @Override public void onFailure(Call<List<NovaDtos.BloodRequest>> call, Throwable t) {
                out.postValue(Resource.error("network"));
            }
        });
        return out;
    }

    public LiveData<Resource<NovaDtos.CreateRequestResult>> createRequest(String group, int units,
                                                                          String patient, String hospital,
                                                                          String district, String area,
                                                                          String neededByIso, String urgency,
                                                                          String notes, String photoUrl,
                                                                          String phone) {
        MutableLiveData<Resource<NovaDtos.CreateRequestResult>> out = new MutableLiveData<>(Resource.loading());
        Map<String, Object> body = new HashMap<>();
        body.put("p_group", group);
        body.put("p_units", units);
        body.put("p_patient", safe(patient));
        body.put("p_hospital", hospital);
        body.put("p_district", district);
        body.put("p_area", safe(area));
        body.put("p_needed_by", neededByIso);
        body.put("p_urgency", urgency);
        body.put("p_notes", safe(notes));
        body.put("p_photo", photoUrl == null ? "" : photoUrl);
        body.put("p_phone", phone);
        api.createBloodRequest(body).enqueue(new Callback<NovaDtos.CreateRequestResult>() {
            @Override public void onResponse(Call<NovaDtos.CreateRequestResult> call,
                                             Response<NovaDtos.CreateRequestResult> response) {
                if (response.isSuccessful() && response.body() != null) {
                    out.postValue(Resource.success(response.body()));
                } else {
                    String code = NovaErrors.code(response);
                    Log.w(TAG, "create failed " + code);
                    out.postValue(Resource.error(code));
                }
            }

            @Override public void onFailure(Call<NovaDtos.CreateRequestResult> call, Throwable t) {
                out.postValue(Resource.error("network"));
            }
        });
        return out;
    }

    public LiveData<Resource<NovaDtos.PledgeResult>> pledge(String requestId) {
        MutableLiveData<Resource<NovaDtos.PledgeResult>> out = new MutableLiveData<>(Resource.loading());
        Map<String, Object> body = new HashMap<>();
        body.put("p_request", requestId);
        api.pledge(body).enqueue(new Callback<NovaDtos.PledgeResult>() {
            @Override public void onResponse(Call<NovaDtos.PledgeResult> call,
                                             Response<NovaDtos.PledgeResult> response) {
                if (response.isSuccessful() && response.body() != null) {
                    out.postValue(Resource.success(response.body()));
                } else {
                    out.postValue(Resource.error(NovaErrors.code(response)));
                }
            }

            @Override public void onFailure(Call<NovaDtos.PledgeResult> call, Throwable t) {
                out.postValue(Resource.error("network"));
            }
        });
        return out;
    }

    public LiveData<Resource<Boolean>> unpledge(String requestId) {
        MutableLiveData<Resource<Boolean>> out = new MutableLiveData<>(Resource.loading());
        Map<String, Object> body = new HashMap<>();
        body.put("p_request", requestId);
        api.unpledge(body).enqueue(new Callback<Void>() {
            @Override public void onResponse(Call<Void> call, Response<Void> response) {
                out.postValue(response.isSuccessful() ? Resource.success(true) : Resource.error(NovaErrors.code(response)));
            }
            @Override public void onFailure(Call<Void> call, Throwable t) {
                out.postValue(Resource.error("network"));
            }
        });
        return out;
    }

    public LiveData<Resource<NovaDtos.ConfirmResult>> confirmDonation(String requestId, String donorId) {
        MutableLiveData<Resource<NovaDtos.ConfirmResult>> out = new MutableLiveData<>(Resource.loading());
        Map<String, Object> body = new HashMap<>();
        body.put("p_request", requestId);
        body.put("p_donor", donorId);
        api.confirmDonation(body).enqueue(new Callback<NovaDtos.ConfirmResult>() {
            @Override public void onResponse(Call<NovaDtos.ConfirmResult> call,
                                             Response<NovaDtos.ConfirmResult> response) {
                if (response.isSuccessful() && response.body() != null) {
                    out.postValue(Resource.success(response.body()));
                } else {
                    out.postValue(Resource.error(NovaErrors.code(response)));
                }
            }
            @Override public void onFailure(Call<NovaDtos.ConfirmResult> call, Throwable t) {
                out.postValue(Resource.error("network"));
            }
        });
        return out;
    }

    public LiveData<Resource<Boolean>> setStatus(String requestId, String status) {
        MutableLiveData<Resource<Boolean>> out = new MutableLiveData<>(Resource.loading());
        Map<String, Object> body = new HashMap<>();
        body.put("p_request", requestId);
        body.put("p_status", status);
        api.setRequestStatus(body).enqueue(new Callback<Void>() {
            @Override public void onResponse(Call<Void> call, Response<Void> response) {
                out.postValue(response.isSuccessful() ? Resource.success(true) : Resource.error(NovaErrors.code(response)));
            }
            @Override public void onFailure(Call<Void> call, Throwable t) {
                out.postValue(Resource.error("network"));
            }
        });
        return out;
    }

    public LiveData<Resource<String>> contact(String requestId) {
        MutableLiveData<Resource<String>> out = new MutableLiveData<>(Resource.loading());
        Map<String, Object> body = new HashMap<>();
        body.put("p_request", requestId);
        api.requestContact(body).enqueue(new Callback<JsonElement>() {
            @Override public void onResponse(Call<JsonElement> call, Response<JsonElement> response) {
                if (response.isSuccessful() && response.body() != null) {
                    JsonElement value = response.body();
                    out.postValue(Resource.success(value.isJsonPrimitive()
                            ? value.getAsString() : value.toString()));
                } else {
                    out.postValue(Resource.error(NovaErrors.code(response)));
                }
            }
            @Override public void onFailure(Call<JsonElement> call, Throwable t) {
                out.postValue(Resource.error("network"));
            }
        });
        return out;
    }

    public LiveData<Resource<List<NovaDtos.Pledge>>> pledges(String requestId) {
        MutableLiveData<Resource<List<NovaDtos.Pledge>>> out = new MutableLiveData<>(Resource.loading());
        Map<String, Object> body = new HashMap<>();
        body.put("p_request", requestId);
        api.requestPledges(body).enqueue(new Callback<List<NovaDtos.Pledge>>() {
            @Override public void onResponse(Call<List<NovaDtos.Pledge>> call,
                                             Response<List<NovaDtos.Pledge>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    out.postValue(Resource.success(response.body()));
                } else {
                    out.postValue(Resource.error(NovaErrors.code(response)));
                }
            }
            @Override public void onFailure(Call<List<NovaDtos.Pledge>> call, Throwable t) {
                out.postValue(Resource.error("network"));
            }
        });
        return out;
    }

    public LiveData<Resource<Integer>> matchCount(String requestId) {
        MutableLiveData<Resource<Integer>> out = new MutableLiveData<>(Resource.loading());
        Map<String, Object> body = new HashMap<>();
        body.put("p_request", requestId);
        api.matchCount(body).enqueue(new Callback<Integer>() {
            @Override public void onResponse(Call<Integer> call, Response<Integer> response) {
                if (response.isSuccessful() && response.body() != null) {
                    out.postValue(Resource.success(response.body()));
                } else {
                    out.postValue(Resource.error(NovaErrors.code(response)));
                }
            }
            @Override public void onFailure(Call<Integer> call, Throwable t) {
                out.postValue(Resource.error("network"));
            }
        });
        return out;
    }

    /* ------------------------------- my lists -------------------------------- */

    public LiveData<Resource<List<NovaDtos.BloodRequest>>> myRequests() {
        MutableLiveData<Resource<List<NovaDtos.BloodRequest>>> out = new MutableLiveData<>(Resource.loading());
        Map<String, String> q = new HashMap<>();
        q.put("requester_id", "eq." + session.getUserId());
        q.put("order", "created_at.desc");
        q.put("select", "*");
        api.getMyBloodRequests(q).enqueue(new Callback<List<NovaDtos.BloodRequest>>() {
            @Override public void onResponse(Call<List<NovaDtos.BloodRequest>> call,
                                             Response<List<NovaDtos.BloodRequest>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    out.postValue(Resource.success(response.body()));
                } else {
                    out.postValue(Resource.error(NovaErrors.code(response)));
                }
            }
            @Override public void onFailure(Call<List<NovaDtos.BloodRequest>> call, Throwable t) {
                out.postValue(Resource.error("network"));
            }
        });
        return out;
    }

    public LiveData<Resource<List<NovaDtos.BloodRequest>>> myPledges() {
        MutableLiveData<Resource<List<NovaDtos.BloodRequest>>> out = new MutableLiveData<>(Resource.loading());
        Map<String, String> q = new HashMap<>();
        q.put("donor_id", "eq." + session.getUserId());
        q.put("select", "request_id,status,created_at");
        api.getMyPledges(q).enqueue(new Callback<List<Map<String, Object>>>() {
            @Override public void onResponse(Call<List<Map<String, Object>>> call,
                                             Response<List<Map<String, Object>>> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    out.postValue(Resource.error(NovaErrors.code(response)));
                    return;
                }
                Set<String> ids = new LinkedHashSet<>();
                for (Map<String, Object> row : response.body()) {
                    Object id = row.get("request_id");
                    if (id != null) ids.add(String.valueOf(id));
                }
                if (ids.isEmpty()) {
                    out.postValue(Resource.success(new ArrayList<>()));
                    return;
                }
                StringBuilder filter = new StringBuilder("in.(");
                int i = 0;
                for (String id : ids) {
                    if (i++ > 0) filter.append(",");
                    filter.append(id);
                }
                filter.append(")");
                Map<String, String> q2 = new HashMap<>();
                q2.put("id", filter.toString());
                q2.put("select", "*");
                api.getMyBloodRequests(q2).enqueue(new Callback<List<NovaDtos.BloodRequest>>() {
                    @Override public void onResponse(Call<List<NovaDtos.BloodRequest>> c2,
                                                     Response<List<NovaDtos.BloodRequest>> r2) {
                        if (r2.isSuccessful() && r2.body() != null) {
                            out.postValue(Resource.success(r2.body()));
                        } else {
                            out.postValue(Resource.error(NovaErrors.code(r2)));
                        }
                    }
                    @Override public void onFailure(Call<List<NovaDtos.BloodRequest>> c2, Throwable t) {
                        out.postValue(Resource.error("network"));
                    }
                });
            }
            @Override public void onFailure(Call<List<Map<String, Object>>> call, Throwable t) {
                out.postValue(Resource.error("network"));
            }
        });
        return out;
    }

    /* -------------------------------- donor ---------------------------------- */

    public LiveData<Resource<NovaDtos.Donor>> myDonor() {
        MutableLiveData<Resource<NovaDtos.Donor>> out = new MutableLiveData<>(Resource.loading());
        Map<String, String> q = new HashMap<>();
        q.put("user_id", "eq." + session.getUserId());
        q.put("select", "*");
        api.getDonor(q).enqueue(new Callback<List<NovaDtos.Donor>>() {
            @Override public void onResponse(Call<List<NovaDtos.Donor>> call, Response<List<NovaDtos.Donor>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    out.postValue(Resource.success(response.body().isEmpty() ? null : response.body().get(0)));
                } else {
                    out.postValue(Resource.error(NovaErrors.code(response)));
                }
            }
            @Override public void onFailure(Call<List<NovaDtos.Donor>> call, Throwable t) {
                out.postValue(Resource.error("network"));
            }
        });
        return out;
    }

    public LiveData<Resource<Boolean>> createDonor(String group, String district, String area,
                                                   String lastDonation, boolean available) {
        MutableLiveData<Resource<Boolean>> out = new MutableLiveData<>(Resource.loading());
        Map<String, Object> body = new HashMap<>();
        body.put("user_id", session.getUserId());
        body.put("blood_group", group);
        body.put("district", district);
        body.put("area", emptyToNull(area));
        body.put("available", available);
        body.put("last_donation_date", emptyToNull(lastDonation));
        body.put("donations_count", 0); // server-owned counter; insert contract
        api.insertDonor(body).enqueue(new Callback<Void>() {
            @Override public void onResponse(Call<Void> call, Response<Void> response) {
                out.postValue(response.isSuccessful() ? Resource.success(true) : Resource.error(NovaErrors.code(response)));
            }
            @Override public void onFailure(Call<Void> call, Throwable t) {
                out.postValue(Resource.error("network"));
            }
        });
        return out;
    }

    public LiveData<Resource<Boolean>> updateDonor(String group, String district, String area,
                                                   String lastDonation, boolean available) {
        MutableLiveData<Resource<Boolean>> out = new MutableLiveData<>(Resource.loading());
        Map<String, Object> body = new HashMap<>();
        body.put("blood_group", group);
        body.put("district", district);
        body.put("area", emptyToNull(area));
        body.put("available", available);
        body.put("last_donation_date", emptyToNull(lastDonation));
        api.patchDonor("eq." + session.getUserId(), body).enqueue(new Callback<Void>() {
            @Override public void onResponse(Call<Void> call, Response<Void> response) {
                out.postValue(response.isSuccessful() ? Resource.success(true) : Resource.error(NovaErrors.code(response)));
            }
            @Override public void onFailure(Call<Void> call, Throwable t) {
                out.postValue(Resource.error("network"));
            }
        });
        return out;
    }

    public LiveData<Resource<Boolean>> leaveDonor() {
        MutableLiveData<Resource<Boolean>> out = new MutableLiveData<>(Resource.loading());
        api.deleteDonor("eq." + session.getUserId()).enqueue(new Callback<Void>() {
            @Override public void onResponse(Call<Void> call, Response<Void> response) {
                out.postValue(response.isSuccessful() ? Resource.success(true) : Resource.error(NovaErrors.code(response)));
            }
            @Override public void onFailure(Call<Void> call, Throwable t) {
                out.postValue(Resource.error("network"));
            }
        });
        return out;
    }

    public LiveData<Resource<List<NovaDtos.District>>> districts() {
        MutableLiveData<Resource<List<NovaDtos.District>>> out = new MutableLiveData<>(Resource.loading());
        Map<String, String> q = new HashMap<>();
        q.put("select", "name,division");
        q.put("order", "division.asc,name.asc");
        api.getDistricts(q).enqueue(new Callback<List<NovaDtos.District>>() {
            @Override public void onResponse(Call<List<NovaDtos.District>> call,
                                             Response<List<NovaDtos.District>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    out.postValue(Resource.success(response.body()));
                } else {
                    out.postValue(Resource.error(NovaErrors.code(response)));
                }
            }
            @Override public void onFailure(Call<List<NovaDtos.District>> call, Throwable t) {
                out.postValue(Resource.error("network"));
            }
        });
        return out;
    }

    /**
     * The optional photo of one request. The feed RPC does not return it, so the detail
     * screen reads the column directly when the list had nothing to pass along.
     */
    public LiveData<Resource<String>> requestPhoto(String requestId) {
        MutableLiveData<Resource<String>> out = new MutableLiveData<>(Resource.loading());
        Map<String, String> query = new HashMap<>();
        query.put("select", "photo_url");
        query.put("id", "eq." + requestId);
        api.getMyBloodRequests(query).enqueue(new Callback<List<NovaDtos.BloodRequest>>() {
            @Override public void onResponse(Call<List<NovaDtos.BloodRequest>> call,
                                             Response<List<NovaDtos.BloodRequest>> response) {
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    NovaDtos.BloodRequest row = response.body().get(0);
                    out.postValue(Resource.success(row.photoUrl == null ? "" : row.photoUrl));
                } else {
                    out.postValue(Resource.success(""));
                }
            }

            @Override public void onFailure(Call<List<NovaDtos.BloodRequest>> call, Throwable t) {
                out.postValue(Resource.success(""));
            }
        });
        return out;
    }

    private static String emptyToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    /**
     * nova_create_blood_request takes all ten arguments without defaults, so an omitted
     * (null) optional value makes PostgREST fail to resolve the overload. Empty string
     * keeps the key in the JSON body while meaning "not provided".
     */
    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
