// Nvidiator Android: starts Python (Chaquopy) and NVDA's modules, and wraps the bridge calls.
// Not affiliated with or endorsed by NV Access. Licensed under the GNU GPL version 2.
package com.irondonkey.nvidiator;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;

/**
 * The Python half of the screen reader: NVDA's controlTypes, characterProcessing, translations
 * and Python console, running in Chaquopy. If Python cannot start, {@link #ready()} stays false
 * and the service falls back to plain Java speech so the phone stays usable.
 */
public final class NvdaPy {
    private static final String TAG = "Nvidiator";
    private static volatile boolean ready = false;
    private static volatile String error = null;
    private static PyObject bridge;
    private static JSONObject messages = new JSONObject();
    private static String locale = "en";

    private NvdaPy() {}

    public static boolean ready() { return ready; }
    public static String error() { return error; }
    public static String locale() { return locale; }

    /** Starts Python and NVDA's modules. Safe to call from any thread, more than once. */
    public static synchronized boolean ensure(Context ctx) {
        if (ready) return true;
        try {
            Context app = ctx.getApplicationContext();
            File appDir = extractLocaleData(app);
            if (!Python.isStarted()) Python.start(new AndroidPlatform(app));
            Python py = Python.getInstance();
            bridge = py.getModule("nvidiator.bridge");
            String lang = Prefs.s(app, Prefs.LANGUAGE, "");
            if (lang.isEmpty()) lang = Locale.getDefault().toString();
            locale = bridge.callAttr("init", appDir.getAbsolutePath(), lang).toString();
            messages = new JSONObject(bridge.callAttr("messages").toString());
            applyConfig(app);
            ready = true;
            error = null;
            Log.i(TAG, "NVDA core ready, Python " + bridge.callAttr("pythonVersion") + ", locale " + locale);
        } catch (Throwable t) {
            error = t.toString();
            Log.e(TAG, "NVDA core failed to start", t);
        }
        return ready;
    }

    /** Copies NVDA's locale folder (symbols.dic, characterDescriptions.dic, nvda.mo) to real files. */
    private static File extractLocaleData(Context app) throws Exception {
        File appDir = new File(app.getFilesDir(), "nvda");
        File marker = new File(appDir, ".version");
        String want = BuildConfig.VERSION_NAME + "/" + BuildConfig.VERSION_CODE;
        if (marker.exists()) {
            try (InputStream in = new java.io.FileInputStream(marker)) {
                byte[] b = new byte[64];
                int n = in.read(b);
                if (n > 0 && new String(b, 0, n, "UTF-8").equals(want)) return appDir;
            }
        }
        copyAssetDir(app.getAssets(), "nvda/locale", new File(appDir, "locale"));
        try (OutputStream o = new FileOutputStream(marker)) { o.write(want.getBytes("UTF-8")); }
        return appDir;
    }

    private static void copyAssetDir(AssetManager am, String path, File out) throws Exception {
        String[] list = am.list(path);
        if (list == null || list.length == 0) {
            out.getParentFile().mkdirs();
            try (InputStream in = am.open(path); OutputStream o = new FileOutputStream(out)) {
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) o.write(buf, 0, n);
            }
            return;
        }
        out.mkdirs();
        for (String name : list) copyAssetDir(am, path + "/" + name, new File(out, name));
    }

    public static synchronized void applyConfig(Context c) {
        if (bridge == null) return;
        try {
            bridge.callAttr("setConfig", "speech", "symbolLevel", Prefs.symbolLevel(c));
            bridge.callAttr("setConfig", "presentation", "reportObjectPositionInformation", Prefs.reportPosition(c));
            bridge.callAttr("setConfig", "presentation", "reportObjectDescriptions", Prefs.reportDescriptions(c));
            bridge.callAttr("setConfig", "keyboard", "speakTypedCharacters", Prefs.typedChars(c));
            bridge.callAttr("setConfig", "keyboard", "speakTypedWords", Prefs.typedWords(c));
        } catch (Throwable t) {
            Log.w(TAG, "applyConfig", t);
        }
    }

    public static synchronized void setLanguage(Context c, String lang) {
        if (!ready) return;
        try {
            if (lang == null || lang.isEmpty()) lang = Locale.getDefault().toString();
            locale = bridge.callAttr("setLanguage", lang).toString();
            messages = new JSONObject(bridge.callAttr("messages").toString());
        } catch (Throwable t) {
            Log.w(TAG, "setLanguage", t);
        }
    }

    private static String call(String fn, Object... args) {
        if (!ready) return null;
        try {
            PyObject r = bridge.callAttr(fn, args);
            return r == null ? null : r.toString();
        } catch (Throwable t) {
            Log.w(TAG, fn + " failed", t);
            return null;
        }
    }

    public static String describeFocus(String node, String entered, String exited, String reason) {
        return call("describeFocus", node, entered, exited, reason);
    }

    public static String describe(String node, String reason) { return call("describe", node, reason); }
    public static String stateSpeech(String node) { return call("stateSpeech", node); }
    public static String roleInfo(String node) { return call("roleInfo", node); }
    public static String processSymbols(String text, int level) { return call("processSymbols", text, level); }
    public static String speakCharacter(String ch) { return call("speakCharacter", ch); }
    public static String characterDescription(String ch) { return call("characterDescription", ch); }
    public static String roleName(String role) { return call("roleName", role); }
    public static String languages() { return call("listLanguages"); }

    /** One of NVDA's translated messages (see bridge.messages), or the English fallback. */
    public static String msg(String key, String fallback) {
        String v = messages.optString(key, "");
        return v.isEmpty() ? fallback : v;
    }

    public static PyObject module(String name) {
        return Python.getInstance().getModule(name);
    }
}
