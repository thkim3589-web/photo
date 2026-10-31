package com.thkim.photo;

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(SaveFilePlugin.class);
        super.onCreate(savedInstanceState);

        // 상태바(시간/배터리)와 하단 내비게이션 영역을 앱 화면과 겹치지 않게 하고,
        // 그 영역은 앱 헤더와 같은 남색으로 채워 흰 아이콘이 잘 보이게 한다.
        Window window = getWindow();
        final int navy = Color.parseColor("#003399");
        window.getDecorView().setBackgroundColor(navy);
        window.setStatusBarColor(navy);
        window.setNavigationBarColor(navy);
        WindowInsetsControllerCompat ctl = new WindowInsetsControllerCompat(window, window.getDecorView());
        ctl.setAppearanceLightStatusBars(false);
        ctl.setAppearanceLightNavigationBars(false);
        final View content = findViewById(android.R.id.content);
        ViewCompat.setOnApplyWindowInsetsListener(content, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, ime.bottom));
            return WindowInsetsCompat.CONSUMED;
        });
    }

    // 사진 데이터가 많을 때 앱이 백그라운드에서 종료된 후 "최근 앱" 목록에서
    // 다시 열면 상태 복원(onSaveInstanceState) 크기 제한을 넘어서
    // "앱을 열지 못했어요" 오류가 나는 것을 막기 위해 상태 저장을 생략한다.
    // (사진/입력 데이터는 WebView의 로컬 저장소에 별도 보관되므로 데이터 손실 없음)
    @Override
    public void onSaveInstanceState(Bundle outState) {
        // intentionally does not call super.onSaveInstanceState(outState)
    }
}
