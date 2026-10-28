package com.busho.app.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.busho.app.R;
import com.busho.app.models.SliderItem;

import java.util.ArrayList;
import java.util.List;

public class CarouselSliderAdapter extends RecyclerView.Adapter<CarouselSliderAdapter.SliderViewHolder> {

    private List<SliderItem> sliderItems = new ArrayList<>();
    private OnSliderClickListener clickListener;

    public interface OnSliderClickListener {
        void onSliderClick(String url);
    }

    public CarouselSliderAdapter(OnSliderClickListener clickListener) {
        this.clickListener = clickListener;
    }

    @NonNull
    @Override
    public SliderViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_carousel_slider, parent, false);
        return new SliderViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SliderViewHolder holder, int position) {
        SliderItem item = sliderItems.get(position);
        
        // Load image with Glide
        Glide.with(holder.itemView.getContext())
                .load(item.getImgUrl())
                .placeholder(R.drawable.demo_user)
                .error(R.drawable.demo_user)
                .centerCrop()
                .into(holder.imageView);

        // Set click listener
        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null && item.getUrl() != null && !item.getUrl().isEmpty()) {
                clickListener.onSliderClick(item.getUrl());
            }
        });
    }

    @Override
    public int getItemCount() {
        return sliderItems.size();
    }

    public void setSliderItems(List<SliderItem> items) {
        this.sliderItems = items != null ? items : new ArrayList<>();
        notifyDataSetChanged();
    }

    static class SliderViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;

        SliderViewHolder(@NonNull View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.sliderImage);
        }
    }
}

