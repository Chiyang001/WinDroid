package app.windroid;

import android.content.Context;
import android.util.JsonReader;

import com.winlator.R;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.zip.GZIPInputStream;

final class WinDroidStoreCatalog {
    enum Category {
        ALL(R.string.wd_store_all), PRODUCTIVITY(R.string.wd_store_productivity),
        CREATIVE(R.string.wd_store_creative), MEDIA(R.string.wd_store_media),
        INTERNET(R.string.wd_store_internet), DEVELOPMENT(R.string.wd_store_development),
        UTILITIES(R.string.wd_store_utilities), GAMES(R.string.wd_store_games), OTHER(R.string.wd_store_other);

        final int title;

        Category(int title) {
            this.title = title;
        }

        static Category read(String key) {
            return valueOf(key.toUpperCase(Locale.ROOT));
        }
    }

    static final class Installer {
        final String architecture;
        final String extension;
        final String url;
        final String sha256;

        Installer(String architecture, String extension, String url, String sha256) {
            this.architecture = architecture;
            this.extension = extension;
            this.url = url;
            this.sha256 = sha256;
        }
    }

    static final class App {
        final String id;
        final String name;
        final String publisher;
        final String version;
        final Category category;
        final String description;
        final String chineseDescription;
        final List<Installer> installers;
        private final String searchable;
        private final String searchName;
        private final String searchId;

        App(String id, String name, String publisher, String version, Category category,
            String description, String chineseDescription, String tags, List<Installer> installers) {
            this.id = id;
            this.name = name;
            this.publisher = publisher;
            this.version = version;
            this.category = category;
            this.description = description;
            this.chineseDescription = chineseDescription;
            this.installers = Collections.unmodifiableList(installers);
            searchName = name.toLowerCase(Locale.ROOT);
            searchId = id.toLowerCase(Locale.ROOT);
            searchable = (name + " " + publisher + " " + id + " " + description + " " + chineseDescription + " " + tags)
                    .toLowerCase(Locale.ROOT);
        }

        boolean matches(String[] terms) {
            for (String term : terms) if (!searchable.contains(term)) return false;
            return true;
        }

        int searchRank(String query) {
            if (searchName.equals(query) || searchId.equals(query)
                    || searchId.endsWith("." + query)) return 0;
            if (searchName.startsWith(query)) return 1;
            if (searchName.contains(query) || searchId.contains(query)) return 2;
            return 3;
        }
    }

    static List<App> load(Context context) throws IOException {
        List<App> catalog = new ArrayList<>();
        try (GZIPInputStream input = new GZIPInputStream(context.getAssets().open("windroid/store-catalog.wdcatalog"));
             JsonReader json = new JsonReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            json.beginArray();
            while (json.hasNext()) {
                json.beginArray();
                String id = json.nextString();
                String name = json.nextString();
                String publisher = json.nextString();
                String version = json.nextString();
                Category category = Category.read(json.nextString());
                String description = json.nextString();
                String chinese = json.nextString();
                String tags = json.nextString();
                List<Installer> installers = new ArrayList<>(2);
                json.beginArray();
                while (json.hasNext()) {
                    json.beginArray();
                    installers.add(new Installer(json.nextString(), json.nextString(), json.nextString(), json.nextString()));
                    json.endArray();
                }
                json.endArray();
                json.endArray();
                if (!installers.isEmpty()) {
                    catalog.add(new App(id, name, publisher, version, category, description, chinese, tags, installers));
                }
            }
            json.endArray();
        }
        return Collections.unmodifiableList(catalog);
    }
}
