package com.mehboob.bushorat.adapters;



import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.mehboob.bushorat.R;
import com.mehboob.bushorat.activities.WebViewActivity;
import com.mehboob.bushorat.models.Anime;


import java.util.List;

import maes.tech.intentanim.CustomIntent;

public class RecyclerViewAdapter extends RecyclerView.Adapter<RecyclerViewAdapter.MyViewHolder> {

    private Context mContext;
    private List<Anime> mData ;
    RequestOptions option;
    /**
     private InterstitialAd interstitialAd;
     */

    public RecyclerViewAdapter(Context mContext, List<Anime> mData) {
        this.mContext = mContext;
        this.mData = mData;
        /**
         *interstitialAd = new InterstitialAd(mContext.getApplicationContext());
         *interstitialAd.setAdUnitId(mContext.getString(R.string.admob_interstitial));
         *interstitialAd.loadAd(new AdRequest.Builder().build());
         */


        // Request option for Glide
        option = new RequestOptions().centerCrop().placeholder(R.drawable.loading_shape).error(R.drawable.loading_shape);
    }

    @Override
    public MyViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {

        View view ;
        LayoutInflater inflater = LayoutInflater.from(mContext);
        view = inflater.inflate(R.layout.row_items,parent,false);
        final MyViewHolder viewHolder = new MyViewHolder(view) ;
        viewHolder.view_container.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                /**
                 if (interstitialAd != null) {
                 interstitialAd.show();
                 } else {
                 //do something
                 }
                 */

                Intent i = new Intent(mContext, WebViewActivity.class);
                view.startAnimation(AnimationUtils.loadAnimation(mContext, R.anim.clcik));
                i.putExtra("action_link",mData.get(viewHolder.getAdapterPosition()).getWebsite_link());
                i.putExtra("puzzle_link",mData.get(viewHolder.getAdapterPosition()).getWebsite_link());
                i.putExtra("arcade_link",mData.get(viewHolder.getAdapterPosition()).getWebsite_link());
                i.putExtra("adventure_link",mData.get(viewHolder.getAdapterPosition()).getWebsite_link());
                i.putExtra("sports_link",mData.get(viewHolder.getAdapterPosition()).getWebsite_link());
                i.putExtra("racing_link",mData.get(viewHolder.getAdapterPosition()).getWebsite_link());
                i.putExtra("zombie_link",mData.get(viewHolder.getAdapterPosition()).getWebsite_link());

                mContext.startActivity(i);
                CustomIntent.customType(mContext,"fadein-to-fadeout");
            }
        });

        return viewHolder;
    }

    @Override
    public void onBindViewHolder(MyViewHolder holder, int position) {
        // Glide.with(mContext).load(mData.get(position).getWebsite_logo()).into(holder.img_thumbnail);

        //
        Glide.with(mContext).load(mData.get(position).getWebsite_logo()).apply(option).into(holder.img_thumbnail);
    }

    @Override
    public int getItemCount() {
        return mData.size();
    }

    public static class MyViewHolder extends RecyclerView.ViewHolder {

        ImageView img_thumbnail;
        LinearLayout view_container;

        public MyViewHolder(View itemView) {
            super(itemView);

            view_container = itemView.findViewById(R.id.container);
            img_thumbnail = itemView.findViewById(R.id.thumbnail);

        }
    }
}
