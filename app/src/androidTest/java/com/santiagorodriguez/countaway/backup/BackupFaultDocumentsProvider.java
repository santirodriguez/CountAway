package com.santiagorodriguez.countaway.backup;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.DocumentsProvider;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Instrumentation-only DocumentsProvider. Java is intentional: the AndroidTest
 * APK hosts this provider in a separate process and does not bundle Kotlin stdlib.
 * This fixture does not exist in debug or release production app APKs.
 */
public final class BackupFaultDocumentsProvider extends DocumentsProvider {
    private static final String[] ROOT_COLUMNS = {
        DocumentsContract.Root.COLUMN_ROOT_ID,
        DocumentsContract.Root.COLUMN_DOCUMENT_ID,
        DocumentsContract.Root.COLUMN_TITLE,
        DocumentsContract.Root.COLUMN_SUMMARY,
        DocumentsContract.Root.COLUMN_ICON,
        DocumentsContract.Root.COLUMN_FLAGS,
        DocumentsContract.Root.COLUMN_MIME_TYPES,
        DocumentsContract.Root.COLUMN_AVAILABLE_BYTES
    };

    private static final String[] DOCUMENT_COLUMNS = {
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_FLAGS,
        DocumentsContract.Document.COLUMN_SIZE,
        DocumentsContract.Document.COLUMN_LAST_MODIFIED
    };

    private Context app() {
        Context result = getContext();
        if (result == null) throw new IllegalStateException("Fixture context unavailable");
        return result;
    }

    private File directory() {
        File base = new File(app().getFilesDir(), "ci-fault-saf");
        if (!base.exists() && !base.mkdirs()) {
            throw new IllegalStateException("Cannot initialize fixture directory");
        }
        return base;
    }

    private SharedPreferences settings() {
        return app().getSharedPreferences("ci_fault_saf", Context.MODE_PRIVATE);
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor queryRoots(String[] projection) {
        MatrixCursor cursor = new MatrixCursor(projection == null ? ROOT_COLUMNS : projection);
        Map<String, Object> values = new HashMap<>();
        values.put(DocumentsContract.Root.COLUMN_ROOT_ID, "root");
        values.put(DocumentsContract.Root.COLUMN_DOCUMENT_ID, "root");
        values.put(DocumentsContract.Root.COLUMN_TITLE, "CountAway Test Storage");
        values.put(DocumentsContract.Root.COLUMN_SUMMARY, "Disposable backup failure fixtures");
        values.put(DocumentsContract.Root.COLUMN_ICON, android.R.drawable.ic_menu_save);
        values.put(DocumentsContract.Root.COLUMN_FLAGS,
            DocumentsContract.Root.FLAG_LOCAL_ONLY | DocumentsContract.Root.FLAG_SUPPORTS_CREATE);
        values.put(DocumentsContract.Root.COLUMN_MIME_TYPES, "*/*");
        values.put(DocumentsContract.Root.COLUMN_AVAILABLE_BYTES, directory().getUsableSpace());
        addProjectedRow(cursor, values);
        return cursor;
    }

    @Override
    public Cursor queryDocument(String documentId, String[] projection) throws FileNotFoundException {
        MatrixCursor cursor = new MatrixCursor(projection == null ? DOCUMENT_COLUMNS : projection);
        addDocument(cursor, documentId);
        return cursor;
    }

    @Override
    public Cursor queryChildDocuments(
            String parentDocumentId, String[] projection, String sortOrder)
            throws FileNotFoundException {
        MatrixCursor cursor = new MatrixCursor(projection == null ? DOCUMENT_COLUMNS : projection);
        if ("root".equals(parentDocumentId)) {
            addDocument(cursor, "folder");
        } else if ("folder".equals(parentDocumentId)) {
            File[] entries = directory().listFiles();
            if (entries != null) {
                for (File item : entries) {
                    if (item.isFile() && item.getName().startsWith("f-")) {
                        addDocument(cursor, item.getName());
                    }
                }
            }
        } else {
            throw new FileNotFoundException("Unknown parent document: " + parentDocumentId);
        }
        return cursor;
    }

    @Override
    public boolean isChildDocument(String parentDocumentId, String documentId) {
        return ("root".equals(parentDocumentId) && "folder".equals(documentId)) ||
            ("folder".equals(parentDocumentId) && isFileId(documentId));
    }

    @Override
    public String createDocument(String parentDocumentId, String mimeType, String displayName)
            throws FileNotFoundException {
        if (!"folder".equals(parentDocumentId) || !"application/json".equals(mimeType)) {
            throw new FileNotFoundException("Fixture only writes JSON in Backup Scenarios");
        }
        if ("UNAVAILABLE".equals(settings().getString("mode", "NORMAL"))) {
            throw new FileNotFoundException("Fixture provider unavailable");
        }
        String safeName = displayName.substring(0, Math.min(90, displayName.length()))
            .replaceAll("[^A-Za-z0-9._-]", "_");
        String id = "f-" + UUID.randomUUID() + "__" + safeName;
        if (!backing(id).createNewFile()) {
            throw new FileNotFoundException("Cannot create fixture document");
        }
        return id;
    }

    @Override
    public ParcelFileDescriptor openDocument(
            String documentId, String mode, CancellationSignal signal)
            throws FileNotFoundException {
        File file = backing(documentId);
        if (!file.isFile()) throw new FileNotFoundException(documentId);
        String fault = settings().getString("mode", "NORMAL");
        if (mode.contains("w")) {
            if ("NO_SPACE".equals(fault)) {
                throw new FileNotFoundException("ENOSPC: fixture provider quota exhausted");
            }
            if ("ABORT".equals(fault)) {
                writeFixture(file, "{incomplete");
                throw new FileNotFoundException("Fixture aborted after partial output");
            }
            if ("SLOW".equals(fault)) {
                settings().edit().putBoolean("entered_slow_write", true).commit();
                try {
                    Thread.sleep(1800L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    FileNotFoundException failure = new FileNotFoundException("Slow write interrupted");
                    failure.initCause(e);
                    throw failure;
                }
            }
        } else if ("TRUNCATED".equals(fault) &&
                !documentId.equals(settings().getString("protected_document", null))) {
            File truncated = new File(app().getCacheDir(), "ci-saf-truncated-read");
            writeFixture(truncated, "{partial");
            return ParcelFileDescriptor.open(truncated, ParcelFileDescriptor.MODE_READ_ONLY);
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.parseMode(mode));
    }

    @Override
    public void deleteDocument(String documentId) throws FileNotFoundException {
        if (!backing(documentId).delete()) {
            throw new FileNotFoundException("Cannot delete fixture: " + documentId);
        }
    }

    private boolean isFileId(String id) {
        return id != null && id.matches("f-[a-f0-9-]{36}__[A-Za-z0-9._-]{1,90}");
    }

    private File backing(String id) throws FileNotFoundException {
        if (!isFileId(id)) throw new FileNotFoundException("Unsafe fixture document ID");
        return new File(directory(), id);
    }

    private void addDocument(MatrixCursor cursor, String id) throws FileNotFoundException {
        boolean folder = "root".equals(id) || "folder".equals(id);
        File file = null;
        if (!folder) {
            file = backing(id);
            if (!file.isFile()) throw new FileNotFoundException(id);
        }
        Map<String, Object> values = new HashMap<>();
        values.put(DocumentsContract.Document.COLUMN_DOCUMENT_ID, id);
        values.put(DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            "root".equals(id) ? "CountAway Test Storage" :
            "folder".equals(id) ? "Backup Scenarios" : id.substring(id.indexOf("__") + 2));
        values.put(DocumentsContract.Document.COLUMN_MIME_TYPE,
            folder ? DocumentsContract.Document.MIME_TYPE_DIR : "application/json");
        values.put(DocumentsContract.Document.COLUMN_FLAGS,
            folder ? DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE :
                (DocumentsContract.Document.FLAG_SUPPORTS_WRITE |
                 DocumentsContract.Document.FLAG_SUPPORTS_DELETE));
        values.put(DocumentsContract.Document.COLUMN_SIZE, file == null ? 0 : file.length());
        values.put(DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            file == null ? System.currentTimeMillis() : file.lastModified());
        addProjectedRow(cursor, values);
    }

    private static void addProjectedRow(MatrixCursor cursor, Map<String, Object> values) {
        String[] columns = cursor.getColumnNames();
        Object[] row = new Object[columns.length];
        for (int i = 0; i < columns.length; i++) row[i] = values.get(columns[i]);
        cursor.addRow(row);
    }

    private static void writeFixture(File file, String text) throws FileNotFoundException {
        try (FileOutputStream out = new FileOutputStream(file, false)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            FileNotFoundException failure = new FileNotFoundException("Fixture write failed");
            failure.initCause(e);
            throw failure;
        }
    }
}
