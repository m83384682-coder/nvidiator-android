// Nvidiator Android: NVDA's Add-on Store (gui/addonStoreGui), for installed add-ons and
// "Install from external source". Titles, buttons, statuses and prompts use NVDA's own wording
// and translations. There is no online catalogue: add-ons are installed from .nvda-addon files.
// Not affiliated with or endorsed by NV Access. Licensed under the GNU GPL version 2.
package com.irondonkey.nvidiator;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.LinearLayout;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class AddonStoreActivity extends Activity {
    private static final int PICK = 41;
    private LinearLayout list;
    private boolean changed = false;

    private String t(String k) { return UiText.get(this, k); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout col = Ui.page(this, t("addonStoreTitle"));
        Ui.text(this, col, getString(R.string.addon_not_supported));
        Ui.button(this, col, t("installExternal"), v -> chooseFile());
        Ui.heading(this, col, t("installedAddons"), 20);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        col.addView(list);
        Ui.button(this, col, t("close"), v -> finish());
        list.post(this::fillAsync);
        if (Prefs.b(this, Prefs.ADDON_WARNING, true)) {
            new AlertDialog.Builder(this)
                    .setTitle(t("addonWarningTitle"))
                    .setMessage(t("addonWarning"))
                    .setPositiveButton(t("ok"), (d, w) -> Prefs.put(this, Prefs.ADDON_WARNING, false))
                    .setCancelable(false)
                    .show();
        }
    }

    private void fillAsync() {
        new Thread(() -> {
            NvdaPy.ensure(this);
            JSONArray a = Addons.list();
            if (a.length() == 0 && !Addons.started()) {
                try { NvdaPy.module("addonHandler").callAttr("scanOnly", Addons.dir(this).getAbsolutePath()); } catch (Throwable ignored) {}
                a = Addons.list();
            }
            JSONArray fa = a;
            runOnUiThread(() -> fill(fa));
        }).start();
    }

    private String statusText(JSONObject o) {
        String s = o.optString("status");
        if ("failed".equals(s)) return t("statusDisabled") + ". " + o.optString("failure");
        if ("disabled".equals(s)) return o.optBoolean("running") ? t("statusDisablePending") : t("statusDisabled");
        return o.optBoolean("running") || !Addons.started() ? t("statusEnabled") : t("statusEnablePending");
    }

    private void fill(JSONArray a) {
        list.removeAllViews();
        if (a.length() == 0) { Ui.text(this, list, getString(R.string.addon_none)); return; }
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o == null) continue;
            String name = o.optString("name");
            String summary = o.optString("summary", name);
            Ui.heading(this, list, summary + " " + o.optString("version"), 18);
            Ui.text(this, list, statusText(o));
            if (!o.optString("author").isEmpty()) Ui.text(this, list, o.optString("author"));
            if (!o.optString("description").isEmpty()) Ui.text(this, list, o.optString("description"));
            boolean disabled = "disabled".equals(o.optString("status")) || "failed".equals(o.optString("status"));
            Ui.button(this, list, disabled ? t("addonEnable") : t("addonDisable"), v -> {
                new Thread(() -> { Addons.setEnabled(name, disabled); changed = true; runOnUiThread(this::fillAsync); }).start();
            }).setContentDescription((disabled ? t("addonEnable") : t("addonDisable")) + ", " + summary);
            Ui.button(this, list, t("addonRemove"), v -> new AlertDialog.Builder(this)
                    .setTitle(t("removeTitle"))
                    .setMessage(t("removeQuestion").replace("{addon}", summary))
                    .setPositiveButton(t("yes"), (d, w) -> new Thread(() -> {
                        Addons.remove(name); changed = true; runOnUiThread(this::fillAsync);
                    }).start())
                    .setNegativeButton(t("no"), null)
                    .show()).setContentDescription(t("addonRemove") + ", " + summary);
        }
    }

    private void chooseFile() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_TITLE, t("chooseAddonFile"));
        try { startActivityForResult(i, PICK); }
        catch (Exception e) { Sounds.play("error"); }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != PICK || res != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        new Thread(() -> {
            File f = new File(getCacheDir(), "incoming.nvda-addon");
            String shown = uri.getLastPathSegment() == null ? uri.toString() : uri.getLastPathSegment();
            try (InputStream in = getContentResolver().openInputStream(uri); OutputStream out = new FileOutputStream(f)) {
                if (in == null) throw new java.io.IOException("no stream");
                byte[] buf = new byte[65536];
                int n;
                long total = 0;
                while ((n = in.read(buf)) > 0) {
                    total += n;
                    if (total > 200L * 1024 * 1024) throw new java.io.IOException("too big");
                    out.write(buf, 0, n);
                }
            } catch (Exception e) {
                runOnUiThread(() -> error(String.format(t("failedOpen"), shown)));
                return;
            }
            NvdaPy.ensure(this);
            JSONObject info;
            try { info = Addons.bundleInfo(f.getAbsolutePath()); }
            catch (Throwable e) { runOnUiThread(() -> error(String.format(t("failedOpen"), shown))); return; }
            JSONObject fi = info;
            runOnUiThread(() -> new AlertDialog.Builder(this)
                    .setTitle(t("installTitle"))
                    .setMessage(t("confirmInstall").replace("{summary}", fi.optString("summary")).replace("{version}", fi.optString("version")))
                    .setPositiveButton(t("yes"), (d, w) -> new Thread(() -> {
                        try {
                            Addons.install(f.getAbsolutePath());
                            changed = true;
                            runOnUiThread(() -> { fillAsync(); askRestart(); });
                        } catch (Throwable e) {
                            runOnUiThread(() -> error(String.format(t("failedInstall"), shown)));
                        } finally {
                            //noinspection ResultOfMethodCallIgnored
                            f.delete();
                        }
                    }).start())
                    .setNegativeButton(t("no"), null)
                    .show());
        }).start();
    }

    private void error(String msg) {
        Sounds.play("error");
        new AlertDialog.Builder(this).setTitle(t("error")).setMessage(msg).setPositiveButton(t("ok"), null).show();
    }

    private void askRestart() {
        new AlertDialog.Builder(this)
                .setTitle(t("restartTitle"))
                .setMessage(t("restartQuestion"))
                .setPositiveButton(t("yes"), (d, w) -> { changed = false; NvidiatorService.restartAddons(); })
                .setNegativeButton(t("no"), null)
                .show();
    }

    @Override
    public void onBackPressed() {
        if (changed && NvidiatorService.get() != null) { askRestartThenFinish(); return; }
        super.onBackPressed();
    }

    private void askRestartThenFinish() {
        new AlertDialog.Builder(this)
                .setTitle(t("restartTitle"))
                .setMessage(t("restartQuestion"))
                .setPositiveButton(t("yes"), (d, w) -> { changed = false; NvidiatorService.restartAddons(); finish(); })
                .setNegativeButton(t("no"), (d, w) -> { changed = false; finish(); })
                .show();
    }
}
