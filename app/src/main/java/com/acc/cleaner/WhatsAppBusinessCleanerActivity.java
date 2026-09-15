package com.acc.cleaner;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class WhatsAppBusinessCleanerActivity extends Activity {
    private static final int REQ_STORAGE = 6201;
    private static final long LARGE_BYTES = 100L * 1024L * 1024L;
    private static final long TEMP_OLD_MS = 7L * 24L * 60L * 60L * 1000L;
    private static final int DISPLAY_LIMIT = 250;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean scanning = new AtomicBoolean(false);
    private final List<Item> results = new ArrayList<>();

    private LinearLayout resultsBox;
    private TextView accessText;
    private TextView statusText;
    private TextView summaryText;
    private TextView selectedText;
    private ProgressBar progress;
    private Button scanButton;
    private Button oldTempButton;
    private Button allTempButton;
    private Button clearButton;
    private Button deleteButton;

    private int bg, surface, surface2, primary, text, muted, success, warning, danger;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        bindColors();
        buildUi();
        refreshAccess();
        if (hasDeepAccess()) startScan();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshAccess();
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private void bindColors() {
        bg = getColor(R.color.bg);
        surface = getColor(R.color.surface);
        surface2 = getColor(R.color.surface_2);
        primary = getColor(R.color.primary);
        text = getColor(R.color.text);
        muted = getColor(R.color.muted);
        success = getColor(R.color.success);
        warning = getColor(R.color.warning);
        danger = getColor(R.color.danger);
    }

    private void buildUi() {
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(bg);
        scroll.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(18), dp(16), dp(34));
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);

        TextView brand = text("ACC CLEANER · WA BUSINESS · v1.0.5", 11, primary, Typeface.BOLD);
        brand.setLetterSpacing(0.10f);
        root.addView(brand);

        TextView title = text("WhatsApp Business Cleaner", 24, text, Typeface.BOLD);
        LinearLayout.LayoutParams titleLp = matchWrap();
        titleLp.topMargin = dp(6);
        root.addView(title, titleLp);

        TextView sub = text("Scan ini hanya membaca folder WhatsApp Business. Hasil tidak dicampur dengan kamera, Download, Telegram, atau aplikasi lain.", 13, muted, Typeface.NORMAL);
        LinearLayout.LayoutParams subLp = matchWrap();
        subLp.topMargin = dp(5);
        root.addView(sub, subLp);

        LinearLayout control = card();
        LinearLayout.LayoutParams controlLp = matchWrap();
        controlLp.topMargin = dp(14);
        root.addView(control, controlLp);

        accessText = text("Memeriksa akses…", 12, muted, Typeface.BOLD);
        control.addView(accessText);

        scanButton = button("SCAN WA BUSINESS", true);
        LinearLayout.LayoutParams scanLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
        scanLp.topMargin = dp(10);
        control.addView(scanButton, scanLp);
        scanButton.setOnClickListener(v -> {
            if (hasDeepAccess()) startScan();
            else requestDeepAccess();
        });

        progress = new ProgressBar(this);
        progress.setVisibility(View.GONE);
        LinearLayout.LayoutParams progressLp = new LinearLayout.LayoutParams(dp(30), dp(30));
        progressLp.gravity = Gravity.CENTER_HORIZONTAL;
        progressLp.topMargin = dp(12);
        root.addView(progress, progressLp);

        statusText = text("Belum ada scan.", 12, muted, Typeface.NORMAL);
        statusText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statusLp = matchWrap();
        statusLp.topMargin = dp(6);
        root.addView(statusText, statusLp);

        LinearLayout resultCard = card();
        LinearLayout.LayoutParams resultLp = matchWrap();
        resultLp.topMargin = dp(14);
        root.addView(resultCard, resultLp);

        resultCard.addView(text("HASIL WA BUSINESS", 11, primary, Typeface.BOLD));
        summaryText = text("Setelah scan, ACC Cleaner akan memisahkan TMP/log dan file besar yang perlu ditinjau.", 13, muted, Typeface.NORMAL);
        LinearLayout.LayoutParams summaryLp = matchWrap();
        summaryLp.topMargin = dp(6);
        resultCard.addView(summaryText, summaryLp);

        selectedText = text("Belum ada file dipilih.", 12, warning, Typeface.BOLD);
        LinearLayout.LayoutParams selectedLp = matchWrap();
        selectedLp.topMargin = dp(8);
        resultCard.addView(selectedText, selectedLp);

        LinearLayout bulk1 = new LinearLayout(this);
        bulk1.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams bulk1Lp = matchWrap();
        bulk1Lp.topMargin = dp(10);
        resultCard.addView(bulk1, bulk1Lp);

        oldTempButton = button("PILIH TMP 7+ HARI", false);
        oldTempButton.setEnabled(false);
        bulk1.addView(oldTempButton, new LinearLayout.LayoutParams(0, dp(48), 1f));
        oldTempButton.setOnClickListener(v -> bulkSelect(true));

        View gap = new View(this);
        bulk1.addView(gap, new LinearLayout.LayoutParams(dp(8), 1));

        allTempButton = button("PILIH SEMUA TMP", false);
        allTempButton.setEnabled(false);
        bulk1.addView(allTempButton, new LinearLayout.LayoutParams(0, dp(48), 1f));
        allTempButton.setOnClickListener(v -> bulkSelect(false));

        clearButton = button("BATALKAN SEMUA PILIHAN", false);
        clearButton.setEnabled(false);
        LinearLayout.LayoutParams clearLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
        clearLp.topMargin = dp(8);
        resultCard.addView(clearButton, clearLp);
        clearButton.setOnClickListener(v -> clearSelection());

        TextView bulkHelp = text("PILIH TMP 7+ HARI = opsi lebih aman. PILIH SEMUA TMP juga memilih file temporary yang masih baru, jadi periksa ringkasannya sebelum hapus.", 11, warning, Typeface.BOLD);
        LinearLayout.LayoutParams helpLp = matchWrap();
        helpLp.topMargin = dp(9);
        resultCard.addView(bulkHelp, helpLp);

        resultsBox = new LinearLayout(this);
        resultsBox.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams boxLp = matchWrap();
        boxLp.topMargin = dp(8);
        resultCard.addView(resultsBox, boxLp);

        deleteButton = button("HAPUS FILE TERPILIH", true);
        deleteButton.setEnabled(false);
        deleteButton.setAlpha(0.4f);
        LinearLayout.LayoutParams deleteLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
        deleteLp.topMargin = dp(12);
        resultCard.addView(deleteButton, deleteLp);
        deleteButton.setOnClickListener(v -> confirmDelete());

        TextView safety = text("Tidak ada auto-select dan tidak ada auto-delete. Bulk select hanya menandai file; penghapusan tetap memakai dua konfirmasi.", 11, warning, Typeface.BOLD);
        safety.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams safetyLp = matchWrap();
        safetyLp.topMargin = dp(14);
        root.addView(safety, safetyLp);
    }

    private void refreshAccess() {
        if (hasDeepAccess()) {
            accessText.setText("Akses file aktif · siap scan WhatsApp Business");
            accessText.setTextColor(success);
            scanButton.setText("SCAN ULANG WA BUSINESS");
        } else {
            accessText.setText("Akses file belum aktif");
            accessText.setTextColor(warning);
            scanButton.setText("AKTIFKAN AKSES & SCAN");
        }
    }

    private boolean hasDeepAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) return Environment.isExternalStorageManager();
        return checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestDeepAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            } catch (Throwable t) {
                startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
            }
            Toast.makeText(this, "Aktifkan akses file untuk ACC Cleaner lalu kembali.", Toast.LENGTH_LONG).show();
        } else {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_STORAGE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_STORAGE && hasDeepAccess()) startScan();
    }

    private void startScan() {
        if (!hasDeepAccess()) {
            requestDeepAccess();
            return;
        }
        if (scanning.getAndSet(true)) return;

        results.clear();
        resultsBox.removeAllViews();
        summaryText.setText("Membaca folder WhatsApp Business…");
        selectedText.setText("Belum ada file dipilih.");
        statusText.setText("Scan WA Business berjalan…");
        progress.setVisibility(View.VISIBLE);
        scanButton.setEnabled(false);
        setBulkEnabled(false);

        executor.execute(() -> {
            List<Item> found = new ArrayList<>();
            AtomicInteger scanned = new AtomicInteger();
            try {
                File storage = Environment.getExternalStorageDirectory();
                File modern = new File(storage, "Android/media/com.whatsapp.w4b");
                File legacy = new File(storage, "WhatsApp Business");

                boolean rootFound = false;
                if (modern.exists()) {
                    rootFound = true;
                    scanTree(modern, found, scanned);
                }
                if (!modern.exists() && legacy.exists()) {
                    rootFound = true;
                    scanTree(legacy, found, scanned);
                }

                Collections.sort(found, Comparator.comparingLong((Item i) -> i.size).reversed());
                boolean finalRootFound = rootFound;
                runOnUiThread(() -> finishScan(found, scanned.get(), finalRootFound));
            } catch (Throwable t) {
                runOnUiThread(() -> failScan(t));
            }
        });
    }

    private void scanTree(File node, List<Item> found, AtomicInteger scanned) {
        if (Thread.currentThread().isInterrupted() || node == null || !node.exists()) return;
        if (node.isDirectory()) {
            File[] children;
            try {
                children = node.listFiles();
            } catch (SecurityException e) {
                return;
            }
            if (children == null) return;
            for (File child : children) scanTree(child, found, scanned);
            return;
        }

        int count = scanned.incrementAndGet();
        Category category = classify(node);
        if (category != null) {
            Item item = new Item();
            item.file = node;
            item.name = node.getName();
            item.path = node.getAbsolutePath();
            item.size = Math.max(0L, node.length());
            item.modified = node.lastModified();
            item.category = category;
            item.mime = mimeFromName(item.name);
            found.add(item);
        }

        if (count % 500 == 0) {
            int foundCount = found.size();
            runOnUiThread(() -> statusText.setText(formatInt(count) + " file WA diperiksa · " + formatInt(foundCount) + " kandidat"));
        }
    }

    private Category classify(File file) {
        String n = file.getName() == null ? "" : file.getName().toLowerCase(Locale.ROOT);
        if (isTempName(n)) return Category.TEMP;
        if (file.length() >= LARGE_BYTES) return Category.LARGE;
        return null;
    }

    private boolean isTempName(String n) {
        return n.endsWith(".tmp") || n.endsWith(".temp") || n.endsWith(".log")
                || n.endsWith(".bak") || n.endsWith(".old");
    }

    private boolean isOldTemp(Item item) {
        return item.category == Category.TEMP
                && item.modified > 0
                && System.currentTimeMillis() - item.modified >= TEMP_OLD_MS;
    }

    private void finishScan(List<Item> found, int scanned, boolean rootFound) {
        scanning.set(false);
        progress.setVisibility(View.GONE);
        scanButton.setEnabled(true);
        results.clear();
        results.addAll(found);

        if (!rootFound) {
            statusText.setText("Folder WhatsApp Business tidak ditemukan.");
            summaryText.setText("ACC Cleaner mencari folder modern com.whatsapp.w4b dan folder legacy WhatsApp Business.");
            setBulkEnabled(false);
            return;
        }

        int tmp = 0;
        int oldTmp = 0;
        int large = 0;
        long tmpBytes = 0L;
        long total = 0L;
        for (Item item : results) {
            total += item.size;
            if (item.category == Category.TEMP) {
                tmp++;
                tmpBytes += item.size;
                if (isOldTemp(item)) oldTmp++;
            } else if (item.category == Category.LARGE) {
                large++;
            }
        }

        statusText.setText("Scan selesai · " + formatInt(scanned) + " file WA diperiksa");
        summaryText.setText(formatInt(results.size()) + " kandidat · " + formatBytes(total)
                + "\nTMP/log: " + formatInt(tmp) + " · " + formatBytes(tmpBytes)
                + "\nTMP 7+ hari: " + formatInt(oldTmp)
                + "\nFile besar untuk review manual: " + formatInt(large));
        setBulkEnabled(!results.isEmpty());
        renderResults();
        updateSelectionUi();
    }

    private void failScan(Throwable t) {
        scanning.set(false);
        progress.setVisibility(View.GONE);
        scanButton.setEnabled(true);
        setBulkEnabled(false);
        statusText.setText("Scan gagal: " + safeMessage(t));
        summaryText.setText("Tidak ada hasil karena scan gagal.");
    }

    private void setBulkEnabled(boolean enabled) {
        oldTempButton.setEnabled(enabled);
        allTempButton.setEnabled(enabled);
        clearButton.setEnabled(enabled);
    }

    private void renderResults() {
        resultsBox.removeAllViews();
        if (results.isEmpty()) {
            resultsBox.addView(text("Tidak ada TMP/log atau file besar WhatsApp Business yang cocok dengan aturan review.", 12, muted, Typeface.NORMAL));
            return;
        }

        int shown = Math.min(DISPLAY_LIMIT, results.size());
        for (int i = 0; i < shown; i++) resultsBox.addView(resultRow(results.get(i)));

        if (results.size() > DISPLAY_LIMIT) {
            TextView limit = text("Menampilkan " + DISPLAY_LIMIT + " file terbesar dari " + formatInt(results.size())
                    + ". Bulk select tetap berlaku ke SEMUA file yang cocok, bukan hanya yang terlihat di layar.", 12, warning, Typeface.BOLD);
            LinearLayout.LayoutParams lp = matchWrap();
            lp.topMargin = dp(10);
            resultsBox.addView(limit, lp);
        }
    }

    private View resultRow(Item item) {
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.TOP);
        row.setPadding(0, dp(10), 0, dp(10));

        CheckBox box = new CheckBox(this);
        item.checkbox = box;
        box.setChecked(item.selected);
        box.setOnCheckedChangeListener((buttonView, checked) -> {
            item.selected = checked;
            updateSelectionUi();
        });
        row.addView(box, new LinearLayout.LayoutParams(dp(42), dp(42)));

        TextView badge = text(item.category == Category.TEMP ? "TMP" : "BIG", 10, primary, Typeface.BOLD);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(rounded(surface2, dp(10)));
        LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(dp(54), dp(54));
        badgeLp.setMargins(0, 0, dp(10), 0);
        row.addView(badge, badgeLp);

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        row.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView name = text(item.name, 14, text, Typeface.BOLD);
        name.setMaxLines(2);
        copy.addView(name);

        String age = item.modified > 0
                ? new SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault()).format(new Date(item.modified))
                : "tanggal tidak diketahui";
        TextView meta = text((item.category == Category.TEMP ? "Temporary / log" : "File besar")
                + " · " + formatBytes(item.size) + " · " + age, 11, primary, Typeface.BOLD);
        LinearLayout.LayoutParams metaLp = matchWrap();
        metaLp.topMargin = dp(2);
        copy.addView(meta, metaLp);

        TextView path = text(item.path, 10, muted, Typeface.NORMAL);
        path.setMaxLines(2);
        LinearLayout.LayoutParams pathLp = matchWrap();
        pathLp.topMargin = dp(2);
        copy.addView(path, pathLp);

        String riskText = item.category == Category.LARGE
                ? "PERIKSA DULU"
                : isOldTemp(item) ? "TMP LAMA · TETAP TINJAU" : "TMP BARU · HATI-HATI";
        int riskColor = item.category == Category.LARGE || !isOldTemp(item) ? danger : warning;
        TextView risk = text(riskText, 10, riskColor, Typeface.BOLD);
        LinearLayout.LayoutParams riskLp = matchWrap();
        riskLp.topMargin = dp(3);
        copy.addView(risk, riskLp);

        Button open = button("BUKA", false);
        open.setTextSize(10);
        LinearLayout.LayoutParams openLp = new LinearLayout.LayoutParams(dp(64), dp(40));
        openLp.setMargins(dp(6), 0, 0, 0);
        row.addView(open, openLp);
        open.setOnClickListener(v -> openItem(item));

        wrapper.addView(row, matchWrap());
        View divider = new View(this);
        divider.setBackgroundColor(Color.argb(28, 255, 255, 255));
        wrapper.addView(divider, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
        return wrapper;
    }

    private void bulkSelect(boolean oldOnly) {
        int count = 0;
        for (Item item : results) {
            item.selected = item.category == Category.TEMP && (!oldOnly || isOldTemp(item));
            if (item.selected) count++;
        }
        syncVisibleCheckboxes();
        updateSelectionUi();
        Toast.makeText(this, formatInt(count) + (oldOnly ? " TMP lama dipilih" : " TMP dipilih"), Toast.LENGTH_SHORT).show();
    }

    private void clearSelection() {
        for (Item item : results) item.selected = false;
        syncVisibleCheckboxes();
        updateSelectionUi();
    }

    private void syncVisibleCheckboxes() {
        for (Item item : results) {
            if (item.checkbox != null && item.checkbox.isChecked() != item.selected) {
                item.checkbox.setOnCheckedChangeListener(null);
                item.checkbox.setChecked(item.selected);
                item.checkbox.setOnCheckedChangeListener((buttonView, checked) -> {
                    item.selected = checked;
                    updateSelectionUi();
                });
            }
        }
    }

    private void updateSelectionUi() {
        int count = 0;
        int freshTemp = 0;
        int large = 0;
        long bytes = 0L;
        for (Item item : results) {
            if (!item.selected) continue;
            count++;
            bytes += item.size;
            if (item.category == Category.LARGE) large++;
            if (item.category == Category.TEMP && !isOldTemp(item)) freshTemp++;
        }

        if (count == 0) {
            selectedText.setText("Belum ada file dipilih.");
            selectedText.setTextColor(warning);
            deleteButton.setEnabled(false);
            deleteButton.setAlpha(0.4f);
        } else {
            selectedText.setText(formatInt(count) + " file dipilih · " + formatBytes(bytes)
                    + (freshTemp > 0 ? " · " + formatInt(freshTemp) + " TMP baru" : "")
                    + (large > 0 ? " · " + formatInt(large) + " file besar" : ""));
            selectedText.setTextColor(freshTemp > 0 || large > 0 ? danger : success);
            deleteButton.setEnabled(true);
            deleteButton.setAlpha(1f);
        }
    }

    private void confirmDelete() {
        List<Item> selected = selectedItems();
        if (selected.isEmpty()) return;

        long bytes = 0L;
        int oldTmp = 0;
        int freshTmp = 0;
        int large = 0;
        for (Item item : selected) {
            bytes += item.size;
            if (item.category == Category.LARGE) large++;
            else if (isOldTemp(item)) oldTmp++;
            else freshTmp++;
        }

        String message = "Terpilih: " + formatInt(selected.size()) + " file · " + formatBytes(bytes)
                + "\nTMP 7+ hari: " + formatInt(oldTmp)
                + "\nTMP baru / tanggal tidak diketahui: " + formatInt(freshTmp)
                + "\nFile besar: " + formatInt(large)
                + "\n\nACC Cleaner tidak memilih file secara otomatis.";

        long finalBytes = bytes;
        new AlertDialog.Builder(this)
                .setTitle("Periksa pilihan WA Business")
                .setMessage(message)
                .setNegativeButton("Batal", null)
                .setPositiveButton("Saya sudah periksa", (dialog, which) -> finalDeleteConfirmation(selected, finalBytes))
                .show();
    }

    private void finalDeleteConfirmation(List<Item> selected, long total) {
        StringBuilder names = new StringBuilder();
        int preview = Math.min(5, selected.size());
        for (int i = 0; i < preview; i++) names.append("• ").append(selected.get(i).name).append("\n");
        if (selected.size() > preview) names.append("• +").append(formatInt(selected.size() - preview)).append(" file lain\n");

        new AlertDialog.Builder(this)
                .setTitle("Hapus permanen dari WA Business?")
                .setMessage(names + "\nTotal: " + formatBytes(total)
                        + "\n\nTidak ada recycle bin di ACC Cleaner.")
                .setNegativeButton("Jangan hapus", null)
                .setPositiveButton("Hapus permanen", (dialog, which) -> deleteSelected(selected))
                .show();
    }

    private List<Item> selectedItems() {
        List<Item> selected = new ArrayList<>();
        for (Item item : results) if (item.selected) selected.add(item);
        return selected;
    }

    private void deleteSelected(List<Item> selected) {
        deleteButton.setEnabled(false);
        scanButton.setEnabled(false);
        statusText.setText("Menghapus " + formatInt(selected.size()) + " file pilihan…");
        progress.setVisibility(View.VISIBLE);

        executor.execute(() -> {
            int ok = 0;
            int failed = 0;
            long freed = 0L;
            for (Item item : selected) {
                boolean deleted = false;
                try {
                    deleted = item.file != null && item.file.delete();
                } catch (Throwable ignored) {
                }
                if (deleted) {
                    ok++;
                    freed += item.size;
                } else {
                    failed++;
                }
            }

            int okFinal = ok;
            int failedFinal = failed;
            long freedFinal = freed;
            runOnUiThread(() -> {
                progress.setVisibility(View.GONE);
                Toast.makeText(this, formatInt(okFinal) + " file terhapus · " + formatBytes(freedFinal) + " dibebaskan"
                        + (failedFinal > 0 ? " · " + formatInt(failedFinal) + " gagal" : ""), Toast.LENGTH_LONG).show();
                startScan();
            });
        });
    }

    private void openItem(Item item) {
        if (item.file == null || !item.file.exists()) {
            Toast.makeText(this, "File sudah tidak ada.", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", item.file);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, item.mime == null || item.mime.isBlank() ? "*/*" : item.mime);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.setClipData(ClipData.newRawUri("ACC Cleaner preview", uri));
            startActivity(Intent.createChooser(intent, "Buka file dengan"));
        } catch (Throwable t) {
            Toast.makeText(this, "Tidak ada aplikasi yang dapat membuka file ini.", Toast.LENGTH_LONG).show();
        }
    }

    private String mimeFromName(String name) {
        if (name == null) return "*/*";
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) return "*/*";
        String ext = name.substring(dot + 1).toLowerCase(Locale.ROOT);
        String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
        return mime == null ? "*/*" : mime;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        card.setBackground(rounded(surface, dp(18)));
        return card;
    }

    private Button button(String value, boolean filled) {
        Button b = new Button(this);
        b.setText(value);
        b.setTextSize(11);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setAllCaps(false);
        b.setTextColor(filled ? bg : primary);
        b.setBackground(rounded(filled ? primary : surface2, dp(13)));
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
        d.setStroke(dp(1), Color.argb(28, 255, 255, 255));
        return d;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double value = bytes;
        String[] units = {"KB", "MB", "GB", "TB"};
        int unit = -1;
        do {
            value /= 1024d;
            unit++;
        } while (value >= 1024d && unit < units.length - 1);
        DecimalFormat df = value >= 100 ? new DecimalFormat("0") : value >= 10 ? new DecimalFormat("0.0") : new DecimalFormat("0.00");
        return df.format(value) + " " + units[unit];
    }

    private String formatInt(int value) {
        return String.format(Locale.getDefault(), "%,d", value);
    }

    private String safeMessage(Throwable t) {
        return t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
    }

    private enum Category { TEMP, LARGE }

    private static final class Item {
        File file;
        String name;
        String path;
        String mime;
        long size;
        long modified;
        Category category;
        boolean selected;
        CheckBox checkbox;
    }
}
