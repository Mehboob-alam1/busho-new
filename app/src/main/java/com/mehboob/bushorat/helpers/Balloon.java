package com.mehboob.bushorat.helpers;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;

import androidx.appcompat.widget.AppCompatImageView;

public class Balloon extends AppCompatImageView {

    public Balloon(Context context, int color) {
        super(context);
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.OVAL);
        shape.setColor(color);
        shape.setSize(120, 160);
        setBackground(shape);
    }
}
