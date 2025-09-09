package com.mehboob.bushorat.adapters;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ProgressBar;


import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import com.mehboob.bushorat.R;
import com.mehboob.bushorat.activities.VideoPlayerActivity;
import com.mehboob.bushorat.helpers.SessionManager;
import com.mehboob.bushorat.models.VideoModel;

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

        // Load thumbnail using Glide with optimized settings
        RequestOptions options = new RequestOptions()
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .centerCrop()
                .placeholder(R.drawable.ic_video_placeholder)
                .error(R.drawable.ic_video_error);

        Glide.with(context)
                .load(video.getThumbnailUrl())
                .apply(options)
                .into(holder.thumbnail);

        holder.itemView.setOnClickListener(v -> {
            // Track video click
            if (sessionManager.isUserLoggedIn()) {
                sessionManager.updateUserActivity("video_clicked", new java.util.HashMap<String, Object>() {{
                    put("videoUrl", video.getVideoUrl());
                    put("thumbnailUrl", video.getThumbnailUrl());
                    put("position", position);
                    put("timestamp", System.currentTimeMillis());
                }});
            }

            Intent intent = new Intent(context, VideoPlayerActivity.class);
            intent.putExtra("VIDEO_URL", video.getVideoUrl());
            intent.putExtra("THUMBNAIL_URL", video.getThumbnailUrl());
            context.startActivity(intent);
        });
    }

    @Override
    public int getItemCount() { return videoList.size(); }

    public static class VideoViewHolder extends RecyclerView.ViewHolder {
        ImageView thumbnail;
        public VideoViewHolder(@NonNull View itemView) {
            super(itemView);
            thumbnail = itemView.findViewById(R.id.thumbnail);
        }
    }
}
