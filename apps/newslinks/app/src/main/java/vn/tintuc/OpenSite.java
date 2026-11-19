package vn.tintuc;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

/** Opens the news site in Cốc Cốc (or any browser) and closes itself. */
public class OpenSite extends Activity {

    private static final String COC_COC = "com.coccoc.trinhduyet_tv";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent view = new Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.URL))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            // Cốc Cốc TV drops links sent through its dispatcher; its browsing screen takes them directly.
            startActivity(new Intent(view).setClassName(COC_COC, "coccoc.android_tv.WebSurfingActivity"));
        } catch (ActivityNotFoundException | SecurityException noCocCoc) {
            try {
                startActivity(view);
            } catch (ActivityNotFoundException noBrowser) {
                // Nothing on this TV can open a web page.
            }
        }
        finish();
    }
}
