package com.acc.cleaner;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.provider.DocumentsContract;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class MainActivity extends Activity {

    private static final int REQ_STORAGE = 4001;
    private static final int REQ_TREE = 4002;
    private static final long LARGE_FILE_BYTES = 100L * 1024L * 1024L;
    private static final long OLD_DOWNLOAD_AGE_MS = 30L * 24L * 60L * 60L * 1000L;
    private static final int DISPLAY_RESULT_LIMIT = 500;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean scanning = new AtomicBoolean(false);
    private final List<CleanItem> scanResults = new ArrayList<>();

    private LinearLayout root;
    private LinearLayout resultsContainer;
    private TextView storageValue;
    private TextView reclaimValue;
    private TextView accessValue;
    private TextView scanStatus;
    private TextView resultSummary;
    private ProgressBar storageProgress;
    private ProgressBar scanProgress;
    private Button scanButton;
    private Button deepAccessButton;
    private Button folderButton;
    private Button cleanButton;

    private int cBg;
    private int cSurface;
    private int cSurface2;
    private int cPrimary;
    private int cPrimary2;
    private int cText;
    private int cMuted;
    private int cSuccess;
    private int cWarning;
    private int cDanger;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        bindColors();
        buildUi();
        refreshStorage();
        refreshAccessState();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshAccessState();
        refreshStorage();
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private void bindColors() {
        cBg = getColor(R.color.bg);
        cSurface = getColor(R.color.surface);
        cSurface2 = getColor(R.color.surface_2);
        cPrimary = getColor(R.color.primary);
        cPrimary2 = getColor(R.color.primary_2);
        cText = getColor(R.color.text);
        cMuted = getColor(R.color.muted);
        cSuccess = getColor(R.color.success);
        cWarning = getColor(R.color.warning);
        cDanger = getColor(R.color.danger);
    }

    private void buildUi() {
        getWindow().setStatusBarColor(cBg);
        getWindow().setNavigationBarColor(cBg);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(cBg);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(36));
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);

        TextView brand = text("ACC CLEANER", 13, cPrimary2, Typeface.BOLD);
        brand.setLetterSpacing(0.18f);
        root.addView(brand);

        TextView title = text("Penyimpanan bersih, tanpa hapus sembarangan.", 26, cText, Typeface.BOLD);
        LinearLayout.LayoutParams titleLp = lpMatchWrap();
        titleLp.topMargin = dp(6);
        root.addView(title, titleLp);

        TextView subtitle = text("Scan • review • pilih • bersihkan", 14, cMuted, Typeface.NORMAL);
        LinearLayout.LayoutParams subtitleLp = lpMatchWrap();
        subtitleLp.topMargin = dp(5);
        root.addView(subtitle, subtitleLp);

        LinearLayout storageCard = card();
        LinearLayout.LayoutParams cardLp = lpMatchWrap();
        cardLp.topMargin = dp(20);
        root.addView(storageCard, cardLp);

        storageCard.addView(sectionLabel("STORAGE"));
        storageValue = text("Menghitung…", 22, cText, Typeface.BOLD);
        LinearLayout.LayoutParams svLp = lpMatchWrap();
        svLp.topMargin = dp(8);
        storageCard.addView(storageValue, svLp);

        storageProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        storageProgress.setMax(1000);
        storageProgress.setProgressTintList(android.content.res.ColorStateList.valueOf(cPrimary));
        storageProgress.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(cSurface2));
        LinearLayout.LayoutParams progressLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(8));
        progressLp.topMargin = dp(12);
        storageCard.addView(storageProgress, progressLp);

        LinearLayout reclaimRow = new LinearLayout(this);
        reclaimRow.setOrientation(LinearLayout.HORIZONTAL);
        reclaimRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rrLp = lpMatchWrap();
        rrLp.topMargin = dp(12);
        storageCard.addView(reclaimRow, rrLp);
        TextView recLabel = text("Potensi dibersihkan", 14, cMuted, Typeface.NORMAL);
        reclaimRow.addView(recLabel, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        reclaimValue = text("—", 15, cSuccess, Typeface.BOLD);
        reclaimRow.addView(reclaimValue);

        LinearLayout accessCard = card();
        LinearLayout.LayoutParams acLp = lpMatchWrap();
        acLp.topMargin = dp(12);
        root.addView(accessCard, acLp);
        accessCard.addView(sectionLabel("AKSES"));
        accessValue = text("Memeriksa akses…", 15, cText, Typeface.BOLD);
        LinearLayout.LayoutParams avLp = lpMatchWrap();
        avLp.topMargin = dp(8);
        accessCard.addView(accessValue, avLp);
        TextView accessInfo = text(
                "Deep Scan membaca penyimpanan bersama setelah izin khusus diberikan. Folder Scan hanya membaca folder yang kamu pilih.",
                13, cMuted, Typeface.NORMAL);
        LinearLayout.LayoutParams aiLp = lpMatchWrap();
        aiLp.topMargin = dp(6);
        accessCard.addView(accessInfo, aiLp);

        LinearLayout accessButtons = new LinearLayout(this);
        accessButtons.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams abLp = lpMatchWrap();
        abLp.topMargin = dp(12);
        accessCard.addView(accessButtons, abLp);

        deepAccessButton = actionButton("AKTIFKAN DEEP SCAN", false);
        accessButtons.addView(deepAccessButton, new LinearLayout.LayoutParams(0, dp(46), 1f));
        deepAccessButton.setOnClickListener(v -> requestDeepAccess());

        View gap = new View(this);
        accessButtons.addView(gap, new LinearLayout.LayoutParams(dp(8), 1));

        folderButton = actionButton("PILIH FOLDER", false);
        accessButtons.addView(folderButton, new LinearLayout.LayoutParams(0, dp(46), 1f));
        folderButton.setOnClickListener(v -> openFolderPicker());

        scanButton = actionButton("SCAN SEKARANG", true);
        LinearLayout.LayoutParams scanLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
        scanLp.topMargin = dp(14);
        root.addView(scanButton, scanLp);
        scanButton.setOnClickListener(v -> {
            if (hasDeepAccess()) {
                startDeepScan();
            } else {
                openFolderPicker();
            }
        });

        scanProgress = new ProgressBar(this);
        scanProgress.setIndeterminate(true);
        scanProgress.setVisibility(View.GONE);
        LinearLayout.LayoutParams spLp = new LinearLayout.LayoutParams(dp(28), dp(28));
        spLp.gravity = Gravity.CENTER_HORIZONTAL;
        spLp.topMargin = dp(18);
        root.addView(scanProgress, spLp);

        scanStatus = text("Belum ada scan.", 13, cMuted, Typeface.NORMAL);
        scanStatus.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams ssLp = lpMatchWrap();
        ssLp.topMargin = dp(8);
        root.addView(scanStatus, ssLp);

        LinearLayout resultsCard = card();
        LinearLayout.LayoutParams rcLp = lpMatchWrap();
        rcLp.topMargin = dp(16);
        root.addView(resultsCard, rcLp);
        resultsCard.addView(sectionLabel("HASIL SCAN"));
        resultSummary = text("Hasil akan tampil di sini setelah scan.", 14, cMuted, Typeface.NORMAL);
        LinearLayout.LayoutParams rsLp = lpMatchWrap();
        rsLp.topMargin = dp(8);
        resultsCard.addView(resultSummary, rsLp);

        resultsContainer = new LinearLayout(this);
        resultsContainer.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams listLp = lpMatchWrap();
        listLp.topMargin = dp(8);
        resultsCard.addView(resultsContainer, listLp);

        cleanButton = actionButton("BERSIHKAN YANG DIPILIH", true);
        cleanButton.setEnabled(false);
        cleanButton.setAlpha(0.45f);
        LinearLayout.LayoutParams cleanLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        cleanLp.topMargin = dp(14);
        resultsCard.addView(cleanButton, cleanLp);
        cleanButton.setOnClickListener(v -> confirmClean());

        TextView safety = text(
                "SAFE CLEAN • ACC Cleaner tidak menjalankan auto-delete. Semua file harus direview dan dipilih sebelum penghapusan.",
                11, cMuted, Typeface.BOLD);
        safety.setGravity(Gravity.CENTER);
        safety.setLetterSpacing(0.08f);
        LinearLayout.LayoutParams safeLp = lpMatchWrap();
        safeLp.topMargin = dp(18);
        root.addView(safety, safeLp);
    }

    private void refreshStorage() {
        try {
            File path = Environment.getExternalStorageDirectory();
            StatFs stat = new StatFs(path.getAbsolutePath());
            long total = stat.getTotalBytes();
            long free = stat.getAvailableBytes();
            long used = Math.max(0, total - free);
            int pct = total > 0 ? (int) ((used * 1000L) / total) : 0;
            storageValue.setText(formatBytes(used) + " / " + formatBytes(total) + " terpakai");
            storageProgress.setProgress(Math.max(0, Math.min(1000, pct)));
        } catch (Throwable t) {
            storageValue.setText("Storage tidak dapat dibaca");
        }
    }

    private void refreshAccessState() {
        boolean deep = hasDeepAccess();
        if (deep) {
            accessValue.setText("Deep Scan aktif");
            accessValue.setTextColor(cSuccess);
            deepAccessButton.setText("DEEP SCAN AKTIF");
            deepAccessButton.setEnabled(false);
            deepAccessButton.setAlpha(0.6f);
        } else {
            accessValue.setText("Mode aman: Folder Scan");
            accessValue.setTextColor(cWarning);
            deepAccessButton.setText("AKTIFKAN DEEP SCAN");
            deepAccessButton.setEnabled(true);
            deepAccessButton.setAlpha(1f);
        }
    }

    private boolean hasDeepAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        }
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
        } else {
            requestPermissions(new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            }, REQ_STORAGE);
        }
    }

    private void openFolderPicker() {
        if (scanning.get()) return;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        startActivityForResult(intent, REQ_TREE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_TREE && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri tree = data.getData();
            int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            try {
                getContentResolver().takePersistableUriPermission(tree, flags);
            } catch (SecurityException ignored) {
            }
            startTreeScan(tree);
        }
    }

    private void startDeepScan() {
        if (scanning.getAndSet(true)) return;
        prepareScanUi("Deep Scan berjalan…");
        executor.execute(() -> {
            ScanStats stats = new ScanStats();
            List<CleanItem> found = new ArrayList<>();
            try {
                scanFileTree(Environment.getExternalStorageDirectory(), found, stats);
                Collections.sort(found, Comparator.comparingLong((CleanItem i) -> i.size).reversed());
                finishScan(found, stats, "Deep Scan");
            } catch (Throwable t) {
                failScan("Scan gagal: " + safeMessage(t));
            }
        });
    }

    private void startTreeScan(Uri treeUri) {
        if (scanning.getAndSet(true)) return;
        prepareScanUi("Folder Scan berjalan…");
        executor.execute(() -> {
            ScanStats stats = new ScanStats();
            List<CleanItem> found = new ArrayList<>();
            try {
                String rootId = DocumentsContract.getTreeDocumentId(treeUri);
                Uri rootDoc = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootId);
                scanDocumentTree(rootDoc, treeUri, found, stats, 0);
                Collections.sort(found, Comparator.comparingLong((CleanItem i) -> i.size).reversed());
                finishScan(found, stats, "Folder Scan");
            } catch (Throwable t) {
                failScan("Folder scan gagal: " + safeMessage(t));
            }
        });
    }

    private void scanFileTree(File node, List<CleanItem> found, ScanStats stats) {
        if (Thread.currentThread().isInterrupted()) return;
        if (node == null || !node.exists()) return;

        if (node.isDirectory()) {
            String path = node.getAbsolutePath().replace('\\', '/').toLowerCase(Locale.ROOT);
            if (path.endsWith("/android/data") || path.endsWith("/android/obb")) return;
            File[] children;
            try {
                children = node.listFiles();
            } catch (SecurityException e) {
                return;
            }
            if (children == null) return;
            for (File child : children) {
                scanFileTree(child, found, stats);
            }
            return;
        }

        stats.filesScanned.incrementAndGet();
        long size = Math.max(0, node.length());
        String path = node.getAbsolutePath();
        Category category = classify(node.getName(), path, size, node.lastModified());
        if (category != null) {
            found.add(CleanItem.forFile(node, category));
            stats.add(category, size);
        }
        maybePublishScanProgress(stats.filesScanned.get(), found.size());
    }

    private void scanDocumentTree(Uri documentUri, Uri treeUri, List<CleanItem> found, ScanStats stats, int depth) {
        if (Thread.currentThread().isInterrupted() || depth > 64) return;
        String parentId = DocumentsContract.getDocumentId(documentUri);
        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId);
        ContentResolver resolver = getContentResolver();

        String[] projection = new String[]{
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                DocumentsContract.Document.COLUMN_SIZE
        };

        try (Cursor cursor = resolver.query(childrenUri, projection, null, null, null)) {
            if (cursor == null) return;
            int idCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID);
            int nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME);
            int mimeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE);
            int modCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED);
            int sizeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE);

            while (cursor.moveToNext()) {
                String id = cursor.getString(idCol);
                String name = cursor.getString(nameCol);
                String mime = cursor.getString(mimeCol);
                long modified = cursor.isNull(modCol) ? 0L : cursor.getLong(modCol);
                long size = cursor.isNull(sizeCol) ? 0L : cursor.getLong(sizeCol);
                Uri child = DocumentsContract.buildDocumentUriUsingTree(treeUri, id);

                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                    scanDocumentTree(child, treeUri, found, stats, depth + 1);
                } else {
                    stats.filesScanned.incrementAndGet();
                    Category category = classify(name, id, size, modified);
                    if (category != null) {
                        found.add(CleanItem.forDocument(child, name, id, size, modified, category));
                        stats.add(category, size);
                    }
                    maybePublishScanProgress(stats.filesScanned.get(), found.size());
                }
            }
        } catch (SecurityException ignored) {
        }
    }

    private Category classify(String name, String pathHint, long size, long modified) {
        String n = name == null ? "" : name.toLowerCase(Locale.ROOT);
        String p = pathHint == null ? "" : pathHint.replace('\\', '/').toLowerCase(Locale.ROOT);
        long age = modified > 0 ? System.currentTimeMillis() - modified : 0;

        if (n.endsWith(".apk")) return Category.APK;
        if (n.contains("screenshot") || p.contains("/screenshots/") || p.contains(":pictures/screenshots/")) {
            return Category.SCREENSHOT;
        }
        if ((p.contains("/download/") || p.contains(":download/")) && modified > 0 && age >= OLD_DOWNLOAD_AGE_MS) {
            return Category.OLD_DOWNLOAD;
        }
        if (n.endsWith(".tmp") || n.endsWith(".temp") || n.endsWith(".log") || n.endsWith(".bak") || n.endsWith(".old")) {
            return Category.TEMP;
        }
        if (size >= LARGE_FILE_BYTES) return Category.LARGE;
        return null;
    }

    private void maybePublishScanProgress(int scanned, int candidates) {
        if (scanned % 250 != 0) return;
        runOnUiThread(() -> scanStatus.setText(
                formatInt(scanned) + " file diperiksa • " + formatInt(candidates) + " kandidat"));
    }

    private void prepareScanUi(String status) {
        scanResults.clear();
        resultsContainer.removeAllViews();
        resultSummary.setText("Scan sedang berjalan…");
        reclaimValue.setText("Menghitung…");
        scanStatus.setText(status);
        scanProgress.setVisibility(View.VISIBLE);
        scanButton.setEnabled(false);
        scanButton.setAlpha(0.6f);
        folderButton.setEnabled(false);
        cleanButton.setEnabled(false);
        cleanButton.setAlpha(0.45f);
    }

    private void finishScan(List<CleanItem> found, ScanStats stats, String mode) {
        runOnUiThread(() -> {
            scanning.set(false);
            scanProgress.setVisibility(View.GONE);
            scanButton.setEnabled(true);
            scanButton.setAlpha(1f);
            folderButton.setEnabled(true);
            scanResults.clear();
            scanResults.addAll(found);
            reclaimValue.setText(formatBytes(stats.candidateBytes));
            scanStatus.setText(mode + " selesai • " + formatInt(stats.filesScanned.get()) + " file diperiksa");
            renderResults(stats);
        });
    }

    private void failScan(String message) {
        runOnUiThread(() -> {
            scanning.set(false);
            scanProgress.setVisibility(View.GONE);
            scanButton.setEnabled(true);
            scanButton.setAlpha(1f);
            folderButton.setEnabled(true);
            scanStatus.setText(message);
            resultSummary.setText("Tidak ada hasil karena scan gagal.");
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        });
    }

    private void renderResults(ScanStats stats) {
        resultsContainer.removeAllViews();
        if (scanResults.isEmpty()) {
            resultSummary.setText("Tidak ada kandidat pembersihan dari aturan ACC Cleaner v1.0.");
            cleanButton.setEnabled(false);
            cleanButton.setAlpha(0.45f);
            return;
        }

        resultSummary.setText(
                formatInt(scanResults.size()) + " kandidat • " + formatBytes(stats.candidateBytes)
                        + "\n" + stats.summary());

        int shown = Math.min(DISPLAY_RESULT_LIMIT, scanResults.size());
        for (int i = 0; i < shown; i++) {
            CleanItem item = scanResults.get(i);
            resultsContainer.addView(resultRow(item));
        }
        if (scanResults.size() > DISPLAY_RESULT_LIMIT) {
            TextView limit = text(
                    "Menampilkan " + DISPLAY_RESULT_LIMIT + " kandidat terbesar. Jalankan scan per-folder untuk review yang lebih rinci.",
                    12, cWarning, Typeface.BOLD);
            LinearLayout.LayoutParams lp = lpMatchWrap();
            lp.topMargin = dp(10);
            resultsContainer.addView(limit, lp);
        }
        cleanButton.setEnabled(true);
        cleanButton.setAlpha(1f);
    }

    private View resultRow(CleanItem item) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.TOP);
        row.setPadding(dp(4), dp(10), dp(4), dp(10));

        CheckBox box = new CheckBox(this);
        box.setButtonTintList(android.content.res.ColorStateList.valueOf(cPrimary));
        box.setTag(item);
        item.checkbox = box;
        row.addView(box, new LinearLayout.LayoutParams(dp(44), dp(44)));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        row.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView name = text(item.name, 14, cText, Typeface.BOLD);
        name.setMaxLines(2);
        copy.addView(name);

        TextView meta = text(item.category.label + " • " + formatBytes(item.size), 12, item.category.color(this), Typeface.BOLD);
        LinearLayout.LayoutParams metaLp = lpMatchWrap();
        metaLp.topMargin = dp(3);
        copy.addView(meta, metaLp);

        TextView path = text(item.pathHint, 11, cMuted, Typeface.NORMAL);
        path.setMaxLines(2);
        LinearLayout.LayoutParams pathLp = lpMatchWrap();
        pathLp.topMargin = dp(2);
        copy.addView(path, pathLp);

        View divider = new View(this);
        divider.setBackgroundColor(Color.argb(30, 255, 255, 255));
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.addView(row, lpMatchWrap());
        wrapper.addView(divider, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
        return wrapper;
    }

    private void confirmClean() {
        List<CleanItem> selected = new ArrayList<>();
        long selectedBytes = 0L;
        for (CleanItem item : scanResults) {
            if (item.checkbox != null && item.checkbox.isChecked()) {
                selected.add(item);
                selectedBytes += item.size;
            }
        }
        if (selected.isEmpty()) {
            Toast.makeText(this, "Pilih file yang ingin dibersihkan.", Toast.LENGTH_SHORT).show();
            return;
        }
        final long bytes = selectedBytes;
        new AlertDialog.Builder(this)
                .setTitle("Hapus " + selected.size() + " file?")
                .setMessage("Total " + formatBytes(bytes) + ". Tindakan ini permanen untuk file yang dipilih.")
                .setNegativeButton("Batal", null)
                .setPositiveButton("Hapus", (dialog, which) -> deleteSelected(selected))
                .show();
    }

    private void deleteSelected(List<CleanItem> selected) {
        cleanButton.setEnabled(false);
        cleanButton.setAlpha(0.6f);
        scanStatus.setText("Membersihkan file terpilih…");
        executor.execute(() -> {
            int ok = 0;
            int failed = 0;
            long freed = 0L;
            for (CleanItem item : selected) {
                boolean deleted = false;
                try {
                    if (item.file != null) {
                        deleted = item.file.delete();
                    } else if (item.uri != null) {
                        deleted = DocumentsContract.deleteDocument(getContentResolver(), item.uri);
                    }
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
                scanStatus.setText("Selesai • " + okFinal + " terhapus • " + formatBytes(freedFinal) + " dibebaskan");
                Toast.makeText(this,
                        failedFinal == 0 ? "Pembersihan selesai." : (failedFinal + " file tidak dapat dihapus."),
                        Toast.LENGTH_LONG).show();
                refreshStorage();
                if (hasDeepAccess()) startDeepScan();
                else {
                    cleanButton.setEnabled(true);
                    cleanButton.setAlpha(1f);
                }
            });
        });
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(cSurface);
        bg.setCornerRadius(dp(18));
        bg.setStroke(dp(1), Color.argb(28, 255, 255, 255));
        card.setBackground(bg);
        return card;
    }

    private TextView sectionLabel(String s) {
        TextView t = text(s, 11, cPrimary2, Typeface.BOLD);
        t.setLetterSpacing(0.14f);
        return t;
    }

    private Button actionButton(String label, boolean primary) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(12);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setAllCaps(false);
        b.setTextColor(primary ? cBg : cPrimary2);
        b.setPadding(dp(10), 0, dp(10), 0);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(primary ? cPrimary : cSurface2);
        bg.setCornerRadius(dp(14));
        if (!primary) bg.setStroke(dp(1), Color.argb(90, 78, 167, 255));
        b.setBackground(bg);
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

    private LinearLayout.LayoutParams lpMatchWrap() {
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
        String m = t.getMessage();
        return (m == null || m.isBlank()) ? t.getClass().getSimpleName() : m;
    }

    private enum Category {
        LARGE("File besar"),
        OLD_DOWNLOAD("Download lama"),
        SCREENSHOT("Screenshot"),
        APK("Installer APK"),
        TEMP("File sementara/log");

        final String label;

        Category(String label) {
            this.label = label;
        }

        int color(MainActivity a) {
            return switch (this) {
                case TEMP -> a.cSuccess;
                case APK -> a.cWarning;
                case LARGE -> a.cPrimary2;
                case SCREENSHOT -> a.cPrimary;
                case OLD_DOWNLOAD -> a.cWarning;
            };
        }
    }

    private static class CleanItem {
        final File file;
        final Uri uri;
        final String name;
        final String pathHint;
        final long size;
        final long modified;
        final Category category;
        CheckBox checkbox;

        private CleanItem(File file, Uri uri, String name, String pathHint, long size, long modified, Category category) {
            this.file = file;
            this.uri = uri;
            this.name = name == null || name.isBlank() ? "Tanpa nama" : name;
            this.pathHint = pathHint == null ? "" : pathHint;
            this.size = size;
            this.modified = modified;
            this.category = category;
        }

        static CleanItem forFile(File file, Category category) {
            return new CleanItem(file, null, file.getName(), file.getAbsolutePath(), file.length(), file.lastModified(), category);
        }

        static CleanItem forDocument(Uri uri, String name, String id, long size, long modified, Category category) {
            return new CleanItem(null, uri, name, id, size, modified, category);
        }
    }

    private static class ScanStats {
        final AtomicInteger filesScanned = new AtomicInteger();
        int large;
        int oldDownloads;
        int screenshots;
        int apk;
        int temp;
        long candidateBytes;

        synchronized void add(Category category, long bytes) {
            candidateBytes += Math.max(0, bytes);
            switch (category) {
                case LARGE -> large++;
                case OLD_DOWNLOAD -> oldDownloads++;
                case SCREENSHOT -> screenshots++;
                case APK -> apk++;
                case TEMP -> temp++;
            }
        }

        String summary() {
            return "File besar " + large
                    + " • Download lama " + oldDownloads
                    + " • Screenshot " + screenshots
                    + " • APK " + apk
                    + " • Temp/log " + temp;
        }
    }
}
