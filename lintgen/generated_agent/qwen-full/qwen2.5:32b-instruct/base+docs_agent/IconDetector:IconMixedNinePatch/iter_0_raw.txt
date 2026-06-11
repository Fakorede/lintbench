package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.Density;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceRepository;
import com.android.resources.ResourceType;
import com.android.utils.Pair;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.Collection;

import javax.annotation.Nullable;

public class IconDetector extends ResourceXmlScanner {

    private Multimap<String, Pair<ResourceRepository, String>> mDrawableMap =
            HashMultimap.create();

    @Override
    public Collection<String> getApplicableResourceTypes() {
        return ResourceType.DRAWABLE.getApplicableFiles();
    }

    @Nullable
    @Override
    public Document onResourceFileScanned(@NonNull ResourceRepository repository,
                                          @NonNull ResourceType type, @NonNull String name,
                                          @NonNull Density density, @NonNull String sourcePath)
            throws IOException, SAXException {

        if (name.endsWith(".png") || name.endsWith(".9.png")) {
            // Remove the extension to get the base name
            String baseName = name.substring(0, name.lastIndexOf('.'));

            mDrawableMap.put(baseName, Pair.of(repository, sourcePath));
        }

        return null;
    }

    @Override
    public void afterCheckProject() {
        for (String key : mDrawableMap.keySet()) {
            Collection<Pair<ResourceRepository, String>> values = mDrawableMap.get(key);
            if (values.size() > 1) {
                boolean hasPng = false;
                boolean hasNinePatch = false;

                for (Pair<ResourceRepository, String> pair : values) {
                    String path = pair.second;
                    if (path.endsWith(".png")) {
                        hasPng = true;
                    } else if (path.endsWith(".9.png")) {
                        hasNinePatch = true;
                    }
                }

                if (hasPng && hasNinePatch) {
                    for (Pair<ResourceRepository, String> pair : values) {
                        String path = pair.second;
                        Location location = createLocation(ResourceFolderType.DRAWABLE,
                                ResourceType.DRAWABLE, null, path);
                        if (path.endsWith(".png")) {
                            report(location, "Clashing PNG and 9-PNG files: " + key);
                        } else if (path.endsWith(".9.png")) {
                            report(location, "Clashing PNG and 9-PNG files: " + key);
                        }
                    }
                }
            }
        }

        mDrawableMap.clear();
    }

    private void report(Location location, String message) {
        issueReporter().report(
                location,
                IconDetector.ISSUE,
                message,
                null
        );
    }

    public static final Issue ISSUE = Issue.create(
            "ClashingIcons",
            "Clashing PNG and 9-PNG files",
            "If you accidentally name two separate resources `file.png` and `file.9.png`, the image file and the nine patch file will both map to the same drawable resource, `@drawable/file`, which is probably not what was intended.",
            Category.CORRECTNESS,
            5,
            Severity.ERROR,
            new IconDetector()
    );
}