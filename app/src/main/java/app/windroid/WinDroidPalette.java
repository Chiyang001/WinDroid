package app.windroid;

import android.content.Context;

import com.winlator.R;

final class WinDroidPalette {
    final int background;
    final int surface;
    final int tile;
    final int border;
    final int text;
    final int secondary;
    final int accent;
    final int onAccent;
    final int accentTile;
    final int accentSecondary;

    WinDroidPalette(Context context) {
        background = context.getColor(R.color.windroid_window);
        surface = context.getColor(R.color.windroid_surface);
        tile = context.getColor(R.color.windroid_tile);
        border = context.getColor(R.color.windroid_border);
        text = context.getColor(R.color.windroid_primary_text);
        secondary = context.getColor(R.color.windroid_secondary_text);
        accent = context.getColor(R.color.windroid_accent);
        onAccent = context.getColor(R.color.windroid_on_accent);
        accentTile = context.getColor(R.color.windroid_accent_tile);
        accentSecondary = context.getColor(R.color.windroid_accent_secondary);
    }
}
