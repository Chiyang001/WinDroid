package app.windroid;

import androidx.annotation.StringRes;

import java.io.IOException;

final class WinDroidException extends IOException {
    @StringRes final int messageId;

    WinDroidException(@StringRes int messageId) {
        this.messageId = messageId;
    }

    WinDroidException(@StringRes int messageId, Throwable cause) {
        super(cause);
        this.messageId = messageId;
    }

    WinDroidException(@StringRes int messageId, String diagnostic) {
        super(diagnostic);
        this.messageId = messageId;
    }
}
