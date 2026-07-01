package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class DuplicateIdDetector extends LayoutDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(DuplicateIdDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "DuplicateIncludedIds",
                    "Duplicate ids across layouts combined with include tags",
                    "It's okay for two independent layouts to use the same ids. However, if "
                            + "layouts are combined with include tags, then the id's need to be unique "
                            + "within any chain of included layouts, or `Activity#findViewById()` can "
                            + "return an unexpected view.",
                    Category.CORRECTNESS,
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private Map<File, List<Occurrence>> mIds;
    private Map<File, List<IncludeReference>> mIncludes;

    private static class Occurrence implements Comparable<Occurrence> {
        final String id;
        final File file;
        final Location location;

        Occurrence(String id, File file, Location location) {
            this.id = id;
            this.file = file;
            this.location = location;
        }

        @Override
        public String toString() {
            return id;
        }

        @Override
        public int compareTo(Occurrence other) {
            return id.compareTo(other.id);
        }
    }

    private static class IncludeReference {
        final File layout;
        final Location location;

        IncludeReference(File layout, Location location) {
            this.layout = layout;
            this.location = location;
        }
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList("id");
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("include");
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {}

    @Override
    public void afterCheckFile(@NonNull Context context) {}

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mIds = new HashMap<>();
        mIncludes = new HashMap<>();
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (mIds == null) {
            return;
        }

        for (File file : mIds.keySet()) {
            Set<File> visited = new HashSet<>();
            Map<String, Set<File>> fileMap = new HashMap<>();
            Map<String, List<Location>> locationMap = new HashMap<>();
            collectIds(file, visited, fileMap, locationMap);

            for (Map.Entry<String, Set<File>> entry : fileMap.entrySet()) {
                if (entry.getValue().size() > 1) {
                    List<Location> locations = locationMap.get(entry.getKey());
                    if (locations != null && locations.size() > 1) {
                        Location location = locations.get(0);
                        Location chain = location;
                        for (int i = 1; i < locations.size(); i++) {
                            Location next = locations.get(i);
                            chain.setSecondary(next);
                            chain = next;
                        }

                        String message =
                                String.format(
                                        "Duplicate id @id/%s across included layouts",
                                        entry.getKey());
                        context.report(ISSUE, location, message);
                    }
                }
            }
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String layout = element.getAttribute("layout");
        if (layout == null || !layout.startsWith("@layout/")) {
            return;
        }

        String layoutName = layout.substring("@layout/".length());
        File layoutFile = findLayoutFile(context, layoutName);
        if (layoutFile == null) {
            return;
        }

        List<IncludeReference> list = mIncludes.get(context.file);
        if (list == null) {
            list = new ArrayList<>();
            mIncludes.put(context.file, list);
        }
        list.add(new IncludeReference(layoutFile, context.getLocation(element)));
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String value = attribute.getValue();
        String id = extractId(value);
        if (id == null) {
            return;
        }

        List<Occurrence> list = mIds.get(context.file);
        if (list == null) {
            list = new ArrayList<>();
            mIds.put(context.file, list);
        }
        list.add(new Occurrence(id, context.file, context.getLocation(attribute)));
    }

    private static String extractId(String value) {
        if (value.startsWith("@+id/")) {
            return value.substring("@+id/".length());
        } else if (value.startsWith("@id/")) {
            return value.substring("@id/".length());
        }
        return null;
    }

    private static File findLayoutFile(XmlContext context, String layoutName) {
        File file = context.file;
        File parent = file.getParentFile();
        if (parent == null) {
            return null;
        }
        File resDir = parent.getParentFile();
        if (resDir == null) {
            return null;
        }

        File target = new File(parent, layoutName + ".xml");
        if (target.exists()) {
            return target;
        }

        File[] layoutDirs = resDir.listFiles((dir, name) -> name.startsWith("layout"));
        if (layoutDirs != null) {
            for (File layoutDir : layoutDirs) {
                if (layoutDir.equals(parent)) {
                    continue;
                }
                target = new File(layoutDir, layoutName + ".xml");
                if (target.exists()) {
                    return target;
                }
            }
        }
        return null;
    }

    private void collectIds(File file, Set<File> visited, Map<String, Set<File>> fileMap,
            Map<String, List<Location>> locationMap) {
        if (!visited.add(file)) {
            return;
        }

        List<Occurrence> occurrences = mIds.get(file);
        if (occurrences != null) {
            for (Occurrence occurrence : occurrences) {
                Set<File> files = fileMap.get(occurrence.id);
                if (files == null) {
                    files = new HashSet<>();
                    fileMap.put(occurrence.id, files);
                }
                files.add(occurrence.file);

                List<Location> locations = locationMap.get(occurrence.id);
                if (locations == null) {
                    locations = new ArrayList<>();
                    locationMap.put(occurrence.id, locations);
                }
                locations.add(occurrence.location);
            }
        }

        List<IncludeReference> includes = mIncludes.get(file);
        if (includes != null) {
            for (IncludeReference include : includes) {
                collectIds(include.layout, visited, fileMap, locationMap);
            }
        }
    }
}