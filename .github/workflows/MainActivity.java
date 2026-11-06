package com.guardianpet;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    Button btn(String s, View.OnClickListener c) {
        Button b = new Button(this);
        b.setText(s);
        b.setOnClickListener(c);
        return b;
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
        }
        int p = (int) (20 * getResources().getDisplayMetrics().density);
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(p, p * 2, p, p);
        TextView tv = new TextView(this);
        tv.setTextSize(16);
        tv.setText("Guardian Pet\n\nTap Start to let your pet roam over other apps. The first time, allow Display over other apps. Tap the pet to make it roar, or drag and throw it. Stop it here or from its notification.\n");
        l.addView(tv);
        l.addView(btn("Start pet", v -> start()));
        l.addView(btn("Stop pet", v -> stopService(new Intent(this, PetService.class))));
        TextView lk = new TextView(this);
        lk.setText("\nLooks");
        lk.setTextSize(16);
        l.addView(lk);
        String[] names = {"Guardian", "Ember", "Frost", "Void", "Blossom", "Stone"};
        for (String n : names) {
            final String id = n.toLowerCase();
            l.addView(btn(n, v -> startService(new Intent(this, PetService.class).putExtra("look", id))));
        }
        ScrollView sv = new ScrollView(this);
        sv.addView(l);
        setContentView(sv);
    }

    void start() {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
            Toast.makeText(this, "Allow Display over other apps, then come back and tap Start", Toast.LENGTH_LONG).show();
            return;
        }
        startForegroundService(new Intent(this, PetService.class));
    }
}
