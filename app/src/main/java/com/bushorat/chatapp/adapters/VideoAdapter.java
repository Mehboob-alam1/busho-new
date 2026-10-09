package com.bushorat.chatapp.adapters;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import com.bushorat.chatapp.R;
import com.bushorat.chatapp.activities.VideoPlayerActivity;
import com.bushorat.chatapp.helpers.SessionManager;
import com.bushorat.chatapp.models.VideoModel;

import java.util.List;

public class VideoAdapter extends RecyclerView.Adapter<VideoAdapter.VideoViewHolder> {
    private List<VideoModel> videoList;
    private Context context;
    private SessionManager sessionManager;

    public VideoAdapter(Context context, List<VideoModel> videoList) {
        this.context = context;
        this.videoList = videoList;
        this.sessionManager = new SessionManager(context);
    }

    @NonNull
    @Override
    public VideoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.video_grid_item, parent, false);
        return new VideoViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VideoViewHolder holder, int position) {
        VideoModel video = videoList.get(position);

        holder.title.setText(video.getTitle() != null ? video.getTitle() : context.getString(R.string.app_name));

        RequestOptions options = new RequestOptions()
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .centerCrop()
                .placeholder(R.drawable.ic_video_placeholder)
                .error(R.drawable.ic_video_error);

        String thumbnailSource = video.getThumbnailUrl() != null ? video.getThumbnailUrl() : video.getVideoUrl();

        Glide.with(context)
                .load(thumbnailSource)
                .apply(options)
                .into(holder.thumbnail);

        holder.itemView.setOnClickListener(v -> {
            v.animate().scaleX(0.97f).scaleY(0.97f).setDuration(80)
                    .withEndAction(() -> {
                        v.animate().scaleX(1f).scaleY(1f).setDuration(80).start();

                        if (sessionManager.isUserLoggedIn()) {
                            sessionManager.updateUserActivity("video_clicked", new java.util.HashMap<String, Object>() {{
                                put("videoUrl", video.getVideoUrl());
                                put("title", video.getTitle());
                                put("uploadedBy", video.getUploadedBy());
                                put("timestamp", System.currentTimeMillis());
                            }});
                        }

                        Intent intent = new Intent(context, VideoPlayerActivity.class);
                        intent.putExtra("VIDEO_URL", video.getVideoUrl());
                        intent.putExtra("THUMBNAIL_URL", thumbnailSource);
                        intent.putExtra("VIDEO_TITLE", video.getTitle());
                        context.startActivity(intent);
                    }).start();
        });
    }

    @Override
    public int getItemCount() { return videoList.size(); }

    public static class VideoViewHolder extends RecyclerView.ViewHolder {
        ImageView thumbnail;
        TextView title;
        public VideoViewHolder(@NonNull View itemView) {
            super(itemView);
            thumbnail = itemView.findViewById(R.id.thumbnail);
            title = itemView.findViewById(R.id.videoTitle);
        }
    }
}
