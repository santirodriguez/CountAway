package com.santiagorodriguez.countaway.backup;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.provider.DocumentsContract;
import java.io.File;
import java.util.Arrays;
import java.util.List;

/** Instrumentation-only fault-mode controller; never included in the production app. */
public final class BackupFaultControlProvider extends ContentProvider {
    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        Context app = getContext();
        if (app == null) throw new IllegalStateException("Fixture context unavailable");
        String[] packages = app.getPackageManager().getPackagesForUid(Binder.getCallingUid());
        List<String> callers = packages == null ? java.util.Collections.emptyList() : Arrays.asList(packages);
        if (!callers.contains("com.santiagorodriguez.countaway") &&
            !callers.contains(app.getPackageName())) {
            throw new SecurityException("Fixture controller restricted to its test app");
        }
        SharedPreferences prefs = app.getSharedPreferences("ci_fault_saf", Context.MODE_PRIVATE);
        Bundle result = new Bundle();
        switch (method) {
            case "probe":
                result.putBoolean("ready", true);
                return result;
            case "reset":
                prefs.edit().clear().commit();
                deleteOnlyFixtureDirectory(new File(app.getFilesDir(), "ci-fault-saf"));
                app.getContentResolver().notifyChange(
                    DocumentsContract.buildRootsUri(app.getPackageName() + ".ci-fault-saf"), null);
                break;
            case "mode":
                if (arg == null || !Arrays.asList(
                    "NORMAL", "UNAVAILABLE", "NO_SPACE", "ABORT", "TRUNCATED", "SLOW").contains(arg)) {
                    throw new IllegalArgumentException("Invalid fixture mode");
                }
                prefs.edit().putString("mode", arg)
                    .putBoolean("entered_slow_write", false).commit();
                break;
            case "protect":
                if (arg == null || !arg.startsWith("f-")) {
                    throw new IllegalArgumentException("Invalid protected test document");
                }
                prefs.edit().putString("protected_document", arg).commit();
                break;
            case "status":
                result.putBoolean("entered_slow_write",
                    prefs.getBoolean("entered_slow_write", false));
                return result;
            default:
                throw new IllegalArgumentException("Unknown fixture command");
        }
        result.putBoolean("ready", true);
        return result;
    }

    private static void deleteOnlyFixtureDirectory(File file) {
        if (!file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) deleteOnlyFixtureDirectory(child);
            }
        }
        if (!file.delete()) throw new IllegalStateException("Could not clean disposable fixture");
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
        String[] selectionArgs, String sortOrder) { return null; }

    @Override
    public String getType(Uri uri) { return null; }

    @Override
    public Uri insert(Uri uri, ContentValues values) { return null; }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }

    @Override
    public int update(Uri uri, ContentValues values, String selection,
        String[] selectionArgs) { return 0; }
}
