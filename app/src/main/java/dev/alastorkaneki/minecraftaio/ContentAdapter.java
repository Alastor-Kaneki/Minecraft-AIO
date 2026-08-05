package dev.alastorkaneki.minecraftaio;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;

final class ContentAdapter extends RecyclerView.Adapter<ContentAdapter.Holder> {
    private final List<ContentItem> items = new ArrayList<>();

    void replace(List<ContentItem> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        Context context = parent.getContext();
        MaterialCardView card = new MaterialCardView(context);
        RecyclerView.LayoutParams cardParams = new RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        int margin = dp(context, 8);
        cardParams.setMargins(margin, margin / 2, margin, margin / 2);
        card.setLayoutParams(cardParams);
        card.setRadius(dp(context, 18));
        card.setCardElevation(dp(context, 1));

        LinearLayout body = new LinearLayout(context);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(context, 18), dp(context, 14), dp(context, 18), dp(context, 14));
        card.addView(body);

        TextView source = new TextView(context);
        source.setTextSize(12);
        source.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        body.addView(source);

        TextView title = new TextView(context);
        title.setTextSize(18);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        body.addView(title);

        TextView description = new TextView(context);
        description.setTextSize(14);
        description.setPadding(0, dp(context, 6), 0, dp(context, 8));
        body.addView(description);

        MaterialButton open = new MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        open.setText("Open");
        body.addView(open, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return new Holder(card, source, title, description, open);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        ContentItem item = items.get(position);
        holder.source.setText(item.source + " • " + item.type);
        holder.title.setText(item.title);
        holder.description.setText(item.description);
        holder.open.setOnClickListener(v -> {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(item.url));
                v.getContext().startActivity(intent);
            } catch (Exception ignored) {
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final TextView source;
        final TextView title;
        final TextView description;
        final MaterialButton open;

        Holder(@NonNull MaterialCardView itemView, TextView source, TextView title, TextView description, MaterialButton open) {
            super(itemView);
            this.source = source;
            this.title = title;
            this.description = description;
            this.open = open;
        }
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
