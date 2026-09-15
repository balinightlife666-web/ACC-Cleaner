package com.acc.cleaner;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class CleanerHomeActivityV105 extends Activity {
    private int bg, surface, primary, text, muted, warning;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        bg = getColor(R.color.bg);
        surface = getColor(R.color.surface);
        primary = getColor(R.color.primary);
        text = getColor(R.color.text);
        muted = getColor(R.color.muted);
        warning = getColor(R.color.warning);
        buildUi();
    }

    private void buildUi() {
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(bg);
        scroll.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(24), dp(18), dp(32));
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);

        TextView brand = text("ACC CLEANER · v1.0.5", 12, primary, Typeface.BOLD);
        brand.setLetterSpacing(0.14f);
        root.addView(brand);

        TextView title = text("Pilih jenis scan", 27, text, Typeface.BOLD);
        LinearLayout.LayoutParams titleLp = matchWrap();
        titleLp.topMargin = dp(7);
        root.addView(title, titleLp);

        TextView sub = text("WhatsApp Business sekarang dipisah supaya ribuan file tidak bercampur dengan hasil scan lain.", 14, muted, Typeface.NORMAL);
        LinearLayout.LayoutParams subLp = matchWrap();
        subLp.topMargin = dp(6);
        root.addView(sub, subLp);

        LinearLayout general = card();
        LinearLayout.LayoutParams generalLp = matchWrap();
        generalLp.topMargin = dp(20);
        root.addView(general, generalLp);
        general.addView(text("SCAN UMUM", 12, primary, Typeface.BOLD));
        TextView generalDesc = text("Foto, video, APK, Download lama, screenshot, temporary/log dan file besar.", 13, muted, Typeface.NORMAL);
        LinearLayout.LayoutParams generalDescLp = matchWrap();
        generalDescLp.topMargin = dp(6);
        general.addView(generalDesc, generalDescLp);
        Button generalButton = button("BUKA SCAN UMUM", false);
        LinearLayout.LayoutParams generalBtnLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
        generalBtnLp.topMargin = dp(12);
        general.addView(generalButton, generalBtnLp);
        generalButton.setOnClickListener(v -> startActivity(new Intent(this, ReviewMainActivityV104.class)));

        LinearLayout wa = card();
        LinearLayout.LayoutParams waLp = matchWrap();
        waLp.topMargin = dp(14);
        root.addView(wa, waLp);
        wa.addView(text("WHATSAPP BUSINESS", 12, primary, Typeface.BOLD));
        TextView waDesc = text("Scan khusus folder WhatsApp Business. Bisa pilih ribuan TMP/log sekaligus tanpa centang satu-satu.", 14, text, Typeface.BOLD);
        LinearLayout.LayoutParams waDescLp = matchWrap();
        waDescLp.topMargin = dp(6);
        wa.addView(waDesc, waDescLp);
        TextView safe = text("Bulk select tidak otomatis menghapus. Tetap ada 2 konfirmasi sebelum penghapusan permanen.", 12, warning, Typeface.BOLD);
        LinearLayout.LayoutParams safeLp = matchWrap();
        safeLp.topMargin = dp(8);
        wa.addView(safe, safeLp);
        Button waButton = button("SCAN WA BUSINESS", true);
        LinearLayout.LayoutParams waBtnLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
        waBtnLp.topMargin = dp(14);
        wa.addView(waButton, waBtnLp);
        waButton.setOnClickListener(v -> startActivity(new Intent(this, WhatsAppBusinessCleanerActivity.class)));
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setBackground(rounded(surface, dp(18)));
        return card;
    }

    private Button button(String value, boolean filled) {
        Button b = new Button(this);
        b.setText(value);
        b.setTextSize(12);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setAllCaps(false);
        b.setTextColor(filled ? bg : primary);
        b.setBackground(rounded(filled ? primary : surface, dp(14)));
        return b;
    }

    private TextView text(String value, int sp, int color, int style) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(Typeface.DEFAULT, style);
        t.setLineSpacing(0f, 1.08f);
        return t;
    }

    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        return d;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
