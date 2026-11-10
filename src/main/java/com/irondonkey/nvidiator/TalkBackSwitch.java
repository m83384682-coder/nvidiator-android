// Nvidiator Android: swapping between Nvidiator and TalkBack.
// Needs the WRITE_SECURE_SETTINGS permission, which only adb can grant:
//   adb shell pm grant com.irondonkey.nvidiator android.permission.WRITE_SECURE_SETTINGS
// Without it, Nvidiator turns itself off and the user turns TalkBack on with the shortcut.
// Not affiliated with or endorsed by NV Access. Licensed under the GNU GPL version 2.
package com.irondonkey.nvidiator;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ComponentName;
import android.content.Context;
import android.provider.Settings;
import android.util.Log;
import android.view.accessibility.AccessibilityManager;

import java.util.ArrayList;
import java.util.List;

public final class TalkBackSwitch {
    private TalkBackSwitch() {}

    public static String talkBackComponent(Context c) {
        AccessibilityManager am = (AccessibilityManager) c.getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (am == null) return null;
        for (AccessibilityServiceInfo s : am.getInstalledAccessibilityServiceList()) {
            String id = s.getId();
            if (id != null && id.toLowerCase().contains("talkback")) return id;
        }
        return null;
    }

    private static List<String> split(String s) {
        List<String> out = new ArrayList<>();
        if (s == null) return out;
        for (String p : s.split(":")) if (!p.trim().isEmpty()) out.add(p.trim());
        return out;
    }

    private static boolean same(String a, String b) {
        ComponentName ca = ComponentName.unflattenFromString(a), cb = ComponentName.unflattenFromString(b);
        return ca != null && ca.equals(cb);
    }

    private static boolean swap(Context c, String remove, String add) {
        if (!NvidiatorService.hasSecureSettings(c) || add == null) return false;
        try {
            List<String> list = split(NvidiatorService.enabledServices(c));
            List<String> out = new ArrayList<>();
            for (String s : list) if (remove == null || !same(s, remove)) out.add(s);
            boolean has = false;
            for (String s : out) if (same(s, add)) has = true;
            if (!has) out.add(add);
            Settings.Secure.putString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, String.join(":", out));
            Settings.Secure.putInt(c.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, 1);
            return true;
        } catch (Exception e) {
            Log.e("Nvidiator", "swap", e);
            return false;
        }
    }

    /** Turns Nvidiator off and TalkBack on. */
    public static boolean toTalkBack(Context c) {
        return swap(c, NvidiatorService.self(c).flattenToString(), talkBackComponent(c));
    }

    /** Turns TalkBack off and Nvidiator on. */
    public static boolean toNvidiator(Context c) {
        return swap(c, talkBackComponent(c), NvidiatorService.self(c).flattenToString());
    }
}
