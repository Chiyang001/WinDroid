package app.windroid;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.LocaleList;

import androidx.appcompat.app.AppCompatDelegate;

import java.util.Locale;

public final class WinDroidPreferences {
    private static final String SETTINGS = "windroid_ui";
    private static final String LANGUAGE = "language";
    private static final String THEME = "theme";
    private static final String SYSTEM = "system";
    private static final String CHINESE = "zh";

    private WinDroidPreferences() {}

    public static String language(Context context) {
        return supportedLanguage(preferences(context).getString(LANGUAGE, CHINESE));
    }

    public static String theme(Context context) {
        return supportedTheme(preferences(context).getString(THEME, SYSTEM));
    }

    public static void setLanguage(Context context, String value) {
        preferences(context).edit().putString(LANGUAGE, supportedLanguage(value)).apply();
    }

    public static void setTheme(Context context, String value) {
        preferences(context).edit().putString(THEME, supportedTheme(value)).apply();
    }

    public static Context wrapLanguage(Context context) {
        LocaleList locales = new LocaleList(displayLocale(context));
        if (locales.equals(context.getResources().getConfiguration().getLocales())) return context;
        Configuration configuration = new Configuration();
        configuration.setLocales(locales);
        return context.createConfigurationContext(configuration);
    }

    public static boolean usesChinese(Context context) {
        return CHINESE.equals(displayLocale(context).getLanguage());
    }

    private static Locale displayLocale(Context context) {
        switch (language(context)) {
            case CHINESE:
                return Locale.SIMPLIFIED_CHINESE;
            case "en":
                return Locale.ENGLISH;
            default:
                return systemLanguage();
        }
    }

    public static int nightMode(Context context) {
        switch (theme(context)) {
            case "light": return AppCompatDelegate.MODE_NIGHT_NO;
            case "dark": return AppCompatDelegate.MODE_NIGHT_YES;
            default: return AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        }
    }

    private static String supportedLanguage(String value) {
        return "en".equals(value) || SYSTEM.equals(value) ? value : CHINESE;
    }

    private static Locale systemLanguage() {
        LocaleList system = Resources.getSystem().getConfiguration().getLocales();
        for (int index = 0; index < system.size(); index++) {
            Locale locale = system.get(index);
            if (CHINESE.equals(locale.getLanguage())) return Locale.SIMPLIFIED_CHINESE;
            if ("en".equals(locale.getLanguage())) return Locale.ENGLISH;
        }
        return Locale.SIMPLIFIED_CHINESE;
    }

    private static String supportedTheme(String value) {
        return "light".equals(value) || "dark".equals(value) ? value : SYSTEM;
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE);
    }
}
