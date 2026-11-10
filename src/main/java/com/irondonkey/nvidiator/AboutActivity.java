// Nvidiator Android: About and License.
// Not affiliated with or endorsed by NV Access. Licensed under the GNU GPL version 2.
package com.irondonkey.nvidiator;

import android.app.Activity;
import android.os.Bundle;
import android.widget.LinearLayout;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class AboutActivity extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        boolean license = getIntent().getBooleanExtra("license", false);
        if (license) {
            LinearLayout col = Ui.page(this, UiText.get(this, "menuLicense"));
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(getAssets().open("COPYING.txt"), "UTF-8"))) {
                String line;
                while ((line = r.readLine()) != null) sb.append(line).append('\n');
            } catch (Exception e) {
                sb.append("GNU General Public License, version 2");
            }
            Ui.text(this, col, sb.toString());
            Ui.button(this, col, UiText.get(this, "close"), v -> finish());
            return;
        }
        LinearLayout col = Ui.page(this, getString(R.string.app_name));
        Ui.text(this, col, getString(R.string.about_text, BuildConfig.VERSION_NAME));
        String py = NvdaPy.ready() ? getString(R.string.about_python_ok, NvdaPy.locale()) : getString(R.string.about_python_not_started);
        Ui.text(this, col, py);
        Ui.button(this, col, UiText.get(this, "close"), v -> finish());
    }
}
