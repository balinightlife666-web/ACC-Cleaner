package com.acc.cleaner;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.Context;
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
import android.view.WindowInsets;
import android.webkit.MimeTypeMap;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

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

public class SafeMainActivity extends Activity {

    private static final int REQ_STORAGE = 5001;
    private static final int REQ_TREE = 5002;
    private static final long LARGE_FILE_BYTES = 100L * 1024L * 1024L;
    private static final long OLD_DOWNLOAD_AGE_MS = 30L * 24L * 60L * 60L * 1000L;
    private static final long TEMP_REVIEW_AGE_MS = 7L * 24L * 60L * 60L * 1000L;
    private static final int DISPLAY_RESULT_LIMIT = 500;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean scanning = new AtomicBoolean(false);
    private final List<ReviewItem> scanResults = new ArrayList<>();

    private LinearLayout root;
    private LinearLayout resultsContainer;
    private TextView storageValue;
    private TextView reviewValue;
    private TextView accessValue;
    private TextView scanStatus;
    private TextView resultSummary;
    private ProgressBar storageProgress;
    private ProgressBar scanProgress;
    private Button scanButton;
    private Button deepAccessButton;
    private Button folderButton;
    private Button deleteButton;

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
        root.setPadding(dp(18), dp(18), dp(18), dp(24));
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);

        scroll.setOnApplyWindowInsetsListener((v, insets) -> {
            int bottomInset;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                bottomInset = insets.getInsets(WindowInsets.Type.systemBars()).bottom;
            } else {
                bottomInset = insets.getSystemWindowInsetBottom();
            }
            root.setPadding(dp(18), dp(18), dp(18), dp(24) + bottomInset);
            return insets;
        });
        scroll.requestApplyInsets();

        TextView brand = text("ACC CLEANER", 13, cPrimary2, Typeface.BOLD);
        brand.setLetterSpacing(0.18f);
        root.addView(brand);

        TextView title = text("Bersihkan setelah tahu file-nya.", 26, cText, Typeface.BOLD);
        LinearLayout.LayoutParams titleLp = lpMatchWrap();
        titleLp.topMargin = dp(6);
        root.addView(title, titleLp);

        TextView subtitle = text("Scan • identifikasi • review • pilih • hapus", 14, cMuted, Typeface.NORMAL);
        LinearLayout.LayoutParams subtitleLp = lpMatchWrap();
        subtitleLp.topMargin = dp(5);
        root.addView(subtitle, subtitleLp);

        LinearLayout safetyCard = card();
        LinearLayout.LayoutParams safetyLp = lpMatchWrap();
        safetyLp.topMargin = dp(16);
        root.addView(safetyCard, safetyLp);
        safetyCard.addView(sectionLabel("SAFETY REVIEW v1.0.2"));
        TextView safetyText = text(
                "File besar BUKAN sampah. Video, foto, audio, dokumen, screenshot, APK, dan file tidak dikenal selalu wajib diperiksa dulu. ACC Cleaner tidak memilih file otomatis.",
                13, cWarning, Typeface.BOLD);
        LinearLayout.LayoutParams safetyTextLp = lpMatchWrap();
        safetyTextLp.topMargin = dp(8);
        safetyCard.addView(safetyText, safetyTextLp);

        LinearLayout storageCard = card();
        LinearLayout.LayoutParams storageLp = lpMatchWrap();
        storageLp.topMargin = dp(12);
        root.addView(storageCard, storageLp);
        storageCard.addView(sectionLabel("STORAGE"));

        storageValue = text("Menghitung…", 22, cText, Typeface.BOLD);
        LinearLayout.LayoutParams storageValueLp = lpMatchWrap();
        storageValueLp.topMargin = dp(8);
        storageCard.addView(storageValue, storageValueLp);

        storageProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        storageProgress.setMax(1000);
        storageProgress.setProgressTintList(android.content.res.ColorStateList.valueOf(cPrimary));
        storageProgress.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(cSurface2));
        LinearLayout.LayoutParams progressLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(8));
        progressLp.topMargin = dp(12);
        storageCard.addView(storageProgress, progressLp);

        LinearLayout reviewRow = new LinearLayout(this);
        reviewRow.setOrientation(LinearLayout.HORIZONTAL);
        reviewRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams reviewRowLp = lpMatchWrap();
        reviewRowLp.topMargin = dp(12);
        storageCard.addView(reviewRow, reviewRowLp);

        TextView reviewLabel = text("Ukuran file perlu ditinjau", 14, cMuted, Typeface.NORMAL);
        reviewRow.addView(reviewLabel, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        reviewValue = text("—", 15, cWarning, Typeface.BOLD);
        reviewRow.addView(reviewValue);

        LinearLayout accessCard = card();
        LinearLayout.LayoutParams accessLp = lpMatchWrap();
        accessLp.topMargin = dp(12);
        root.addView(accessCard, accessLp);
        accessCard.addView(sectionLabel("AKSES"));

        accessValue = text("Memeriksa akses…", 15, cText, Typeface.BOLD);
        LinearLayout.LayoutParams accessValueLp = lpMatchWrap();
        accessValueLp.topMargin = dp(8);
        accessCard.addView(accessValue, accessValueLp);

        TextView accessInfo = text(
                "Deep Scan membaca penyimpanan bersama. Folder Scan hanya membaca folder yang kamu pilih.",
                13, cMuted, Typeface.NORMAL);
        LinearLayout.LayoutParams accessInfoLp = lpMatchWrap();
        accessInfoLp.topMargin = dp(6);
        accessCard.addView(accessInfo, accessInfoLp);

        LinearLayout accessButtons = new LinearLayout(this);
        accessButtons.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams buttonsLp = lpMatchWrap();
        buttonsLp.topMargin = dp(12);
        accessCard.addView(accessButtons, buttonsLp);

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
            if (hasDeepAccess()) startDeepScan();
            else openFolderPicker();
        });

        scanProgress = new ProgressBar(this);
        scanProgress.setIndeterminate(true);
        scanProgress.setVisibility(View.GONE);
        LinearLayout.LayoutParams scanProgressLp = new LinearLayout.LayoutParams(dp(28), dp(28));
        scanProgressLp.gravity = Gravity.CENTER_HORIZONTAL;
        scanProgressLp.topMargin = dp(18);
        root.addView(scanProgress, scanProgressLp);

        scanStatus = text("Belum ada scan.", 13, cMuted, Typeface.NORMAL);
        scanStatus.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statusLp = lpMatchWrap();
        statusLp.topMargin = dp(8);
        root.addView(scanStatus, statusLp);

        LinearLayout resultsCard = card();
        LinearLayout.LayoutParams resultsCardLp = lpMatchWrap();
        resultsCardLp.topMargin = dp(16);
        root.addView(resultsCard, resultsCardLp);
        resultsCard.addView(sectionLabel("HASIL REVIEW"));

        resultSummary = text("Hasil akan tampil di sini setelah scan.", 14, cMuted, Typeface.NORMAL);
        LinearLayout.LayoutParams resultSummaryLp = lpMatchWrap();
        resultSummaryLp.topMargin = dp(8);
        resultsCard.addView(resultSummary, resultSummaryLp);

        resultsContainer = new LinearLayout(this);
        resultsContainer.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams listLp = lpMatchWrap();
        listLp.topMargin = dp(8);
        resultsCard.addView(resultsContainer, listLp);

        deleteButton = actionButton("HAPUS FILE TERPILIH", true);
        deleteButton.setEnabled(false);
        deleteButton.setAlpha(0.45f);
        LinearLayout.LayoutParams deleteLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        deleteLp.topMargin = dp(14);
        resultsCard.addView(deleteButton, deleteLp);
        deleteButton.setOnClickListener(v -> confirmDelete());

        TextView footer = text(
                "TAP NAMA FILE UNTUK DETAIL • Tidak ada auto-delete • Tidak ada auto-select • Penghapusan file penting memakai konfirmasi ganda.",
                11, cMuted, Typeface.BOLD);
        footer.setGravity(Gravity.CENTER);
        footer.setLetterSpacing(0.06f);
        LinearLayout.LayoutParams footerLp = lpMatchWrap();
        footerLp.topMargin = dp(14);
        root.addView(footer, footerLp);
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
            ReviewStats stats = new ReviewStats();
            List<ReviewItem> found = new ArrayList<>();
            try {
                scanFileTree(Environment.getExternalStorageDirectory(), found, stats);
                Collections.sort(found, Comparator.comparingLong((ReviewItem i) -> i.size).reversed());
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
            ReviewStats stats = new ReviewStats();
            List<ReviewItem> found = new ArrayList<>();
            try {
                String rootId = DocumentsContract.getTreeDocumentId(treeUri);
                Uri rootDoc = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootId);
                scanDocumentTree(rootDoc, treeUri, found, stats, 0);
                Collections.sort(found, Comparator.comparingLong((ReviewItem i) -> i.size).reversed());
                finishScan(found, stats, "Folder Scan");
            } catch (Throwable t) {
                failScan("Folder scan gagal: " + safeMessage(t));
            }
        });
    }

    private void scanFileTree(File node, List<ReviewItem> found, ReviewStats stats) {
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
            for (File child : children) scanFileTree(child, found, stats);
            return;
        }

        stats.filesScanned.incrementAndGet();
        long size = Math.max(0, node.length());
        String path = node.getAbsolutePath();
        Category category = classify(node.getName(), path, size, node.lastModified());
        if (category != null) {
            ReviewItem item = ReviewItem.forFile(node, category, mimeFromName(node.getName()));
            enrich(item);
            found.add(item);
            stats.add(item);
        }
        maybePublishScanProgress(stats.filesScanned.get(), found.size());
    }

    private void scanDocumentTree(Uri documentUri, Uri treeUri, List<ReviewItem> found, ReviewStats stats, int depth) {
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
                        ReviewItem item = ReviewItem.forDocument(child, name, id, size, modified, category, mime);
                        enrich(item);
                        found.add(item);
                        stats.add(item);
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
        if (isTempName(n)) return Category.TEMP;
        if (size >= LARGE_FILE_BYTES) return Category.LARGE;
        return null;
    }

    private void enrich(ReviewItem item) {
        item.kind = kindFromName(item.name, item.mime);
        item.source = sourceFromPath(item.pathHint);
        item.risk = riskFor(item);
        item.riskReason = riskReason(item);
    }

    private Risk riskFor(ReviewItem item) {
        String kind = item.kind.toLowerCase(Locale.ROOT);
        if (kind.contains("video") || kind.contains("gambar") || kind.contains("audio") || kind.contains("dokumen")) {
            return Risk.HIGH;
        }
        if (item.category == Category.SCREENSHOT || item.category == Category.OLD_DOWNLOAD || item.category == Category.APK) {
            return Risk.HIGH;
        }
        if (item.category == Category.TEMP) {
            long age = item.modified > 0 ? System.currentTimeMillis() - item.modified : 0L;
            return age >= TEMP_REVIEW_AGE_MS ? Risk.CAUTION : Risk.HIGH;
        }
        return Risk.CAUTION;
    }

    private String riskReason(ReviewItem item) {
        String kind = item.kind.toLowerCase(Locale.ROOT);
        if (kind.contains("video") || kind.contains("gambar") || kind.contains("audio") || kind.contains("dokumen")) {
            return "Konten ini bisa merupakan file pribadi atau penting. Ukuran besar tidak berarti sampah.";
        }
        if (item.category == Category.SCREENSHOT) {
            return "Screenshot bisa berisi bukti, tiket, kode, percakapan, atau informasi penting.";
        }
        if (item.category == Category.OLD_DOWNLOAD) {
            return "File lama di Download tidak otomatis tidak berguna. Periksa nama, tipe, dan sumbernya.";
        }
        if (item.category == Category.APK) {
            return "Ini installer Android. Hapus hanya jika kamu yakin installer tersebut tidak diperlukan lagi.";
        }
        if (item.category == Category.TEMP) {
            long age = item.modified > 0 ? System.currentTimeMillis() - item.modified : 0L;
            if (age < TEMP_REVIEW_AGE_MS) {
                return "File sementara ini masih baru dan mungkin sedang dipakai aplikasi. Jangan hapus dulu bila sumbernya belum jelas.";
            }
            return "Kemungkinan file sementara lama, tetapi tetap periksa sumber aplikasinya sebelum menghapus.";
        }
        return "Nama dan ukuran file saja tidak cukup untuk menentukan aman dihapus.";
    }

    private void maybePublishScanProgress(int scanned, int found) {
        if (scanned % 250 != 0) return;
        runOnUiThread(() -> scanStatus.setText(
                formatInt(scanned) + " file diperiksa • " + formatInt(found) + " perlu review"));
    }

    private void prepareScanUi(String status) {
        scanResults.clear();
        resultsContainer.removeAllViews();
        resultSummary.setText("Scan sedang berjalan…");
        reviewValue.setText("Menghitung…");
        scanStatus.setText(status);
        scanProgress.setVisibility(View.VISIBLE);
        scanButton.setEnabled(false);
        scanButton.setAlpha(0.6f);
        folderButton.setEnabled(false);
        deleteButton.setEnabled(false);
        deleteButton.setAlpha(0.45f);
    }

    private void finishScan(List<ReviewItem> found, ReviewStats stats, String mode) {
        runOnUiThread(() -> {
            scanning.set(false);
            scanProgress.setVisibility(View.GONE);
            scanButton.setEnabled(true);
            scanButton.setAlpha(1f);
            folderButton.setEnabled(true);
            scanResults.clear();
            scanResults.addAll(found);
            reviewValue.setText(formatBytes(stats.reviewBytes));
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

    private void renderResults(ReviewStats stats) {
        resultsContainer.removeAllViews();
        if (scanResults.isEmpty()) {
            resultSummary.setText("Tidak ada file yang masuk aturan review ACC Cleaner v1.0.2.");
            deleteButton.setEnabled(false);
            deleteButton.setAlpha(0.45f);
            return;
        }

        resultSummary.setText(
                formatInt(scanResults.size()) + " file perlu review • " + formatBytes(stats.reviewBytes)
                        + "\n" + stats.summary()
                        + "\nTap nama file untuk melihat detail sebelum memilih.");

        int shown = Math.min(DISPLAY_RESULT_LIMIT, scanResults.size());
        for (int i = 0; i < shown; i++) {
            resultsContainer.addView(resultRow(scanResults.get(i)));
        }

        if (scanResults.size() > DISPLAY_RESULT_LIMIT) {
            TextView limit = text(
                    "Menampilkan " + DISPLAY_RESULT_LIMIT + " file terbesar. Gunakan Folder Scan untuk review yang lebih rinci.",
                    12, cWarning, Typeface.BOLD);
            LinearLayout.LayoutParams lp = lpMatchWrap();
            lp.topMargin = dp(10);
            resultsContainer.addView(limit, lp);
        }
        updateDeleteButtonState();
    }

    private View resultRow(ReviewItem item) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.TOP);
        row.setPadding(dp(4), dp(10), dp(4), dp(10));

        CheckBox box = new CheckBox(this);
        box.setButtonTintList(android.content.res.ColorStateList.valueOf(cPrimary));
        item.checkbox = box;
        box.setOnCheckedChangeListener((buttonView, isChecked) -> updateDeleteButtonState());
        row.addView(box, new LinearLayout.LayoutParams(dp(44), dp(44)));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setClickable(true);
        copy.setFocusable(true);
        copy.setOnClickListener(v -> showDetails(item));
        row.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView name = text(item.name, 14, cText, Typeface.BOLD);
        name.setMaxLines(2);
        copy.addView(name);

        TextView type = text(item.kind + " • " + formatBytes(item.size), 12, cPrimary2, Typeface.BOLD);
        LinearLayout.LayoutParams typeLp = lpMatchWrap();
        typeLp.topMargin = dp(3);
        copy.addView(type, typeLp);

        TextView risk = text(item.risk.label, 11, item.risk.color(this), Typeface.BOLD);
        LinearLayout.LayoutParams riskLp = lpMatchWrap();
        riskLp.topMargin = dp(2);
        copy.addView(risk, riskLp);

        TextView source = text("Sumber: " + item.source, 11, cMuted, Typeface.NORMAL);
        LinearLayout.LayoutParams sourceLp = lpMatchWrap();
        sourceLp.topMargin = dp(2);
        copy.addView(source, sourceLp);

        TextView path = text(item.pathHint, 10, cMuted, Typeface.NORMAL);
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

    private void showDetails(ReviewItem item) {
        String modified = item.modified > 0 ? formatDate(item.modified) : "Tidak diketahui";
        String mime = item.mime == null || item.mime.isBlank() ? "Tidak diketahui" : item.mime;
        String message = "Jenis: " + item.kind
                + "\nKategori scan: " + item.category.label
                + "\nSumber: " + item.source
                + "\nUkuran: " + formatBytes(item.size)
                + "\nTerakhir diubah: " + modified
                + "\nMIME: " + mime
                + "\n\nSTATUS: " + item.risk.label
                + "\n" + item.riskReason
                + "\n\nLokasi:\n" + item.pathHint;

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(item.name)
                .setMessage(message)
                .setPositiveButton("Tutup", null)
                .setNeutralButton("Salin lokasi", (dialog, which) -> copyLocation(item.pathHint));

        if (item.uri != null) {
            builder.setNegativeButton("Buka file", (dialog, which) -> openDocument(item));
        }
        builder.show();
    }

    private void copyLocation(String path) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("ACC Cleaner file location", path));
        Toast.makeText(this, "Lokasi file disalin.", Toast.LENGTH_SHORT).show();
    }

    private void openDocument(ReviewItem item) {
        if (item.uri == null) return;
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(item.uri, item.mime == null || item.mime.isBlank() ? "*/*" : item.mime);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "Buka file dengan"));
        } catch (Throwable t) {
            Toast.makeText(this, "File tidak dapat dibuka dari izin saat ini.", Toast.LENGTH_LONG).show();
        }
    }

    private void updateDeleteButtonState() {
        boolean anySelected = false;
        for (ReviewItem item : scanResults) {
            if (item.checkbox != null && item.checkbox.isChecked()) {
                anySelected = true;
                break;
            }
        }
        deleteButton.setEnabled(anySelected);
        deleteButton.setAlpha(anySelected ? 1f : 0.45f);
    }

    private void confirmDelete() {
        List<ReviewItem> selected = new ArrayList<>();
        long selectedBytes = 0L;
        int highRisk = 0;
        for (ReviewItem item : scanResults) {
            if (item.checkbox != null && item.checkbox.isChecked()) {
                selected.add(item);
                selectedBytes += item.size;
                if (item.risk == Risk.HIGH) highRisk++;
            }
        }

        if (selected.isEmpty()) {
            Toast.makeText(this, "Pilih file yang ingin dihapus.", Toast.LENGTH_SHORT).show();
            return;
        }

        final long bytes = selectedBytes;
        final int importantCount = highRisk;
        String message = "Kamu memilih " + selected.size() + " file (" + formatBytes(bytes) + ").";
        if (importantCount > 0) {
            message += "\n\n" + importantCount + " file berstatus PERIKSA DULU. Bisa berupa video, foto, audio, dokumen, screenshot, download lama, atau APK penting.";
        }
        message += "\n\nPenghapusan bersifat permanen.";

        new AlertDialog.Builder(this)
                .setTitle(importantCount > 0 ? "PERIKSA LAGI SEBELUM HAPUS" : "Konfirmasi penghapusan")
                .setMessage(message)
                .setNegativeButton("Batal", null)
                .setPositiveButton("Saya sudah periksa", (dialog, which) -> finalDeleteConfirmation(selected, bytes))
                .show();
    }

    private void finalDeleteConfirmation(List<ReviewItem> selected, long bytes) {
        StringBuilder names = new StringBuilder();
        int preview = Math.min(4, selected.size());
        for (int i = 0; i < preview; i++) {
            if (i > 0) names.append("\n");
            names.append("• ").append(selected.get(i).name);
        }
        if (selected.size() > preview) names.append("\n• +").append(selected.size() - preview).append(" file lain");

        new AlertDialog.Builder(this)
                .setTitle("Hapus permanen?")
                .setMessage(names + "\n\nTotal: " + formatBytes(bytes) + "\n\nTidak ada recycle bin di versi ini.")
                .setNegativeButton("Jangan hapus", null)
                .setPositiveButton("Hapus permanen", (dialog, which) -> deleteSelected(selected))
                .show();
    }

    private void deleteSelected(List<ReviewItem> selected) {
        deleteButton.setEnabled(false);
        deleteButton.setAlpha(0.6f);
        scanStatus.setText("Menghapus file terpilih…");
        executor.execute(() -> {
            int ok = 0;
            int failed = 0;
            long freed = 0L;
            for (ReviewItem item : selected) {
                boolean deleted = false;
                try {
                    if (item.file != null) deleted = item.file.delete();
                    else if (item.uri != null) deleted = DocumentsContract.deleteDocument(getContentResolver(), item.uri);
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
                        failedFinal == 0 ? "Penghapusan selesai." : (failedFinal + " file tidak dapat dihapus."),
                        Toast.LENGTH_LONG).show();
                refreshStorage();
                if (hasDeepAccess()) startDeepScan();
                else {
                    deleteButton.setEnabled(false);
                    deleteButton.setAlpha(0.45f);
                    resultSummary.setText("Penghapusan selesai. Jalankan Folder Scan lagi untuk memperbarui hasil.");
                }
            });
        });
    }

    private boolean isTempName(String lowerName) {
        return lowerName.endsWith(".tmp")
                || lowerName.endsWith(".temp")
                || lowerName.endsWith(".log")
                || lowerName.endsWith(".bak")
                || lowerName.endsWith(".old");
    }

    private String kindFromName(String name, String mime) {
        String n = name == null ? "" : name.toLowerCase(Locale.ROOT);
        String m = mime == null ? "" : mime.toLowerCase(Locale.ROOT);
        if (m.startsWith("video/") || endsWithAny(n, ".mp4", ".mkv", ".mov", ".avi", ".webm", ".3gp")) return "Video";
        if (m.startsWith("image/") || endsWithAny(n, ".jpg", ".jpeg", ".png", ".webp", ".heic", ".gif")) return "Gambar";
        if (m.startsWith("audio/") || endsWithAny(n, ".mp3", ".m4a", ".wav", ".aac", ".flac", ".ogg")) return "Audio";
        if (m.equals("application/vnd.android.package-archive") || n.endsWith(".apk")) return "Installer Android (APK)";
        if (m.startsWith("text/") || endsWithAny(n, ".pdf", ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx", ".txt", ".csv")) return "Dokumen";
        if (endsWithAny(n, ".zip", ".rar", ".7z", ".tar", ".gz")) return "Arsip";
        if (isTempName(n)) {
            if (n.endsWith(".enc.tmp")) return "File sementara terenkripsi";
            return "File sementara / log";
        }
        int dot = n.lastIndexOf('.');
        if (dot >= 0 && dot < n.length() - 1) return "File ." + n.substring(dot + 1).toUpperCase(Locale.ROOT);
        return "Tipe tidak dikenal";
    }

    private String sourceFromPath(String path) {
        String p = path == null ? "" : path.replace('\\', '/').toLowerCase(Locale.ROOT);
        if (p.contains("com.whatsapp.w4b") || p.contains("whatsapp business")) return "WhatsApp Business";
        if (p.contains("com.whatsapp") || p.contains("/whatsapp/")) return "WhatsApp";
        if (p.contains("org.telegram") || p.contains("/telegram/")) return "Telegram";
        if (p.contains("/dcim/camera") || p.contains(":dcim/camera")) return "Kamera HP";
        if (p.contains("/screenshots/") || p.contains(":pictures/screenshots")) return "Screenshot HP";
        if (p.contains("/download/") || p.contains(":download/")) return "Folder Download";
        if (p.contains("/myalbums/")) return "Album pribadi";
        if (p.contains("/movies/")) return "Folder Movies";
        if (p.contains("/pictures/")) return "Folder Pictures";
        if (p.contains("/music/")) return "Folder Music";
        if (p.contains("/documents/")) return "Folder Documents";
        if (p.contains("/android/media/")) return "Media aplikasi Android";
        return "Penyimpanan perangkat";
    }

    private String mimeFromName(String name) {
        if (name == null) return "application/octet-stream";
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) return "application/octet-stream";
        String ext = name.substring(dot + 1).toLowerCase(Locale.ROOT);
        String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
        return mime == null ? "application/octet-stream" : mime;
    }

    private boolean endsWithAny(String value, String... endings) {
        for (String ending : endings) if (value.endsWith(ending)) return true;
        return false;
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

    private TextView sectionLabel(String value) {
        TextView t = text(value, 11, cPrimary2, Typeface.BOLD);
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

    private String formatDate(long timestamp) {
        return new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(new Date(timestamp));
    }

    private String safeMessage(Throwable t) {
        String message = t.getMessage();
        return (message == null || message.isBlank()) ? t.getClass().getSimpleName() : message;
    }

    private enum Category {
        LARGE("File besar"),
        OLD_DOWNLOAD("Download lama"),
        SCREENSHOT("Screenshot"),
        APK("Installer APK"),
        TEMP("File sementara / log");

        final String label;

        Category(String label) {
            this.label = label;
        }
    }

    private enum Risk {
        HIGH("PERIKSA DULU — BISA PENTING"),
        CAUTION("PERLU DITINJAU — JANGAN HAPUS OTOMATIS");

        final String label;

        Risk(String label) {
            this.label = label;
        }

        int color(SafeMainActivity activity) {
            return this == HIGH ? activity.cDanger : activity.cWarning;
        }
    }

    private static class ReviewItem {
        final File file;
        final Uri uri;
        final String name;
        final String pathHint;
        final long size;
        final long modified;
        final Category category;
        final String mime;
        String kind;
        String source;
        Risk risk;
        String riskReason;
        CheckBox checkbox;

        private ReviewItem(File file, Uri uri, String name, String pathHint, long size, long modified, Category category, String mime) {
            this.file = file;
            this.uri = uri;
            this.name = name == null || name.isBlank() ? "Tanpa nama" : name;
            this.pathHint = pathHint == null ? "" : pathHint;
            this.size = size;
            this.modified = modified;
            this.category = category;
            this.mime = mime;
        }

        static ReviewItem forFile(File file, Category category, String mime) {
            return new ReviewItem(file, null, file.getName(), file.getAbsolutePath(), file.length(), file.lastModified(), category, mime);
        }

        static ReviewItem forDocument(Uri uri, String name, String id, long size, long modified, Category category, String mime) {
            return new ReviewItem(null, uri, name, id, size, modified, category, mime);
        }
    }

    private static class ReviewStats {
        final AtomicInteger filesScanned = new AtomicInteger();
        int large;
        int oldDownloads;
        int screenshots;
        int apk;
        int temp;
        int highRisk;
        long reviewBytes;

        synchronized void add(ReviewItem item) {
            reviewBytes += Math.max(0, item.size);
            if (item.risk == Risk.HIGH) highRisk++;
            switch (item.category) {
                case LARGE -> large++;
                case OLD_DOWNLOAD -> oldDownloads++;
                case SCREENSHOT -> screenshots++;
                case APK -> apk++;
                case TEMP -> temp++;
            }
        }

        String summary() {
            return "Periksa dulu " + highRisk
                    + " • File besar " + large
                    + " • Download lama " + oldDownloads
                    + " • Screenshot " + screenshots
                    + " • APK " + apk
                    + " • Temp/log " + temp;
        }
    }
}
