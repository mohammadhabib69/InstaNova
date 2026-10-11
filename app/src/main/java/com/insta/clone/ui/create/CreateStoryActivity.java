package com.insta.clone.ui.create;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.insta.clone.BuildConfig;
import com.insta.clone.data.local.SessionManager;
import com.insta.clone.data.remote.RetrofitClient;
import com.insta.clone.data.remote.api.RestApi;
import com.insta.clone.data.remote.api.StorageApi;
import com.insta.clone.data.remote.dto.StoryDto;
import com.insta.clone.databinding.ActivityCreateStoryBinding;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Uploads a story image and persists the story row through PostgREST. */
public class CreateStoryActivity extends AppCompatActivity {
    private static final int PICK=41; private ActivityCreateStoryBinding binding; private Uri selected; private String path,uid; private StorageApi storage; private RestApi rest;
    @Override protected void onCreate(Bundle b){ super.onCreate(b); binding=ActivityCreateStoryBinding.inflate(getLayoutInflater()); setContentView(binding.getRoot()); uid=SessionManager.getInstance(this).getUserId(); RetrofitClient c=RetrofitClient.getInstance(this); storage=c.getStorageApi(); rest=c.getRestApi(); binding.btnPickImage.setOnClickListener(v->pick()); binding.btnPublish.setOnClickListener(v->publish()); }
    private void pick(){ Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT); i.setType("image/*"); i.addCategory(Intent.CATEGORY_OPENABLE); startActivityForResult(i,PICK); }
    @Override protected void onActivityResult(int r,int c,Intent d){ super.onActivityResult(r,c,d); if(r==PICK&&c==RESULT_OK&&d!=null&&d.getData()!=null){ selected=d.getData(); binding.ivPreview.setImageURI(selected); binding.ivPreview.setVisibility(android.view.View.VISIBLE); binding.btnPublish.setEnabled(true); } }
    private void publish(){ if(selected==null||uid==null){toast("Choose a photo after signing in.");return;} setLoading(true); path=uid+"/"+UUID.randomUUID()+".jpg"; byte[] data; try{data=read(selected);}catch(Exception e){fail("Could not read the selected image: "+e.getMessage());return;} storage.uploadFile("stories",path,RequestBody.create(MediaType.parse("image/jpeg"),data)).enqueue(new Callback<Void>(){ @Override public void onResponse(Call<Void> c,Response<Void> r){if(!r.isSuccessful()){fail("Story upload failed (HTTP "+r.code()+"): "+error(r));return;} Map<String,Object> body=new HashMap<>();body.put("user_id",uid);body.put("media_url",publicUrl());body.put("media_type","image");rest.createStory(body).enqueue(new Callback<List<StoryDto>>(){@Override public void onResponse(Call<List<StoryDto>> c2,Response<List<StoryDto>> r2){if(r2.isSuccessful()){setResult(RESULT_OK);finish();}else fail("Story row insert failed (HTTP "+r2.code()+"): "+error(r2));}@Override public void onFailure(Call<List<StoryDto>> c2,Throwable t){fail("Story row insert failed: "+t.getMessage());}});} @Override public void onFailure(Call<Void> c,Throwable t){fail("Story upload failed: "+t.getMessage());}}); }
    private String publicUrl(){return BuildConfig.SUPABASE_URL+"storage/v1/object/public/stories/"+path;}
    private void fail(String m){setLoading(false);if(path!=null)storage.deleteFile("stories",path).enqueue(new Callback<Void>(){@Override public void onResponse(Call<Void> c,Response<Void> r){}@Override public void onFailure(Call<Void> c,Throwable t){}});toast(m);}
    private void setLoading(boolean v){binding.progress.setVisibility(v?android.view.View.VISIBLE:android.view.View.GONE);binding.btnPublish.setEnabled(!v&&selected!=null);binding.btnPickImage.setEnabled(!v);}
    private byte[] read(Uri u)throws Exception{try(InputStream in=getContentResolver().openInputStream(u);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toByteArray();}}
    private String error(Response<?> r){try{return r.errorBody()==null?"":r.errorBody().string();}catch(Exception e){return e.getMessage();}}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
}
