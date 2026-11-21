package app.windroid;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;

import app.windroid.WinDroidStoreCatalog.App;
import app.windroid.WinDroidStoreCatalog.Category;

final class StoreHighlights {
    private static final Set<String> PICKS = new HashSet<>(Arrays.asList(
            "VideoLAN.VLC", "OBSProject.OBSStudio", "HandBrake.HandBrake",
            "SumatraPDF.SumatraPDF", "KDE.Okular", "Microsoft.PowerToys",
            "BlenderFoundation.Blender", "Inkscape.Inkscape",
            "Mozilla.Firefox", "WiresharkFoundation.Wireshark", "KDE.Falkon",
            "Git.Git", "Microsoft.VisualStudioCode", "Kitware.CMake",
            "LocalSend.LocalSend", "KeePassXCTeam.KeePassXC",
            "PCSX2Team.PCSX2", "OpenTTD.OpenTTD",
            "Anki.Anki", "Stellarium.Stellarium", "KDE.KStars"));

    private final EnumMap<Category, List<App>> apps = new EnumMap<>(Category.class);
    private final ArrayDeque<Category> remaining = new ArrayDeque<>();
    private final Random random;
    private Category previous;

    StoreHighlights(List<App> catalog, Predicate<App> hasIcon, Random random) {
        this.random = random;
        for (App app : catalog) {
            if (app.category != Category.ALL && PICKS.contains(app.id) && hasIcon.test(app)) {
                apps.computeIfAbsent(app.category, category -> new ArrayList<>()).add(app);
            }
        }
        for (List<App> candidates : apps.values()) Collections.shuffle(candidates, random);
    }

    App next() {
        if (apps.isEmpty()) return null;
        if (remaining.isEmpty()) {
            List<Category> categories = new ArrayList<>(apps.keySet());
            Collections.shuffle(categories, random);
            if (categories.size() > 1 && categories.get(0) == previous) Collections.swap(categories, 0, 1);
            remaining.addAll(categories);
        }
        previous = remaining.removeFirst();
        List<App> candidates = apps.get(previous);
        App app = candidates.remove(0);
        candidates.add(app);
        return app;
    }
}
