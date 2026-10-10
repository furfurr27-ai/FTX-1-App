package dev.n0png.fieldops.app;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * CP-0009D proof-of-packaging-only Android Activity.
 *
 * No CAT/PTT, USB, microphone, network, RF, background service, data transmission
 * or receipt claim is made here. Host archive contracts are not yet UI-wired.
 */
public final class MainActivity extends Activity {
    private static final int BACKGROUND = Color.rgb(15, 19, 15);
    private static final int PANEL = Color.rgb(32, 38, 29);
    private static final int BORDER = Color.rgb(85, 92, 68);
    private static final int TEXT = Color.rgb(235, 232, 211);
    private static final int MUTED = Color.rgb(180, 183, 159);
    private static final int AMBER = Color.rgb(230, 180, 101);

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BACKGROUND);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(28), dp(20), dp(24));
        scroll.addView(root);

        TextView heading = text("N0PNG  /  FIELDOPS", 24, AMBER, true);
        root.addView(heading);
        root.addView(spacer(6));
        root.addView(text("FTX-1 • ANDROID PACKAGING FOUNDATION", 12, MUTED, true));
        root.addView(spacer(18));

        panel(root, "OFFLINE / HARDWARE DISCONNECTED",
            "This installable developer shell does not connect to a radio, capture audio, transmit, or send any data.");
        panel(root, "PROPAGATION ARCHIVE",
            "Canonical offline-history contracts are host-tested. Android storage, archive selection screens, and live-source wiring are not yet implemented.");
        panel(root, "DIGITAL MODES / LOGBOOK",
            "The host-side engines and research are separate workstreams. This APK does not operate FT8, FT4, JS8, WSPR, CW, SSB, or LoTW.");
        panel(root, "NEXT INTEGRATION",
            "Attach the verified host modules to Android UI and services only after compile, data, privacy, and physical-radio checks.");
        root.addView(spacer(8));
        root.addView(text("BUILD: CP-0009D • DEBUG / CI VERIFICATION ONLY", 11, MUTED, false));

        setContentView(scroll);
    }

    private void panel(LinearLayout parent, String title, String copy) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(15), dp(15), dp(15), dp(15));
        GradientDrawable background = new GradientDrawable();
        background.setColor(PANEL);
        background.setCornerRadius(dp(7));
        background.setStroke(dp(1), BORDER);
        box.setBackground(background);
        box.addView(text(title, 14, AMBER, true));
        box.addView(spacer(7));
        box.addView(text(copy, 14, TEXT, false));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(12);
        parent.addView(box, params);
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView result = new TextView(this);
        result.setText(value);
        result.setTextSize(sp);
        result.setTextColor(color);
        result.setGravity(Gravity.START);
        if (bold) result.setTypeface(android.graphics.Typeface.DEFAULT,
            android.graphics.Typeface.BOLD);
        return result;
    }

    private View spacer(int height) {
        View view = new View(this);
        view.setLayoutParams(new LinearLayout.LayoutParams(1, dp(height)));
        return view;
    }

    private int dp(int value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }
}
