package com.thkim.photo;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.net.Uri;
import android.util.Base64;

import androidx.activity.result.ActivityResult;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

import androidx.core.content.FileProvider;

// 안드로이드 시스템 "다른 이름으로 저장" 창(Storage Access Framework)을 띄워
// 사용자가 저장 위치(내 파일/다운로드 등)를 직접 선택해서 파일을 저장할 수 있게 하는 플러그인.
// Capacitor의 Share(공유시트)와 달리 "내 파일" 앱이 목적지로 바로 나타난다.
@CapacitorPlugin(name = "SaveFile")
public class SaveFilePlugin extends Plugin {

    // 방금 저장한 파일의 앱 내부(cache) 복사본 — 📤 공유하기에서 카톡/네이버 등으로 전송할 때 사용
    private File lastSharedFile = null;
    private String lastSharedMime = "application/octet-stream";

    @PluginMethod
    public void saveFile(PluginCall call) {
        String filename = call.getString("filename", "저장파일");
        String mimeType = call.getString("mimeType", "application/octet-stream");

        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(mimeType);
        intent.putExtra(Intent.EXTRA_TITLE, filename);

        startActivityForResult(call, intent, "saveFileResult");
    }

    @ActivityCallback
    private void saveFileResult(PluginCall call, ActivityResult result) {
        if (call == null) return;

        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) {
            call.reject("사용자가 저장을 취소했습니다.");
            return;
        }

        Uri uri = result.getData().getData();
        if (uri == null) {
            call.reject("저장 위치를 가져오지 못했습니다.");
            return;
        }

        try {
            String base64Data = call.getString("data");
            byte[] bytes = Base64.decode(base64Data, Base64.DEFAULT);

            // 공유하기용 복사본 (앱 cache 폴더) — 저장 위치와 무관하게 항상 공유 가능하도록
            try {
                String name = call.getString("filename", "저장파일").replaceAll("[\\\\/:*?\"<>|]", "_");
                File dir = new File(getContext().getCacheDir(), "shared");
                if (!dir.exists()) dir.mkdirs();
                File[] old = dir.listFiles();
                if (old != null) for (File f : old) f.delete();
                File copy = new File(dir, name);
                FileOutputStream fos = new FileOutputStream(copy);
                try { fos.write(bytes); fos.flush(); } finally { fos.close(); }
                lastSharedFile = copy;
                lastSharedMime = call.getString("mimeType", "application/octet-stream");
            } catch (Exception ignore) {
                lastSharedFile = null;
            }

            ContentResolver resolver = getContext().getContentResolver();
            OutputStream out = resolver.openOutputStream(uri);
            if (out == null) {
                call.reject("출력 스트림을 열 수 없습니다.");
                return;
            }
            try {
                out.write(bytes);
                out.flush();
            } finally {
                out.close();
            }

            JSObject ret = new JSObject();
            ret.put("uri", uri.toString());
            call.resolve(ret);
        } catch (Exception e) {
            call.reject("파일 저장 실패: " + e.getMessage());
        }
    }

    // 📤 공유하기: 마지막으로 저장한 파일을 안드로이드 공유창(카톡/네이버 등)으로 전달
    @PluginMethod
    public void shareLastFile(PluginCall call) {
        try {
            if (lastSharedFile == null || !lastSharedFile.exists()) {
                call.reject("공유할 파일이 없습니다.");
                return;
            }
            Uri shareUri = FileProvider.getUriForFile(
                getContext(), getContext().getPackageName() + ".fileprovider", lastSharedFile);

            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType(lastSharedMime);
            send.putExtra(Intent.EXTRA_STREAM, shareUri);
            String title = call.getString("title");
            String text = call.getString("text");
            if (title != null) send.putExtra(Intent.EXTRA_SUBJECT, title);
            if (text != null) send.putExtra(Intent.EXTRA_TEXT, text);
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            Intent chooser = Intent.createChooser(send, call.getString("dialogTitle", "공유하기"));
            chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            getActivity().startActivity(chooser);
            call.resolve();
        } catch (Exception e) {
            call.reject("공유 실패: " + e.getMessage());
        }
    }

    // ===== 대용량 파일용: 조각(chunk) 단위 저장 =====
    // JS에서 파일 전체를 한 번에 넘기면 메모리 부족으로 앱이 종료될 수 있어,
    // beginSave(저장 위치 선택) → writeChunk(여러 번) → finishSave 순서로 나눠서 기록한다.
    private OutputStream pendingOut = null;
    private OutputStream pendingCacheOut = null;
    private File pendingCacheFile = null;
    private String pendingMime = "application/octet-stream";

    private void closePending(boolean deleteCache) {
        try { if (pendingOut != null) pendingOut.close(); } catch (Exception ignore) {}
        try { if (pendingCacheOut != null) pendingCacheOut.close(); } catch (Exception ignore) {}
        if (deleteCache && pendingCacheFile != null) { try { pendingCacheFile.delete(); } catch (Exception ignore) {} }
        pendingOut = null;
        pendingCacheOut = null;
        pendingCacheFile = null;
    }

    @PluginMethod
    public void beginSave(PluginCall call) {
        String filename = call.getString("filename", "저장파일");
        String mimeType = call.getString("mimeType", "application/octet-stream");

        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(mimeType);
        intent.putExtra(Intent.EXTRA_TITLE, filename);

        startActivityForResult(call, intent, "beginSaveResult");
    }

    @ActivityCallback
    private void beginSaveResult(PluginCall call, ActivityResult result) {
        if (call == null) return;
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) {
            call.reject("사용자가 저장을 취소했습니다.");
            return;
        }
        Uri uri = result.getData().getData();
        if (uri == null) {
            call.reject("저장 위치를 가져오지 못했습니다.");
            return;
        }
        try {
            closePending(true);
            ContentResolver resolver = getContext().getContentResolver();
            pendingOut = resolver.openOutputStream(uri);
            if (pendingOut == null) {
                call.reject("출력 스트림을 열 수 없습니다.");
                return;
            }
            pendingMime = call.getString("mimeType", "application/octet-stream");

            // 공유하기용 복사본(앱 cache) — 실패해도 저장 자체는 계속 진행
            try {
                String name = call.getString("filename", "저장파일").replaceAll("[\\\\/:*?\"<>|]", "_");
                File dir = new File(getContext().getCacheDir(), "shared");
                if (!dir.exists()) dir.mkdirs();
                File[] old = dir.listFiles();
                if (old != null) for (File f : old) f.delete();
                pendingCacheFile = new File(dir, name);
                pendingCacheOut = new FileOutputStream(pendingCacheFile);
            } catch (Exception e) {
                pendingCacheOut = null;
                pendingCacheFile = null;
            }

            JSObject ret = new JSObject();
            ret.put("uri", uri.toString());
            call.resolve(ret);
        } catch (Exception e) {
            closePending(true);
            call.reject("저장 시작 실패: " + e.getMessage());
        }
    }

    @PluginMethod
    public void writeChunk(PluginCall call) {
        if (pendingOut == null) {
            call.reject("저장이 시작되지 않았습니다.");
            return;
        }
        try {
            byte[] bytes = Base64.decode(call.getString("data", ""), Base64.DEFAULT);
            pendingOut.write(bytes);
            if (pendingCacheOut != null) {
                try { pendingCacheOut.write(bytes); } catch (Exception e) { pendingCacheOut = null; }
            }
            call.resolve();
        } catch (Exception e) {
            closePending(true);
            call.reject("파일 기록 실패: " + e.getMessage());
        }
    }

    @PluginMethod
    public void finishSave(PluginCall call) {
        try {
            if (pendingOut != null) { pendingOut.flush(); }
            File done = pendingCacheFile;
            boolean cacheOk = pendingCacheOut != null;
            closePending(false);
            if (cacheOk && done != null && done.exists()) {
                lastSharedFile = done;
                lastSharedMime = pendingMime;
            } else {
                lastSharedFile = null;
            }
            call.resolve();
        } catch (Exception e) {
            closePending(true);
            call.reject("저장 마무리 실패: " + e.getMessage());
        }
    }

    @PluginMethod
    public void abortSave(PluginCall call) {
        closePending(true);
        call.resolve();
    }
}
