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
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.InputStream;
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

public class ReviewMainActivity extends Activity {

    private static final int REQ_STORAGE = 6001;
    private static final int REQ_TREE = 6002;
    private static final long LARGE_BYTES = 100L * 1024L * 1024L;
    private static final long OLD_DOWNLOAD_MS = 30L * 24L * 60L * 60L * 1000L;
    private static final long TEMP_CAUTION_MS = 7L * 24L * 60L * 60L * 1000L;
    private static final int DISPLAY_LIMIT = 350;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean scanning = new AtomicBoolean(false);
    private final List<ReviewItem> results = new ArrayList<>();

    private LinearLayout root;
    private LinearLayout resultsBox;
    private TextView storageText;
    private TextView accessText;
    private TextView statusText;
    private TextView summaryText;
    private ProgressBar scanProgress;
    private Button scanButton;
    private Button folderButton;
    private Button deleteButton;
    private Uri lastTreeUri;

    private int bg;
    private int surface;
    private int surface2;
    private int primary;
    private int text;
    private int muted;
    private int success;
    private int warning;
    private int danger;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        bindColors();
        buildUi();
        refreshStorage();
        refreshAccess();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStorage();
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
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(bg);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(18), dp(16), dp(30));
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);

        TextView brand = label("ACC CLEANER · v1.0.3", 12, primary, Typeface.BOLD);
        brand.setLetterSpacing(0.14f);
        root.addView(brand);

        TextView title = label("Lihat file dulu. Baru putuskan.", 25, text, Typeface.BOLD);
        LinearLayout.LayoutParams titleLp = matchWrap();
        titleLp.topMargin = dp(5);
        root.addView(title, titleLp);

        TextView sub = label("Tidak ada auto-select dan tidak ada auto-delete.", 13, muted, Typeface.NORMAL);
        LinearLayout.LayoutParams subLp = matchWrap();
        subLp.topMargin = dp(5);
        root.addView(sub, subLp);

        LinearLayout storageCard = card();
        LinearLayout.LayoutParams storageLp = matchWrap();
        storageLp.topMargin = dp(14);
        root.addView(storageCard, storageLp);

        TextView storageLabel = label("PENYIMPANAN", 11, primary, Typeface.BOLD);
        storageLabel.setLetterSpacing(0.12f);
        storageCard.addView(storageLabel);

        storageText = label("Menghitung…", 19, text, Typeface.BOLD);
        LinearLayout.LayoutParams storageTextLp = matchWrap();
        storageTextLp.topMargin = dp(7);
        storageCard.addView(storageText, storageTextLp);

        accessText = label("Memeriksa akses…", 12, muted, Typeface.BOLD);
        LinearLayout.LayoutParams accessLp = matchWrap();
        accessLp.topMargin = dp(5);
        storageCard.addView(accessText, accessLp);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams actionsLp = matchWrap();
        actionsLp.topMargin = dp(12);
        storageCard.addView(actions, actionsLp);

        scanButton = button("SCAN PERANGKAT", true);
        actions.addView(scanButton, new LinearLayout.LayoutParams(0, dp(48), 1f));
        scanButton.setOnClickListener(v -> {
            if (hasDeepAccess()) startDeepScan();
            else requestDeepAccess();
        });

        View gap = new View(this);
        actions.addView(gap, new LinearLayout.LayoutParams(dp(8), 1));

        folderButton = button("PILIH FOLDER", false);
        actions.addView(folderButton, new LinearLayout.LayoutParams(0, dp(48), 1f));
        folderButton.setOnClickListener(v -> openFolderPicker());

        scanProgress = new ProgressBar(this);
        scanProgress.setVisibility(View.GONE);
        LinearLayout.LayoutParams progressLp = new LinearLayout.LayoutParams(dp(30), dp(30));
        progressLp.gravity = Gravity.CENTER_HORIZONTAL;
        progressLp.topMargin = dp(14);
        root.addView(scanProgress, progressLp);

        statusText = label("Belum ada scan.", 12, muted, Typeface.NORMAL);
        statusText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statusLp = matchWrap();
        statusLp.topMargin = dp(7);
        root.addView(statusText, statusLp);

        LinearLayout resultCard = card();
        LinearLayout.LayoutParams resultLp = matchWrap();
        resultLp.topMargin = dp(14);
        root.addView(resultCard, resultLp);

        TextView resultLabel = label("FILE YANG PERLU DITINJAU", 11, primary, Typeface.BOLD);
        resultLabel.setLetterSpacing(0.10f);
        resultCard.addView(resultLabel);

        summaryText = label(
                "Setelah scan, nama file, jenis, ukuran, sumber, lokasi dan status risikonya tampil di sini.",
                13, muted, Typeface.NORMAL);
        LinearLayout.LayoutParams summaryLp = matchWrap();
        summaryLp.topMargin = dp(7);
        resultCard.addView(summaryText, summaryLp);

        resultsBox = new LinearLayout(this);
        resultsBox.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams boxLp = matchWrap();
        boxLp.topMargin = dp(8);
        resultCard.addView(resultsBox, boxLp);

        deleteButton = button("HAPUS FILE TERPILIH", true);
        deleteButton.setEnabled(false);
        deleteButton.setAlpha(0.4f);
        LinearLayout.LayoutParams deleteLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        deleteLp.topMargin = dp(12);
        resultCard.addView(deleteButton, deleteLp);
        deleteButton.setOnClickListener(v -> confirmDelete());

        TextView safety = label(
                "Checklist selalu kosong setelah scan. Tap file untuk detail. File penting memakai dua konfirmasi sebelum dihapus permanen.",
                11, warning, Typeface.BOLD);
        safety.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams safetyLp = matchWrap();
        safetyLp.topMargin = dp(14);
        root.addView(safety, safetyLp);
    }

    private void refreshStorage() {
        try {
            File path = Environment.getExternalStorageDirectory();
            StatFs stat = new StatFs(path.getAbsolutePath());
            long total = stat.getTotalBytes();
            long free = stat.getAvailableBytes();
            long used = Math.max(0, total - free);
            storageText.setText(formatBytes(used) + " / " + formatBytes(total) + " terpakai");
        } catch (Throwable t) {
            storageText.setText("Penyimpanan tidak dapat dibaca");
        }
    }

    private void refreshAccess() {
        if (hasDeepAccess()) {
            accessText.setText("Deep Scan aktif · seluruh penyimpanan bersama dapat ditinjau");
            accessText.setTextColor(success);
            scanButton.setText("SCAN PERANGKAT");
        } else {
            accessText.setText("Deep Scan belum aktif · Folder Scan tetap dapat dipakai");
            accessText.setTextColor(warning);
            scanButton.setText("AKTIFKAN & SCAN");
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
            Toast.makeText(this, "Aktifkan akses file untuk ACC Cleaner, lalu kembali dan tekan Scan Perangkat.", Toast.LENGTH_LONG).show();
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
            lastTreeUri = tree;
            startTreeScan(tree);
        }
    }

    private void startDeepScan() {
        if (!hasDeepAccess()) {
            requestDeepAccess();
            return;
        }
        if (scanning.getAndSet(true)) return;
        prepareScan("Deep Scan berjalan…");
        executor.execute(() -> {
            AtomicInteger scanned = new AtomicInteger();
            List<ReviewItem> found = new ArrayList<>();
            try {
                scanFileTree(Environment.getExternalStorageDirectory(), found, scanned);
                sortResults(found);
                finishScan(found, scanned.get(), "Deep Scan");
            } catch (Throwable t) {
                failScan("Deep Scan gagal: " + safeMessage(t));
            }
        });
    }

    private void startTreeScan(Uri treeUri) {
        if (scanning.getAndSet(true)) return;
        prepareScan("Folder Scan berjalan…");
        executor.execute(() -> {
            AtomicInteger scanned = new AtomicInteger();
            List<ReviewItem> found = new ArrayList<>();
            try {
                String rootId = DocumentsContract.getTreeDocumentId(treeUri);
                Uri rootDoc = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootId);
                scanDocumentTree(rootDoc, treeUri, found, scanned, 0);
                sortResults(found);
                finishScan(found, scanned.get(), "Folder Scan");
            } catch (Throwable t) {
                failScan("Folder Scan gagal: " + safeMessage(t));
            }
        });
    }

    private void scanFileTree(File node, List<ReviewItem> found, AtomicInteger scanned) {
        if (Thread.currentThread().isInterrupted() || node == null || !node.exists()) return;
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
            for (File child : children) scanFileTree(child, found, scanned);
            return;
        }

        int count = scanned.incrementAndGet();
        Category category = classify(node.getName(), node.getAbsolutePath(), node.length(), node.lastModified());
        if (category != null) found.add(ReviewItem.fromFile(node, category));
        publishProgress(count, found.size());
    }

    private void scanDocumentTree(Uri documentUri, Uri treeUri, List<ReviewItem> found,
                                  AtomicInteger scanned, int depth) {
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
                    scanDocumentTree(child, treeUri, found, scanned, depth + 1);
                } else {
                    int count = scanned.incrementAndGet();
                    Category category = classify(name, id, size, modified);
                    if (category != null) found.add(ReviewItem.fromUri(child, name, id, size, modified, category, mime));
                    publishProgress(count, found.size());
                }
            }
        } catch (SecurityException ignored) {
        }
    }

    private Category classify(String name, String pathHint, long size, long modified) {
        String n = name == null ? "" : name.toLowerCase(Locale.ROOT);
        String p = pathHint == null ? "" : pathHint.replace('\\', '/').toLowerCase(Locale.ROOT);
        long age = modified > 0 ? System.currentTimeMillis() - modified : 0L;

        if (n.endsWith(".apk")) return Category.APK;
        if (n.contains("screenshot") || p.contains("/screenshots/") || p.contains(":pictures/screenshots/")) return Category.SCREENSHOT;
        if ((p.contains("/download/") || p.contains(":download/")) && modified > 0 && age >= OLD_DOWNLOAD_MS) return Category.OLD_DOWNLOAD;
        if (isTemp(n)) return Category.TEMP;
        if (size >= LARGE_BYTES) return Category.LARGE;
        return null;
    }

    private void sortResults(List<ReviewItem> found) {
        Collections.sort(found, Comparator.comparingLong((ReviewItem i) -> i.size).reversed());
    }

    private void publishProgress(int scanned, int found) {
        if (scanned % 250 != 0) return;
        runOnUiThread(() -> statusText.setText(
                formatInt(scanned) + " file diperiksa · " + formatInt(found) + " masuk review"));
    }

    private void prepareScan(String message) {
        results.clear();
        resultsBox.removeAllViews();
        summaryText.setText("Sedang membaca file. Belum ada file yang dipilih.");
        statusText.setText(message);
        scanProgress.setVisibility(View.VISIBLE);
        scanButton.setEnabled(false);
        folderButton.setEnabled(false);
        deleteButton.setEnabled(false);
        deleteButton.setAlpha(0.4f);
    }

    private void finishScan(List<ReviewItem> found, int scanned, String mode) {
        runOnUiThread(() -> {
            scanning.set(false);
            scanProgress.setVisibility(View.GONE);
            scanButton.setEnabled(true);
            folderButton.setEnabled(true);
            results.clear();
            results.addAll(found);
            statusText.setText(mode + " selesai · " + formatInt(scanned) + " file diperiksa");
            renderResults();
        });
    }

    private void failScan(String message) {
        runOnUiThread(() -> {
            scanning.set(false);
            scanProgress.setVisibility(View.GONE);
            scanButton.setEnabled(true);
            folderButton.setEnabled(true);
            statusText.setText(message);
            summaryText.setText("Tidak ada hasil karena scan gagal.");
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        });
    }

    private void renderResults() {
        resultsBox.removeAllViews();
        if (results.isEmpty()) {
            summaryText.setText("Tidak ada file yang cocok dengan aturan review ACC Cleaner.");
            updateDeleteState();
            return;
        }

        long total = 0L;
        for (ReviewItem item : results) total += item.size;
        summaryText.setText(formatInt(results.size()) + " file · " + formatBytes(total)
                + "\nChecklist masih kosong. Tap satu file untuk lihat detail atau membukanya.");

        int shown = Math.min(DISPLAY_LIMIT, results.size());
        for (int i = 0; i < shown; i++) resultsBox.addView(resultRow(results.get(i)));

        if (results.size() > DISPLAY_LIMIT) {
            TextView limit = label("Menampilkan " + DISPLAY_LIMIT + " file terbesar dari hasil scan. Gunakan Pilih Folder untuk review yang lebih spesifik.",
                    12, warning, Typeface.BOLD);
            LinearLayout.LayoutParams lp = matchWrap();
            lp.topMargin = dp(10);
            resultsBox.addView(limit, lp);
        }
        updateDeleteState();
    }

    private View resultRow(ReviewItem item) {
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.TOP);
        row.setPadding(0, dp(10), 0, dp(10));

        CheckBox box = new CheckBox(this);
        item.checkbox = box;
        box.setChecked(false);
        box.setOnCheckedChangeListener((buttonView, checked) -> updateDeleteState());
        row.addView(box, new LinearLayout.LayoutParams(dp(42), dp(42)));

        View preview = previewView(item);
        LinearLayout.LayoutParams previewLp = new LinearLayout.LayoutParams(dp(56), dp(56));
        previewLp.setMargins(0, 0, dp(10), 0);
        row.addView(preview, previewLp);

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setClickable(true);
        copy.setFocusable(true);
        copy.setOnClickListener(v -> showDetails(item));
        row.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView name = label(item.name, 14, text, Typeface.BOLD);
        name.setMaxLines(2);
        copy.addView(name);

        TextView meta = label(item.kind() + " · " + formatBytes(item.size) + " · " + item.category.label,
                11, primary, Typeface.BOLD);
        LinearLayout.LayoutParams metaLp = matchWrap();
        metaLp.topMargin = dp(2);
        copy.addView(meta, metaLp);

        TextView source = label(item.source(), 11, muted, Typeface.NORMAL);
        LinearLayout.LayoutParams sourceLp = matchWrap();
        sourceLp.topMargin = dp(2);
        copy.addView(source, sourceLp);

        TextView path = label(item.pathHint, 10, muted, Typeface.NORMAL);
        path.setMaxLines(2);
        LinearLayout.LayoutParams pathLp = matchWrap();
        pathLp.topMargin = dp(2);
        copy.addView(path, pathLp);

        TextView risk = label(item.riskLabel(), 10, item.highRisk() ? danger : warning, Typeface.BOLD);
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

    private View previewView(ReviewItem item) {
        if (item.kind().equals("Gambar")) {
            Bitmap bitmap = loadSmallBitmap(item);
            if (bitmap != null) {
                ImageView image = new ImageView(this);
                image.setScaleType(ImageView.ScaleType.CENTER_CROP);
                image.setImageBitmap(bitmap);
                GradientDrawable bgDrawable = rounded(surface2, dp(10));
                image.setBackground(bgDrawable);
                image.setClipToOutline(true);
                return image;
            }
        }

        TextView icon = label(item.shortKind(), 10, primary, Typeface.BOLD);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(rounded(surface2, dp(10)));
        return icon;
    }

    private Bitmap loadSmallBitmap(ReviewItem item) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            if (item.file != null) {
                BitmapFactory.decodeFile(item.file.getAbsolutePath(), bounds);
            } else if (item.uri != null) {
                try (InputStream input = getContentResolver().openInputStream(item.uri)) {
                    BitmapFactory.decodeStream(input, null, bounds);
                }
            }
            int sample = 1;
            while ((bounds.outWidth / sample) > 256 || (bounds.outHeight / sample) > 256) sample *= 2;
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = Math.max(1, sample);
            if (item.file != null) return BitmapFactory.decodeFile(item.file.getAbsolutePath(), opts);
            if (item.uri != null) {
                try (InputStream input = getContentResolver().openInputStream(item.uri)) {
                    return BitmapFactory.decodeStream(input, null, opts);
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private void showDetails(ReviewItem item) {
        String modified = item.modified > 0
                ? new SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault()).format(new Date(item.modified))
                : "Tidak diketahui";
        String message = "Jenis: " + item.kind()
                + "\nKategori scan: " + item.category.label
                + "\nUkuran: " + formatBytes(item.size)
                + "\nSumber: " + item.source()
                + "\nTerakhir diubah: " + modified
                + "\n\nSTATUS: " + item.riskLabel()
                + "\n" + item.riskReason()
                + "\n\nLokasi:\n" + item.pathHint;

        new AlertDialog.Builder(this)
                .setTitle(item.name)
                .setMessage(message)
                .setNegativeButton("Tutup", null)
                .setNeutralButton("Salin lokasi", (dialog, which) -> copyLocation(item.pathHint))
                .setPositiveButton("Buka file", (dialog, which) -> openItem(item))
                .show();
    }

    private void openItem(ReviewItem item) {
        Uri uri = item.uri;
        if (uri == null && item.file != null) uri = findMediaUri(item.file);
        if (uri == null) {
            copyLocation(item.pathHint);
            Toast.makeText(this, "Android tidak memberi URI untuk membuka file ini. Lokasinya sudah disalin.", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, item.mime());
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "Buka file dengan"));
        } catch (Throwable t) {
            copyLocation(item.pathHint);
            Toast.makeText(this, "File tidak dapat dibuka dari izin saat ini. Lokasinya sudah disalin.", Toast.LENGTH_LONG).show();
        }
    }

    private Uri findMediaUri(File file) {
        Uri filesUri = MediaStore.Files.getContentUri("external");
        String[] projection = new String[]{MediaStore.MediaColumns._ID};
        String selection = MediaStore.MediaColumns.DATA + "=?";
        String[] args = new String[]{file.getAbsolutePath()};
        try (Cursor cursor = getContentResolver().query(filesUri, projection, selection, args, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                long id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID));
                return Uri.withAppendedPath(filesUri, Long.toString(id));
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private void copyLocation(String path) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("ACC Cleaner file location", path));
        Toast.makeText(this, "Lokasi file disalin.", Toast.LENGTH_SHORT).show();
    }

    private void updateDeleteState() {
        boolean any = false;
        for (ReviewItem item : results) {
            if (item.checkbox != null && item.checkbox.isChecked()) {
                any = true;
                break;
            }
        }
        deleteButton.setEnabled(any);
        deleteButton.setAlpha(any ? 1f : 0.4f);
    }

    private void confirmDelete() {
        List<ReviewItem> selected = new ArrayList<>();
        long total = 0L;
        int high = 0;
        for (ReviewItem item : results) {
            if (item.checkbox != null && item.checkbox.isChecked()) {
                selected.add(item);
                total += item.size;
                if (item.highRisk()) high++;
            }
        }
        if (selected.isEmpty()) return;

        String message = "Terpilih " + selected.size() + " file · " + formatBytes(total)
                + "\n\n" + high + " file berstatus PERIKSA DULU."
                + "\n\nTidak ada file yang dipilih otomatis oleh ACC Cleaner.";
        long finalTotal = total;
        new AlertDialog.Builder(this)
                .setTitle("Periksa pilihan sebelum hapus")
                .setMessage(message)
                .setNegativeButton("Batal", null)
                .setPositiveButton("Saya sudah periksa", (dialog, which) -> finalDeleteConfirmation(selected, finalTotal))
                .show();
    }

    private void finalDeleteConfirmation(List<ReviewItem> selected, long total) {
        StringBuilder names = new StringBuilder();
        int preview = Math.min(5, selected.size());
        for (int i = 0; i < preview; i++) names.append("• ").append(selected.get(i).name).append("\n");
        if (selected.size() > preview) names.append("• +").append(selected.size() - preview).append(" file lain\n");

        new AlertDialog.Builder(this)
                .setTitle("Hapus permanen?")
                .setMessage(names + "\nTotal: " + formatBytes(total)
                        + "\n\nVersi ini belum memiliki recycle bin.")
                .setNegativeButton("Jangan hapus", null)
                .setPositiveButton("Hapus permanen", (dialog, which) -> deleteSelected(selected))
                .show();
    }

    private void deleteSelected(List<ReviewItem> selected) {
        deleteButton.setEnabled(false);
        statusText.setText("Menghapus file yang kamu pilih…");
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
                } else failed++;
            }
            int okFinal = ok;
            int failedFinal = failed;
            long freedFinal = freed;
            runOnUiThread(() -> {
                refreshStorage();
                Toast.makeText(this,
                        okFinal + " file terhapus · " + formatBytes(freedFinal) + " dibebaskan"
                                + (failedFinal > 0 ? " · " + failedFinal + " gagal" : ""),
                        Toast.LENGTH_LONG).show();
                if (hasDeepAccess()) startDeepScan();
                else if (lastTreeUri != null) startTreeScan(lastTreeUri);
                else renderResults();
            });
        });
    }

    private boolean isTemp(String n) {
        return n.endsWith(".tmp") || n.endsWith(".temp") || n.endsWith(".log")
                || n.endsWith(".bak") || n.endsWith(".old");
    }

    private String kindFrom(String name, String mime) {
        String n = name == null ? "" : name.toLowerCase(Locale.ROOT);
        String m = mime == null ? "" : mime.toLowerCase(Locale.ROOT);
        if (m.startsWith("video/") || endsWithAny(n, ".mp4", ".mkv", ".mov", ".avi", ".webm", ".3gp")) return "Video";
        if (m.startsWith("image/") || endsWithAny(n, ".jpg", ".jpeg", ".png", ".webp", ".heic", ".gif")) return "Gambar";
        if (m.startsWith("audio/") || endsWithAny(n, ".mp3", ".m4a", ".wav", ".aac", ".flac", ".ogg")) return "Audio";
        if (n.endsWith(".apk") || m.equals("application/vnd.android.package-archive")) return "Installer APK";
        if (m.startsWith("text/") || endsWithAny(n, ".pdf", ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx", ".txt", ".csv")) return "Dokumen";
        if (endsWithAny(n, ".zip", ".rar", ".7z", ".tar", ".gz")) return "Arsip";
        if (isTemp(n)) return "File sementara / log";
        return "File lain";
    }

    private String sourceFrom(String path) {
        String p = path == null ? "" : path.replace('\\', '/').toLowerCase(Locale.ROOT);
        if (p.contains("com.whatsapp.w4b") || p.contains("whatsapp business")) return "WhatsApp Business";
        if (p.contains("com.whatsapp") || p.contains("/whatsapp/")) return "WhatsApp";
        if (p.contains("telegram")) return "Telegram";
        if (p.contains("/dcim/camera") || p.contains(":dcim/camera")) return "Kamera HP";
        if (p.contains("/screenshots/") || p.contains(":pictures/screenshots")) return "Screenshot HP";
        if (p.contains("/download/") || p.contains(":download/")) return "Folder Download";
        if (p.contains("/movies/")) return "Folder Movies";
        if (p.contains("/pictures/")) return "Folder Pictures";
        if (p.contains("/music/")) return "Folder Music";
        if (p.contains("/documents/")) return "Folder Documents";
        if (p.contains("/android/media/")) return "Media aplikasi Android";
        return "Penyimpanan perangkat";
    }

    private String mimeFrom(String name) {
        if (name == null) return "*/*";
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) return "*/*";
        String ext = name.substring(dot + 1).toLowerCase(Locale.ROOT);
        String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
        return mime == null ? "*/*" : mime;
    }

    private boolean endsWithAny(String value, String... endings) {
        for (String ending : endings) if (value.endsWith(ending)) return true;
        return false;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        card.setBackground(rounded(surface, dp(18)));
        return card;
    }

    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        drawable.setStroke(dp(1), Color.argb(28, 255, 255, 255));
        return drawable;
    }

    private Button button(String value, boolean filled) {
        Button b = new Button(this);
        b.setText(value);
        b.setTextSize(11);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setAllCaps(false);
        b.setTextColor(filled ? bg : primary);
        GradientDrawable drawable = rounded(filled ? primary : surface2, dp(13));
        b.setBackground(drawable);
        return b;
    }

    private TextView label(String value, int sp, int color, int style) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(Typeface.DEFAULT, style);
        t.setLineSpacing(0f, 1.08f);
        return t;
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
        DecimalFormat df = value >= 100 ? new DecimalFormat("0")
                : value >= 10 ? new DecimalFormat("0.0") : new DecimalFormat("0.00");
        return df.format(value) + " " + units[unit];
    }

    private String formatInt(int value) {
        return String.format(Locale.getDefault(), "%,d", value);
    }

    private String safeMessage(Throwable t) {
        return t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
    }

    private enum Category {
        LARGE("File besar"),
        OLD_DOWNLOAD("Download lama"),
        SCREENSHOT("Screenshot"),
        APK("Installer APK"),
        TEMP("Temporary / log");

        final String label;
        Category(String label) { this.label = label; }
    }

    private final class ReviewItem {
        File file;
        Uri uri;
        String name;
        String pathHint;
        String storedMime;
        long size;
        long modified;
        Category category;
        CheckBox checkbox;

        static ReviewItem fromFile(File file, Category category) {
            ReviewItem item = new ReviewMainActivity().new ReviewItem();
            return item;
        }

        static ReviewItem fromUri(Uri uri, String name, String pathHint, long size, long modified,
                                  Category category, String mime) {
            ReviewItem item = new ReviewMainActivity().new ReviewItem();
            return item;
        }

        String mime() {
            return storedMime == null || storedMime.isBlank() ? mimeFrom(name) : storedMime;
        }

        String kind() {
            return kindFrom(name, mime());
        }

        String source() {
            return sourceFrom(pathHint);
        }

        String shortKind() {
            String k = kind();
            if (k.equals("Video")) return "VID";
            if (k.equals("Gambar")) return "IMG";
            if (k.equals("Audio")) return "AUD";
            if (k.contains("APK")) return "APK";
            if (k.equals("Dokumen")) return "DOC";
            if (k.equals("Arsip")) return "ZIP";
            if (category == Category.TEMP) return "TMP";
            return "FILE";
        }

        boolean highRisk() {
            String k = kind();
            if (k.equals("Video") || k.equals("Gambar") || k.equals("Audio") || k.equals("Dokumen")) return true;
            return category == Category.SCREENSHOT || category == Category.OLD_DOWNLOAD || category == Category.APK;
        }

        String riskLabel() {
            return highRisk() ? "PERIKSA DULU" : "TINJAU SEBELUM HAPUS";
        }

        String riskReason() {
            String k = kind();
            if (k.equals("Video") || k.equals("Gambar") || k.equals("Audio") || k.equals("Dokumen"))
                return "Bisa merupakan file pribadi atau penting. Ukuran besar tidak berarti sampah.";
            if (category == Category.SCREENSHOT)
                return "Screenshot dapat berisi bukti, tiket, kode, percakapan atau informasi penting.";
            if (category == Category.OLD_DOWNLOAD)
                return "File lama di Download tidak otomatis tidak berguna.";
            if (category == Category.APK)
                return "Ini installer Android. Hapus hanya jika installer memang tidak diperlukan.";
            if (category == Category.TEMP) {
                long age = modified > 0 ? System.currentTimeMillis() - modified : 0L;
                if (age < TEMP_CAUTION_MS) return "File sementara masih baru dan mungkin sedang dipakai aplikasi.";
                return "Kemungkinan file sementara lama, tetapi sumbernya tetap perlu diperiksa.";
            }
            return "Nama dan ukuran file saja tidak cukup untuk menentukan aman dihapus.";
        }
    }
}
