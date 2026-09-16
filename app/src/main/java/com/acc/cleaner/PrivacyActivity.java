package com.acc.cleaner;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class PrivacyActivity extends Activity {
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
        root.setPadding(dp(18), dp(24), dp(18), dp(36));
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);

        TextView brand = text("ACC CLEANER · PRIVASI", 12, primary, Typeface.BOLD);
        brand.setLetterSpacing(0.12f);
        root.addView(brand);

        TextView title = text("Privasi & akses file", 27, text, Typeface.BOLD);
        LinearLayout.LayoutParams titleLp = matchWrap();
        titleLp.topMargin = dp(7);
        root.addView(title, titleLp);

        TextView intro = text(
                "Versi 1.1.0 memproses file secara lokal di perangkat dan tidak meminta izin internet.",
                14, muted, Typeface.NORMAL);
        LinearLayout.LayoutParams introLp = matchWrap();
        introLp.topMargin = dp(6);
        root.addView(intro, introLp);

        addSection(root, "DATA YANG DIKUMPULKAN",
                "Tidak ada data pribadi, isi file, daftar file, analitik, akun, lokasi, kontak, atau identifier iklan yang dikirim dari aplikasi pada build ini.");

        addSection(root, "AKSES SEMUA FILE",
                "Scan Perangkat dan WhatsApp Business Cleaner dapat memerlukan Akses semua file pada Android 11+. Akses ini dipakai untuk menemukan dan memelihara file/folder di penyimpanan bersama yang menjadi fungsi inti ACC Cleaner. Akses tidak diberikan kepada pihak ketiga.");

        addSection(root, "FOLDER SCAN",
                "Pada File Cleaner, kamu dapat memilih Pilih Folder sebagai alternatif yang lebih terbatas. Android akan menampilkan pemilih folder sistem dan ACC Cleaner hanya mendapat akses ke folder yang kamu setujui.");

        addSection(root, "PENGHAPUSAN FILE",
                "ACC Cleaner tidak menghapus file otomatis. Hasil scan tampil untuk ditinjau, pilihan awal kosong, dan penghapusan permanen memerlukan tindakan pengguna serta dua tahap konfirmasi.");

        addSection(root, "WHATSAPP BUSINESS",
                "Fitur WhatsApp Business Cleaner hanya memindai lokasi WhatsApp Business yang didukung. Bulk select hanya menandai kandidat temporary/log; tidak ada auto-delete.");

        addSection(root, "IKLAN & ANALITIK",
                "Build ini belum memuat SDK iklan atau analitik. Jika monetisasi ditambahkan pada versi berikutnya, kebijakan privasi dan deklarasi Google Play harus diperbarui sebelum versi tersebut dirilis.");

        TextView note = text(
                "Dokumen privasi publik untuk Google Play harus menggunakan isi yang sama dan tersedia pada URL aktif sebelum Production.",
                11, warning, Typeface.BOLD);
        LinearLayout.LayoutParams noteLp = matchWrap();
        noteLp.topMargin = dp(18);
        root.addView(note, noteLp);
    }

    private void addSection(LinearLayout root, String heading, String body) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setBackground(rounded(surface, dp(18)));
        LinearLayout.LayoutParams cardLp = matchWrap();
        cardLp.topMargin = dp(14);
        root.addView(card, cardLp);

        TextView h = text(heading, 11, primary, Typeface.BOLD);
        h.setLetterSpacing(0.10f);
        card.addView(h);

        TextView b = text(body, 13, text, Typeface.NORMAL);
        LinearLayout.LayoutParams bodyLp = matchWrap();
        bodyLp.topMargin = dp(7);
        card.addView(b, bodyLp);
    }

    private TextView text(String value, int sp, int color, int style) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(Typeface.DEFAULT, style);
        t.setLineSpacing(0f, 1.10f);
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
