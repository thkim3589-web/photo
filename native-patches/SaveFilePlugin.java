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

import java.io.OutputStream;

// 안드로이드 시스템 "다른 이름으로 저장" 창(Storage Access Framework)을 띄워
// 사용자가 저장 위치(내 파일/다운로드 등)를 직접 선택해서 파일을 저장할 수 있게 하는 플러그인.
// Capacitor의 Share(공유시트)와 달리 "내 파일" 앱이 목적지로 바로 나타난다.
@CapacitorPlugin(name = "SaveFile")
public class SaveFilePlugin extends Plugin {

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
}
