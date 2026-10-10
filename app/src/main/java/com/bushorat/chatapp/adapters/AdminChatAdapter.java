package com.bushorat.chatapp.adapters;

import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bushorat.chatapp.R;
import com.bushorat.chatapp.helpers.AdminChatHelper;
import com.bushorat.chatapp.models.ChatMessage;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AdminChatAdapter extends RecyclerView.Adapter<AdminChatAdapter.Holder> {
    public interface AttachmentListener {
        void onOpenAttachment(ChatMessage message);
    }

    private final List<ChatMessage> items = new ArrayList<>();
    private final DateFormat timeFormat = DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault());
    private AttachmentListener attachmentListener;
    private String adminDisplayName = "Support";
    private String userDisplayName = "You";

    public void setAttachmentListener(AttachmentListener listener) {
        this.attachmentListener = listener;
    }

    public void setAdminDisplayName(String adminDisplayName) {
        if (adminDisplayName != null && !adminDisplayName.isEmpty()) {
            this.adminDisplayName = adminDisplayName;
            notifyDataSetChanged();
        }
    }

    public void setUserDisplayName(String userDisplayName) {
        if (userDisplayName != null && !userDisplayName.isEmpty()) {
            this.userDisplayName = userDisplayName;
            notifyDataSetChanged();
        }
    }

    public void setMessages(List<ChatMessage> messages) {
        items.clear();
        items.addAll(messages);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_message, parent, false);
        return new Holder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        ChatMessage msg = items.get(position);
        boolean user = msg.isFromUser();
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) holder.bubble.getLayoutParams();
        lp.gravity = user ? Gravity.END : Gravity.START;
        holder.bubble.setLayoutParams(lp);
        holder.bubble.setBackgroundResource(user ? R.drawable.bg_chat_bubble_out : R.drawable.bg_chat_bubble_in);

        holder.sender.setText(user ? userDisplayName : adminDisplayName);
        holder.sender.setTextColor(user
                ? holder.itemView.getContext().getColor(R.color.white)
                : holder.itemView.getContext().getColor(R.color.rose_light));
        holder.time.setText(timeFormat.format(new Date(msg.timestamp)));

        boolean hasText = msg.text != null && !msg.text.isEmpty();
        if (hasText) {
            holder.text.setVisibility(View.VISIBLE);
            holder.text.setText(msg.text);
        } else {
            holder.text.setVisibility(View.GONE);
        }

        boolean isImage = AdminChatHelper.TYPE_IMAGE.equals(msg.type)
                || (msg.mimeType != null && msg.mimeType.startsWith("image/"));
        if (isImage && msg.fileUrl != null) {
            holder.imgPreview.setVisibility(View.VISIBLE);
            Glide.with(holder.imgPreview.getContext())
                    .load(msg.fileUrl)
                    .centerCrop()
                    .into(holder.imgPreview);
            holder.imgPreview.setOnClickListener(v -> {
                if (attachmentListener != null) {
                    attachmentListener.onOpenAttachment(msg);
                }
            });
        } else {
            holder.imgPreview.setVisibility(View.GONE);
            holder.imgPreview.setOnClickListener(null);
        }

        if (msg.fileUrl != null && !isImage) {
            holder.file.setVisibility(View.VISIBLE);
            holder.file.setText("📎 " + (msg.fileName != null ? msg.fileName : "File"));
            holder.file.setOnClickListener(v -> {
                if (attachmentListener != null) {
                    attachmentListener.onOpenAttachment(msg);
                }
            });
        } else {
            holder.file.setVisibility(View.GONE);
            holder.file.setOnClickListener(null);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        LinearLayout bubble;
        TextView sender;
        TextView text;
        TextView file;
        TextView time;
        ImageView imgPreview;

        Holder(@NonNull View itemView) {
            super(itemView);
            bubble = itemView.findViewById(R.id.bubble);
            sender = itemView.findViewById(R.id.tvSender);
            text = itemView.findViewById(R.id.tvText);
            file = itemView.findViewById(R.id.tvFile);
            time = itemView.findViewById(R.id.tvTime);
            imgPreview = itemView.findViewById(R.id.imgPreview);
        }
    }
}
