package com.thkim.photo;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(SaveFilePlugin.class);
        super.onCreate(savedInstanceState);
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
