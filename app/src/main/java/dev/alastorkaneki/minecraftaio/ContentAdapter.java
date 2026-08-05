package dev.alastorkaneki.minecraftaio;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ImageView;
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
        int margin = dp(context, 10);
        cardParams.setMargins(margin, margin / 2, margin, margin / 2);
        card.setLayoutParams(cardParams);
        card.setRadius(dp(context, 20));
        card.setCardElevation(dp(context, 1));
        card.setClickable(true);
        card.setFocusable(true);

        LinearLayout body = new LinearLayout(context);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(context, 16), dp(context, 14), dp(context, 16), dp(context, 14));
        card.addView(body);

        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        body.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        ImageView logo = new ImageView(context);
        logo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(dp(context, 52), dp(context, 52));
        logoParams.setMargins(0, 0, dp(context, 14), 0);
        header.addView(logo, logoParams);

        LinearLayout textColumn = new LinearLayout(context);
        textColumn.setOrientation(LinearLayout.VERTICAL);
        header.addView(textColumn, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView source = new TextView(context);
        source.setTextSize(12);
        source.setTypeface(Typeface.SANS_SERIF, Typeface.BOLD);
        textColumn.addView(source);

        TextView title = new TextView(context);
        title.setTextSize(18);
        title.setTypeface(Typeface.SANS_SERIF, Typeface.BOLD);
        title.setMaxLines(2);
        textColumn.addView(title);

        TextView description = new TextView(context);
        description.setTextSize(14);
        description.setTypeface(Typeface.SANS_SERIF);
        description.setMaxLines(4);
        description.setPadding(0, dp(context, 10), 0, dp(context, 10));
        body.addView(description);

        MaterialButton open = new MaterialButton(
                context,
                null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        open.setText("View in Minecraft AIO");
        open.setAllCaps(false);
        body.addView(open, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        return new Holder(card, logo, source, title, description, open);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        ContentItem item = items.get(position);
        holder.source.setText(item.source + "  •  " + item.type);
        holder.title.setText(item.title);
        holder.description.setText(item.description);
        String image = item.imageUrl.isBlank()
                ? Backends.favicon(item.source.toLowerCase().replace(" ", "") + ".com")
                : item.imageUrl;
        RemoteImage.load(holder.logo, image);
        holder.open.setOnClickListener(v -> DetailActivity.open(v.getContext(), item));
        holder.card.setOnClickListener(v -> DetailActivity.open(v.getContext(), item));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final MaterialCardView card;
        final ImageView logo;
        final TextView source;
        final TextView title;
        final TextView description;
        final MaterialButton open;

        Holder(
                @NonNull MaterialCardView itemView,
                ImageView logo,
                TextView source,
                TextView title,
                TextView description,
                MaterialButton open
        ) {
            super(itemView);
            this.card = itemView;
            this.logo = logo;
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
