package app.windroid;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import app.windroid.WinDroidStoreCatalog.App;
import app.windroid.WinDroidStoreCatalog.Category;

public class WinDroidStoreCatalogTest {
    @Test
    public void searchRanksTheNamedApplicationBeforeReferencesInDescriptions() {
        WinDroidStoreCatalog.App primary = app("Mozilla.Firefox", "Mozilla Firefox (en-US)", "Web browser");
        WinDroidStoreCatalog.App related = app("Example.Downloads", "Download Manager", "Works with Firefox");
        assertTrue(primary.matches(new String[]{"firefox"}));
        assertTrue(related.matches(new String[]{"firefox"}));
        assertTrue(primary.searchRank("firefox") < related.searchRank("firefox"));
    }

    @Test
    public void highlightsRotateEveryAvailableCategoryWithoutAdjacentRepeats() {
        App vlc = app("VideoLAN.VLC", Category.MEDIA);
        App obs = app("OBSProject.OBSStudio", Category.MEDIA);
        App firefox = app("Mozilla.Firefox", Category.INTERNET);
        App git = app("Git.Git", Category.DEVELOPMENT);
        App hidden = app("Inkscape.Inkscape", Category.CREATIVE);
        StoreHighlights highlights = new StoreHighlights(Arrays.asList(vlc, obs, firefox, git, hidden),
                entry -> entry != hidden, new Random(4));
        EnumSet<Category> expected = EnumSet.of(Category.MEDIA, Category.INTERNET, Category.DEVELOPMENT);
        Category before = null;
        App previousMedia = null;
        for (int cycle = 0; cycle < 3; cycle++) {
            EnumSet<Category> seen = EnumSet.noneOf(Category.class);
            for (int position = 0; position < expected.size(); position++) {
                App current = highlights.next();
                assertNotEquals(before, current.category);
                before = current.category;
                seen.add(current.category);
                if (current.category == Category.MEDIA) {
                    assertNotEquals(previousMedia, current);
                    previousMedia = current;
                }
            }
            assertEquals(expected, seen);
        }
    }

    @Test
    public void noHighlightIsShownWhenTheAvailableAppsLackArtwork() {
        StoreHighlights highlights = new StoreHighlights(Collections.singletonList(app("VideoLAN.VLC", Category.MEDIA)),
                entry -> false, new Random(1));
        assertNull(highlights.next());
    }

    private static App app(String id, Category category) {
        return new App(id, id, "Publisher", "1", category, "", "", "", Collections.emptyList());
    }

    private static WinDroidStoreCatalog.App app(String id, String name, String description) {
        return new WinDroidStoreCatalog.App(id, name, "Publisher", "1", WinDroidStoreCatalog.Category.INTERNET,
                description, "", "", Collections.emptyList());
    }
}
