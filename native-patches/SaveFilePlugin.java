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
}
