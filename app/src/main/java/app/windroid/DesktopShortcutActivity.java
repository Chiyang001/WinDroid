package app.windroid;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.winlator.R;
import com.winlator.XServerDisplayActivity;

public final class DesktopShortcutActivity extends AppCompatActivity {
    private boolean sessionStarted;
    private final ActivityResultLauncher<Intent> window = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                Intent data = result.getData();
                close(data == null ? null : data.getStringExtra(XServerDisplayActivity.WINDROID_ERROR));
            });

    @Override
    protected void attachBaseContext(Context context) {
        super.attachBaseContext(WinDroidPreferences.wrapLanguage(context));
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        sessionStarted = state != null && state.getBoolean("sessionStarted");
        if (sessionStarted) return;
        if (!DesktopShortcuts.OPEN_ACTION.equals(getIntent().getAction())) {
            finish();
            return;
        }
        if (WinDroidModel.blocksWindowsLaunch(this)) {
            close(getString(R.string.wd_vm_reset_busy));
            return;
        }

        final String path;
        try {
            path = DesktopShortcuts.pinnedPath(this, getIntent().getStringExtra(DesktopShortcuts.SHORTCUT_ID));
        }
        catch (RuntimeException error) {
            close(getString(R.string.wd_error_desktop_open));
            return;
        }
        if (path == null) {
            close(getString(R.string.wd_error_desktop_expired));
            return;
        }
        if (XServerDisplayActivity.resumeWinDroidSession(this, path)) {
            finish();
            return;
        }

        WinDroidModel model = new ViewModelProvider(this).get(WinDroidModel.class);
        model.status.observe(this, status -> {
            if (sessionStarted || isFinishing()) return;
            if (WinDroidModel.blocksWindowsLaunch(this)) {
                close(getString(R.string.wd_vm_reset_busy));
                return;
            }
            if (status.kind == WinDroidModel.Status.Kind.READY) model.launchHomeScreenShortcut(path);
            if (status.kind == WinDroidModel.Status.Kind.ERROR) close(status.message(this));
        });
        model.launch.observe(this, request -> {
            if (request == null || sessionStarted || isFinishing()) return;
            sessionStarted = true;
            model.launch.setValue(null);
            if (XServerDisplayActivity.resumeWinDroidSession(this, path)) finish();
            else window.launch(request.intent(this));
        });
        model.prepare(false);
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        state.putBoolean("sessionStarted", sessionStarted);
        super.onSaveInstanceState(state);
    }

    private void close(String message) {
        if (message != null) Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        finish();
    }
}
