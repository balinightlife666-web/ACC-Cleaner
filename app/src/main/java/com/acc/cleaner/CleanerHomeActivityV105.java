package com.acc.cleaner;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
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

        TextView brand = text("ACC CLEANER · v1.1.0", 12, primary, Typeface.BOLD);
        brand.setLetterSpacing(0.14f);
        root.addView(brand);

        TextView title = text("Bersihkan file. Kamu yang putuskan.", 27, text, Typeface.BOLD);
        LinearLayout.LayoutParams titleLp = matchWrap();
        titleLp.topMargin = dp(7);
        root.addView(title, titleLp);

        TextView sub = text(
                "ACC Cleaner menampilkan file untuk ditinjau sebelum ada tindakan hapus. Tidak ada auto-delete.",
                14, muted, Typeface.NORMAL);
        LinearLayout.LayoutParams subLp = matchWrap();
        subLp.topMargin = dp(6);
        root.addView(sub, subLp);

        LinearLayout general = card();
        LinearLayout.LayoutParams generalLp = matchWrap();
        generalLp.topMargin = dp(20);
        root.addView(general, generalLp);
        general.addView(text("FILE CLEANER", 12, primary, Typeface.BOLD));
        TextView generalDesc = text(
                "Tinjau file besar, Download lama, screenshot, installer APK, temporary/log, foto, video dan dokumen.",
                13, muted, Typeface.NORMAL);
        LinearLayout.LayoutParams generalDescLp = matchWrap();
        generalDescLp.topMargin = dp(6);
        general.addView(generalDesc, generalDescLp);
        TextView generalSafe = text(
                "Scan Perangkat dapat memakai akses semua file. Folder Scan tersedia sebagai opsi tanpa akses luas.",
                11, warning, Typeface.BOLD);
        LinearLayout.LayoutParams generalSafeLp = matchWrap();
        generalSafeLp.topMargin = dp(8);
        general.addView(generalSafe, generalSafeLp);
        Button generalButton = button("BUKA FILE CLEANER", false);
        LinearLayout.LayoutParams generalBtnLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
        generalBtnLp.topMargin = dp(12);
        general.addView(generalButton, generalBtnLp);
        generalButton.setOnClickListener(v -> openGeneralCleaner());

        LinearLayout wa = card();
        LinearLayout.LayoutParams waLp = matchWrap();
        waLp.topMargin = dp(14);
        root.addView(wa, waLp);
        wa.addView(text("WHATSAPP BUSINESS", 12, primary, Typeface.BOLD));
        TextView waDesc = text(
                "Scan khusus folder WhatsApp Business. Bisa memilih TMP/log lama dalam jumlah besar untuk ditinjau.",
                14, text, Typeface.BOLD);
        LinearLayout.LayoutParams waDescLp = matchWrap();
        waDescLp.topMargin = dp(6);
        wa.addView(waDesc, waDescLp);
        TextView safe = text(
                "Bulk select hanya menandai file. Penghapusan permanen tetap memakai dua konfirmasi.",
                12, warning, Typeface.BOLD);
        LinearLayout.LayoutParams safeLp = matchWrap();
        safeLp.topMargin = dp(8);
        wa.addView(safe, safeLp);
        Button waButton = button("SCAN WA BUSINESS", true);
        LinearLayout.LayoutParams waBtnLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
        waBtnLp.topMargin = dp(14);
        wa.addView(waButton, waBtnLp);
        waButton.setOnClickListener(v -> openWhatsAppCleaner());

        LinearLayout privacy = card();
        LinearLayout.LayoutParams privacyLp = matchWrap();
        privacyLp.topMargin = dp(14);
        root.addView(privacy, privacyLp);
        privacy.addView(text("PRIVASI & AKSES", 12, primary, Typeface.BOLD));
        TextView privacyDesc = text(
                "Build ini tidak meminta izin internet dan tidak mengirim file keluar perangkat. Akses file dipakai hanya untuk fungsi pembersihan yang kamu mulai sendiri.",
                12, muted, Typeface.NORMAL);
        LinearLayout.LayoutParams privacyDescLp = matchWrap();
        privacyDescLp.topMargin = dp(6);
        privacy.addView(privacyDesc, privacyDescLp);
        Button privacyButton = button("LIHAT PRIVASI & AKSES", false);
        LinearLayout.LayoutParams privacyBtnLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        privacyBtnLp.topMargin = dp(12);
        privacy.addView(privacyButton, privacyBtnLp);
        privacyButton.setOnClickListener(v -> startActivity(new Intent(this, PrivacyActivity.class)));
    }

    private void openGeneralCleaner() {
        if (!needsBroadAccessDisclosure()) {
            startActivity(new Intent(this, ReviewMainActivityV104.class));
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Tentang akses file")
                .setMessage(
                        "Scan Perangkat dapat meminta izin Akses semua file agar ACC Cleaner dapat meninjau dan memelihara file/folder di penyimpanan bersama. "
                                + "File tetap diproses lokal dan tidak diunggah. ACC Cleaner tidak memilih atau menghapus file otomatis.\n\n"
                                + "Kamu juga dapat masuk lalu memakai Pilih Folder tanpa memberikan akses semua file.")
                .setNegativeButton("Batal", null)
                .setPositiveButton("Lanjut ke cleaner", (dialog, which) ->
                        startActivity(new Intent(this, ReviewMainActivityV104.class)))
                .show();
    }

    private void openWhatsAppCleaner() {
        if (!needsBroadAccessDisclosure()) {
            startActivity(new Intent(this, WhatsAppBusinessCleanerActivity.class));
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Akses untuk WhatsApp Business Cleaner")
                .setMessage(
                        "Fitur ini perlu meninjau folder WhatsApp Business secara otomatis, termasuk Android/media/com.whatsapp.w4b. "
                                + "Pada Android 11+ ACC Cleaner akan meminta Akses semua file. Data tidak diunggah dan penghapusan tetap hanya terjadi setelah kamu memilih file dan mengonfirmasi dua kali.")
                .setNegativeButton("Batal", null)
                .setPositiveButton("Lanjut", (dialog, which) ->
                        startActivity(new Intent(this, WhatsAppBusinessCleanerActivity.class)))
                .show();
    }

    private boolean needsBroadAccessDisclosure() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager();
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
