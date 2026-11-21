package app.windroid;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class WinDroidLibrary {
    static final String PREFERENCES = "windroid";
    private static final String LIBRARY = "library";
    private static final String INSTALLED = "detected_programs";
    private static final String MANUAL_TARGETS = "manual_program_targets";
    private static final String HIDDEN_TARGETS = "hidden_program_targets";
    private static final String HOME_FAVORITES = "home_favorites";
    private static final String LAUNCH_HISTORY = "home_launch_history";
    private static final int RECENT_LIMIT = 12;
    private static final String TAG = "WinDroidLibrary";

    private WinDroidLibrary() {}

    static synchronized List<String> apps(SharedPreferences preferences) {
        return new ArrayList<>(new Catalog(preferences).visible().keySet());
    }

    static synchronized List<String> manualApps(SharedPreferences preferences) {
        return new ArrayList<>(new Catalog(preferences).manual.keySet());
    }

    static synchronized List<String> availableProgramLinks(SharedPreferences preferences) {
        Catalog programs = new Catalog(preferences);
        Set<String> targets = new LinkedHashSet<>(programs.visible().values());
        List<String> links = new ArrayList<>();
        programs.installed.forEach((path, target) -> {
            if (targets.add(target)) links.add(path);
        });
        return links;
    }

    static synchronized boolean hasVisibleTarget(SharedPreferences preferences, String target) {
        return new Catalog(preferences).visible().containsValue(target);
    }

    static synchronized boolean add(SharedPreferences preferences, String path) {
        Catalog programs = new Catalog(preferences);
        Map<String, String> before = programs.visible();
        String target = programs.identity(path);
        programs.manual.put(path, true);
        if (target != null) programs.hidden.remove(target);
        SharedPreferences.Editor edits = programs.saveManualAndHidden(preferences);
        reconcileHome(preferences, edits, before, programs.visible());
        return edits.commit();
    }

    static synchronized boolean remove(SharedPreferences preferences, String path) {
        Catalog programs = new Catalog(preferences);
        Map<String, String> before = programs.visible();
        String target = programs.identity(path);
        programs.manual.remove(path);
        if (target != null) programs.hidden.add(target);
        SharedPreferences.Editor edits = programs.saveManualAndHidden(preferences);
        before.remove(path);
        reconcileHome(preferences, edits, before, programs.visible());
        return edits.commit();
    }

    static synchronized boolean updateInstalledApps(SharedPreferences preferences, Map<String, String> installed,
                                                     Map<String, String> manualTargets) {
        Catalog programs = new Catalog(preferences);
        if (programs.installed.equals(installed) && programs.manualTargets.equals(manualTargets)) return false;
        Map<String, String> before = programs.visible();
        programs.installed = installed;
        programs.manualTargets = manualTargets;
        Map<String, String> after = programs.visible();
        SharedPreferences.Editor edits = preferences.edit()
                .putString(INSTALLED, new JSONObject(installed).toString())
                .putString(MANUAL_TARGETS, new JSONObject(manualTargets).toString());
        reconcileHome(preferences, edits, before, after);
        edits.apply();
        return !before.equals(after);
    }

    static synchronized void forgetUninstalledTarget(SharedPreferences preferences, String entry, String target) {
        Catalog programs = new Catalog(preferences);
        Map<String, String> before = programs.visible();
        programs.manual.keySet().removeIf(path -> path.equals(entry) || target.equals(programs.identity(path)));
        programs.hidden.remove(target);
        SharedPreferences.Editor edits = programs.saveManualAndHidden(preferences);
        reconcileHome(preferences, edits, before, programs.visible());
        edits.apply();
    }

    static synchronized List<String> recent(SharedPreferences preferences) {
        List<String> paths = new ArrayList<>(readLaunches(preferences, new Catalog(preferences).visible().keySet()).keySet());
        return new ArrayList<>(paths.subList(0, Math.min(paths.size(), RECENT_LIMIT)));
    }

    static synchronized List<String> frequent(SharedPreferences preferences) {
        Map<String, Long> launches = readLaunches(preferences, new Catalog(preferences).visible().keySet());
        List<String> paths = new ArrayList<>(launches.keySet());
        // The launch map is ordered newest first, and stable sorting preserves that order for ties.
        paths.sort((first, second) -> Long.compare(launches.get(second), launches.get(first)));
        return paths;
    }

    static synchronized List<String> favorites(SharedPreferences preferences) {
        return new ArrayList<>(readFavorites(preferences, new Catalog(preferences).visible().keySet()));
    }

    static synchronized boolean favoritesCustomized(SharedPreferences preferences) {
        return preferences.contains(HOME_FAVORITES);
    }

    static synchronized boolean resetFavorites(SharedPreferences preferences) {
        return preferences.edit().remove(HOME_FAVORITES).commit();
    }

    static synchronized boolean setFavorite(SharedPreferences preferences, String path, boolean favorite) {
        Set<String> library = new Catalog(preferences).visible().keySet();
        if (!library.contains(path)) return false;
        Set<String> paths = readFavorites(preferences, library);
        if (favorite) paths.add(path);
        else paths.remove(path);
        return saveFavorites(preferences, paths);
    }

    static synchronized boolean setFavorites(SharedPreferences preferences, List<String> paths) {
        Set<String> library = new Catalog(preferences).visible().keySet();
        Set<String> selected = new LinkedHashSet<>(paths);
        selected.retainAll(library);
        Set<String> ordered = readFavorites(preferences, library);
        ordered.retainAll(selected);
        ordered.addAll(selected);
        return saveFavorites(preferences, ordered);
    }

    public static synchronized void recordLaunch(Context context, String path) {
        if (path == null) return;
        SharedPreferences preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
        Set<String> library = new Catalog(preferences).visible().keySet();
        if (!library.contains(path)) return;
        Map<String, Long> launches = readLaunches(preferences, library);
        long count = launches.getOrDefault(path, 0L);
        launches.remove(path);
        if (count < Long.MAX_VALUE) count++;
        Map<String, Long> updated = new LinkedHashMap<>();
        updated.put(path, count);
        updated.putAll(launches);
        preferences.edit().putString(LAUNCH_HISTORY, encodeLaunches(updated)).apply();
    }

    private static JSONObject readObject(SharedPreferences preferences, String key) {
        try {
            return new JSONObject(preferences.getString(key, "{}"));
        }
        catch (JSONException error) {
            Log.w(TAG, "Unable to read " + key, error);
            return new JSONObject();
        }
    }

    private static Set<String> readFavorites(SharedPreferences preferences, Set<String> library) {
        Set<String> paths = readStrings(preferences, HOME_FAVORITES);
        paths.retainAll(library);
        return paths;
    }

    private static boolean saveFavorites(SharedPreferences preferences, Set<String> paths) {
        return preferences.edit().putString(HOME_FAVORITES, new JSONArray(paths).toString()).commit();
    }

    private static Map<String, Long> readLaunches(SharedPreferences preferences, Set<String> library) {
        JSONArray saved = readArray(preferences, LAUNCH_HISTORY);
        Map<String, Long> launches = new LinkedHashMap<>();
        for (int index = 0; index < saved.length(); index++) {
            JSONArray entry = saved.optJSONArray(index);
            if (entry == null) continue;
            String path = entry.optString(0, null);
            long count = entry.optLong(1, 0);
            if (count > 0 && library.contains(path)) launches.putIfAbsent(path, count);
        }
        return launches;
    }

    private static String encodeLaunches(Map<String, Long> launches) {
        JSONArray saved = new JSONArray();
        for (Map.Entry<String, Long> entry : launches.entrySet()) {
            saved.put(new JSONArray().put(entry.getKey()).put(entry.getValue().longValue()));
        }
        return saved.toString();
    }

    private static JSONArray readArray(SharedPreferences preferences, String key) {
        try {
            return new JSONArray(preferences.getString(key, "[]"));
        }
        catch (JSONException error) {
            Log.w(TAG, "Unable to read " + key, error);
            return new JSONArray();
        }
    }

    private static Set<String> readStrings(SharedPreferences preferences, String key) {
        JSONArray saved = readArray(preferences, key);
        Set<String> paths = new LinkedHashSet<>();
        for (int index = 0; index < saved.length(); index++) {
            Object path = saved.opt(index);
            if (path instanceof String) paths.add((String) path);
        }
        return paths;
    }

    private static void reconcileHome(SharedPreferences preferences, SharedPreferences.Editor edits,
                                      Map<String, String> before, Map<String, String> after) {
        Map<String, String> replacements = new LinkedHashMap<>();
        after.forEach((path, target) -> {
            if (target != null) replacements.putIfAbsent(target, path);
        });
        if (preferences.contains(HOME_FAVORITES)) {
            Set<String> favorites = new LinkedHashSet<>();
            for (String path : readFavorites(preferences, before.keySet())) {
                String current = after.containsKey(path) ? path : replacements.get(before.get(path));
                if (current != null) favorites.add(current);
            }
            edits.putString(HOME_FAVORITES, new JSONArray(favorites).toString());
        }
        if (preferences.contains(LAUNCH_HISTORY)) {
            Map<String, Long> launches = new LinkedHashMap<>();
            readLaunches(preferences, before.keySet()).forEach((path, count) -> {
                String current = after.containsKey(path) ? path : replacements.get(before.get(path));
                if (current != null) launches.merge(current, count, (first, second) ->
                        Long.MAX_VALUE - first < second ? Long.MAX_VALUE : first + second);
            });
            edits.putString(LAUNCH_HISTORY, encodeLaunches(launches));
        }
    }

    private static final class Catalog {
        private final Map<String, Object> manual = new LinkedHashMap<>();
        private final Set<String> hidden;
        private Map<String, String> installed;
        private Map<String, String> manualTargets;

        Catalog(SharedPreferences preferences) {
            JSONObject saved = readObject(preferences, LIBRARY);
            saved.keys().forEachRemaining(path -> manual.put(path, saved.opt(path)));
            installed = readTargets(preferences, INSTALLED);
            manualTargets = readTargets(preferences, MANUAL_TARGETS);
            hidden = readStrings(preferences, HIDDEN_TARGETS);
        }

        private String identity(String path) {
            String target = installed.get(path);
            if (target == null) target = manualTargets.get(path);
            if (target == null && WindowsExecutable.hasExeExtension(path)) target = path;
            return target;
        }

        private Map<String, String> visible() {
            Map<String, String> visible = new LinkedHashMap<>();
            Set<String> targets = new LinkedHashSet<>();
            for (String path : manual.keySet()) {
                String target = identity(path);
                visible.put(path, target);
                targets.add(target);
            }
            installed.forEach((path, target) -> {
                if (!hidden.contains(target) && targets.add(target)) visible.put(path, target);
            });
            return visible;
        }

        private SharedPreferences.Editor saveManualAndHidden(SharedPreferences preferences) {
            return preferences.edit().putString(LIBRARY, new JSONObject(manual).toString())
                    .putString(HIDDEN_TARGETS, new JSONArray(hidden).toString());
        }

        private static Map<String, String> readTargets(SharedPreferences preferences, String key) {
            JSONObject saved = readObject(preferences, key);
            Map<String, String> targets = new LinkedHashMap<>();
            saved.keys().forEachRemaining(path -> {
                Object target = saved.opt(path);
                if (target instanceof String) targets.put(path, (String) target);
            });
            return targets;
        }
    }
}
