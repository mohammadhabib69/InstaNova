package com.insta.clone.ui.create;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.insta.clone.BuildConfig;
import com.insta.clone.data.local.SessionManager;
import com.insta.clone.data.remote.RetrofitClient;
import com.insta.clone.data.remote.api.RestApi;
import com.insta.clone.data.remote.api.StorageApi;
import com.insta.clone.data.remote.dto.PostDto;
import com.insta.clone.databinding.ActivityCreatePostBinding;
import com.insta.clone.util.Resource;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Uploads one image, creates its post row, then creates post_media; failures clean up storage. */
public class CreatePostActivity extends AppCompatActivity {
    private static final int PICK = 40; private ActivityCreatePostBinding binding; private Uri selected;
    private String uploadedPath; private StorageApi storage; private RestApi rest; private String uid;
    @Override protected void onCreate(Bundle b) { super.onCreate(b); binding = ActivityCreatePostBinding.inflate(getLayoutInflater()); setContentView(binding.getRoot()); uid = SessionManager.getInstance(this).getUserId(); RetrofitClient c = RetrofitClient.getInstance(this); storage=c.getStorageApi(); rest=c.getRestApi(); binding.btnPickImage.setOnClickListener(v -> pick()); binding.btnPublish.setOnClickListener(v -> publish()); }
    private void pick() { Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT); i.setType("image/*"); i.addCategory(Intent.CATEGORY_OPENABLE); startActivityForResult(i, PICK); }
    @Override protected void onActivityResult(int r, int c, Intent d) { super.onActivityResult(r,c,d); if (r==PICK && c==RESULT_OK && d != null && d.getData()!=null) { selected=d.getData(); binding.ivPreview.setImageURI(selected); binding.ivPreview.setVisibility(android.view.View.VISIBLE); binding.btnPublish.setEnabled(true); } }
    private void publish() {
        if (selected == null || uid == null) { toast("Choose a photo after signing in."); return; }
        setLoading(true); uploadedPath = uid + "/" + UUID.randomUUID() + ".jpg";
        byte[] bytes; try { bytes=read(selected); } catch (Exception e) { fail("Could not read the selected image: " + e.getMessage()); return; }
        storage.uploadFile("posts", uploadedPath, RequestBody.create(MediaType.parse("image/jpeg"), bytes)).enqueue(new Callback<Void>() {
            @Override public void onResponse(Call<Void> c, Response<Void> r) { if (!r.isSuccessful()) { fail("Post image upload failed (HTTP " + r.code() + "): " + error(r)); return; } createPost(); }
            @Override public void onFailure(Call<Void> c, Throwable t) { fail("Post image upload failed: " + t.getMessage()); }
        });
    }
    private void createPost() { Map<String,Object> body=new HashMap<>(); body.put("user_id",uid); body.put("caption",binding.etCaption.getText().toString().trim()); body.put("thumbnail_url",publicUrl()); rest.createPost(body).enqueue(new Callback<List<PostDto>>() {
        @Override public void onResponse(Call<List<PostDto>> c, Response<List<PostDto>> r) { if (!r.isSuccessful() || r.body()==null || r.body().isEmpty()) { fail("Post row insert failed (HTTP " + r.code() + "): " + error(r)); return; } Map<String,Object> media=new HashMap<>(); media.put("post_id",r.body().get(0).getId()); media.put("url",publicUrl()); media.put("position",0); rest.insertPostMedia(Collections.singletonList(media)).enqueue(new Callback<Void>() { @Override public void onResponse(Call<Void> c2,Response<Void> r2){ if(r2.isSuccessful()){ setResult(RESULT_OK); finish(); } else fail("Post media insert failed (HTTP " + r2.code() + "): " + error(r2)); } @Override public void onFailure(Call<Void> c2,Throwable t){ fail("Post media insert failed: " + t.getMessage()); }}); }
        @Override public void onFailure(Call<List<PostDto>> c, Throwable t) { fail("Post row insert failed: " + t.getMessage()); }
    }); }
    private String publicUrl(){ return BuildConfig.SUPABASE_URL + "storage/v1/object/public/posts/" + uploadedPath; }
    private void fail(String message){ setLoading(false); if(uploadedPath!=null) storage.deleteFile("posts",uploadedPath).enqueue(new Callback<Void>(){ @Override public void onResponse(Call<Void> c,Response<Void> r){} @Override public void onFailure(Call<Void> c,Throwable t){} }); toast(message); }
    private void setLoading(boolean v){ binding.progress.setVisibility(v?android.view.View.VISIBLE:android.view.View.GONE); binding.btnPublish.setEnabled(!v && selected!=null); binding.btnPickImage.setEnabled(!v); }
    private void toast(String s){ Toast.makeText(this,s,Toast.LENGTH_LONG).show(); }
    private byte[] read(Uri uri) throws Exception { try(InputStream in=getContentResolver().openInputStream(uri); ByteArrayOutputStream out=new ByteArrayOutputStream()){ byte[] b=new byte[8192]; int n; while((n=in.read(b))!=-1) out.write(b,0,n); return out.toByteArray(); } }
    private String error(Response<?> r){ try{return r.errorBody()==null?"":r.errorBody().string();}catch(Exception e){return e.getMessage();} }
}
