package com.bushorat.app.adapters;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bushorat.app.R;
import com.bushorat.app.activities.WebViewActivity;
import com.bushorat.app.models.Game;

import java.util.List;

public class GameAdapter extends RecyclerView.Adapter<GameAdapter.GameViewHolder> {

    List<Game> gameList;
    Context context;

    public GameAdapter(List<Game> gameList, Context context) {
        this.gameList = gameList;
        this.context = context;
    }

    @NonNull
    @Override
    public GameViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_game, parent, false);
        return new GameViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull GameViewHolder holder, int position) {
        Game game = gameList.get(position);
        holder.name.setText(game.name);
        holder.image.setImageResource(game.imageResId);

        holder.itemView.setOnClickListener(v -> {
            v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(80)
                    .withEndAction(() -> {
                        v.animate().scaleX(1f).scaleY(1f).setDuration(80).start();
                        Intent intent = new Intent(context, WebViewActivity.class);
                        intent.putExtra("url", game.url);
                        intent.putExtra("title", game.name);
                        context.startActivity(intent);
                    }).start();
        });
    }

    @Override
    public int getItemCount() {
        return gameList.size();
    }

    static class GameViewHolder extends RecyclerView.ViewHolder {
        ImageView image;
        TextView name;

        public GameViewHolder(@NonNull View itemView) {
            super(itemView);
            image = itemView.findViewById(R.id.gameImage);
            name = itemView.findViewById(R.id.gameName);
        }
    }
}

