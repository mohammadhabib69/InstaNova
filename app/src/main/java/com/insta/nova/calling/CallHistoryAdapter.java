package com.insta.nova.calling;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.insta.nova.R;
import com.insta.nova.util.GlideUtils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class CallHistoryAdapter extends RecyclerView.Adapter<CallHistoryAdapter.CallViewHolder> {

    public interface OnCallActionListener {
        void onCallAgain(@NonNull CallHistoryStore.CallRecord record);
        void onOpenChat(@NonNull CallHistoryStore.CallRecord record);
    }

    private final Context context;
    private final List<CallHistoryStore.CallRecord> callList;
    private final OnCallActionListener listener;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm a", Locale.getDefault());
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM d, h:mm a", Locale.getDefault());

    public CallHistoryAdapter(@NonNull Context context,
                              @NonNull List<CallHistoryStore.CallRecord> callList,
                              @NonNull OnCallActionListener listener) {
        this.context = context;
        this.callList = callList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public CallViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_call_history, parent, false);
        return new CallViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CallViewHolder holder, int position) {
        CallHistoryStore.CallRecord record = callList.get(position);

        holder.tvUsername.setText(record.otherUsername != null && !record.otherUsername.isEmpty()
                ? record.otherUsername : (com.insta.nova.util.Constants.APP_NAME + " User"));

        if (record.otherAvatar != null && !record.otherAvatar.isEmpty() && !"none".equals(record.otherAvatar)) {
            GlideUtils.loadAvatar(holder.ivAvatar, record.otherAvatar);
        } else {
            holder.ivAvatar.setImageResource(R.drawable.ic_person_placeholder);
        }

        boolean isVideo = CallSession.TYPE_VIDEO.equalsIgnoreCase(record.callType);
        String typeStr = isVideo ? "video call" : "audio call";

        // Call again icon
        holder.btnCallAgain.setImageResource(isVideo ? R.drawable.ic_video : R.drawable.ic_phone_call);
        holder.btnCallAgain.setOnClickListener(v -> listener.onCallAgain(record));
        holder.itemView.setOnClickListener(v -> listener.onOpenChat(record));

        // Format Date / Time
        String timeStr = formatTimestamp(record.timestamp);

        // Status Details and Icons
        if ("answered".equals(record.status)) {
            String dirStr = record.isOutgoing ? "Outgoing" : "Incoming";
            String durationStr = formatDuration(record.durationSeconds);
            holder.tvCallDetails.setText(dirStr + " " + typeStr + " · " + durationStr + " · " + timeStr);
            holder.tvCallDetails.setTextColor(0xFF8E8E8E);

            holder.ivCallDirection.setImageResource(record.isOutgoing
                    ? R.drawable.ic_call_outgoing : R.drawable.ic_call_incoming);
        } else if ("missed".equals(record.status)) {
            holder.tvCallDetails.setText("Missed " + typeStr + " · " + timeStr);
            holder.tvCallDetails.setTextColor(0xFFED4956); // Red
            holder.ivCallDirection.setImageResource(R.drawable.ic_call_missed);
        } else if ("cancelled".equals(record.status)) {
            holder.tvCallDetails.setText("Cancelled " + typeStr + " · " + timeStr);
            holder.tvCallDetails.setTextColor(0xFF8E8E8E);
            holder.ivCallDirection.setImageResource(R.drawable.ic_call_outgoing);
        } else if ("rejected".equals(record.status)) {
            holder.tvCallDetails.setText("Declined " + typeStr + " · " + timeStr);
            holder.tvCallDetails.setTextColor(0xFF8E8E8E);
            holder.ivCallDirection.setImageResource(R.drawable.ic_call_missed);
        } else {
            holder.tvCallDetails.setText("Failed " + typeStr + " · " + timeStr);
            holder.tvCallDetails.setTextColor(0xFF8E8E8E);
            holder.ivCallDirection.setImageResource(R.drawable.ic_call_missed);
        }
    }

    private String formatDuration(int seconds) {
        if (seconds <= 0) return "< 1m";
        int mins = seconds / 60;
        int secs = seconds % 60;
        if (mins == 0) return secs + "s";
        return mins + "m " + secs + "s";
    }

    private String formatTimestamp(long timeMs) {
        if (timeMs <= 0) return "";
        long now = System.currentTimeMillis();
        long diff = now - timeMs;
        if (diff < 24 * 3600 * 1000L) {
            return timeFormat.format(new Date(timeMs));
        } else {
            return dateFormat.format(new Date(timeMs));
        }
    }

    @Override
    public int getItemCount() {
        return callList.size();
    }

    static class CallViewHolder extends RecyclerView.ViewHolder {
        de.hdodenhof.circleimageview.CircleImageView ivAvatar;
        TextView tvUsername;
        ImageView ivCallDirection;
        TextView tvCallDetails;
        ImageView btnCallAgain;

        public CallViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAvatar = itemView.findViewById(R.id.ivAvatar);
            tvUsername = itemView.findViewById(R.id.tvUsername);
            ivCallDirection = itemView.findViewById(R.id.ivCallDirection);
            tvCallDetails = itemView.findViewById(R.id.tvCallDetails);
            btnCallAgain = itemView.findViewById(R.id.btnCallAgain);
        }
    }
}
