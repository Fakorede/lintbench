package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
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

public class DuplicateIdDetector extends LayoutDetector implements XmlScanner, Comparable<DuplicateIdDetector> {

    public static final Issue DUPLICATE_INCLUDED_IDS =
            Issue.create(
                    "DuplicateIncludedIds",
                    "Duplicate IDs across layouts combined with include tags",
                    "It's okay for two independent layouts to use the same ids. However, if layouts are "
                            + "combined with include tags, then the id's need to be unique within any chain of "
                            + "included layouts, or `Activity#findViewById()` can return an unexpected view.",
                    Category.CORRECTNESS,
                    5,
                    Severity.WARNING,
                    new Implementation(DuplicateIdDetector.class, Scope.ALL_RESOURCES_SCOPE));

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String ID_ATTR = "id";
    private static final String INCLUDE_TAG = "include";
    private static final String LAYOUT_PREFIX = "@layout/";

    private Map<File, Set<String>> mIds;
    private Map<File, Set<File>> mIncludes;
    private Map<File, Map<String, List<Location>>> mIdLocations;

    private File mCurrentFile;
    private Set<String> mCurrentIds;
    private Set<File> mCurrentIncludes;
    private Map<String, List<Location>> mCurrentIdLocations;

    @Override
    public boolean appliesTo(LayoutDetector.ResourceFileType folderType) {
        return folderType == LayoutDetector.ResourceFileType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ID_ATTR);
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(INCLUDE_TAG);
    }

    @Override
    public void beforeCheckRootProject(Context context) {
        mIds = new HashMap<>();
        mIncludes = new HashMap<>();
        mIdLocations = new HashMap<>();
    }

    @Override
    public void afterCheckRootProject(Context context) {
        Set<String> reported = new HashSet<>();

        for (File file : mIds.keySet()) {
            Set<File> reachable = new HashSet<>();
            collectReachable(file, reachable);

            Map<String, List<File>> idToFiles = new HashMap<>();
            for (File reached : reachable) {
                Set<String> ids = mIds.get(reached);
                if (ids == null) {
                    continue;
                }
                for (String id : ids) {
                    idToFiles.computeIfAbsent(id, k -> new ArrayList<>()).add(reached);
                }
            }

            for (Map.Entry<String, List<File>> entry : idToFiles.entrySet()) {
                List<File> files = entry.getValue();
                if (files.size() < 2) {
                    continue;
                }

                String id = entry.getKey();
                String signature = createSignature(id, files);
                if (!reported.add(signature)) {
                    continue;
                }

                reportDuplicate(context, id, files);
            }
        }
    }

    @Override
    public void beforeCheckFile(XmlContext context) {
        mCurrentFile = context.file;
        mCurrentIds = new HashSet<>();
        mCurrentIncludes = new HashSet<>();
        mCurrentIdLocations = new HashMap<>();
    }

    @Override
    public void afterCheckFile(XmlContext context) {
        mIds.put(mCurrentFile, mCurrentIds);
        mIncludes.put(mCurrentFile, mCurrentIncludes);
        mIdLocations.put(mCurrentFile, mCurrentIdLocations);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String layout = element.getAttribute("layout");
        if (layout == null || !layout.startsWith(LAYOUT_PREFIX)) {
            return;
        }

        String name = layout.substring(LAYOUT_PREFIX.length());
        if (name.isEmpty()) {
            return;
        }

        File parent = mCurrentFile.getParentFile();
        if (parent != null) {
            File included = new File(parent, name + ".xml");
            if (included.exists() && included.isFile()) {
                mCurrentIncludes.add(included);
            }
        }
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        if (!ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }

        String value = attribute.getValue();
        if (value == null || value.isEmpty()) {
            return;
        }

        String id = getIdName(value);
        if (id.isEmpty()) {
            return;
        }

        mCurrentIds.add(id);
        mCurrentIdLocations
                .computeIfAbsent(id, k -> new ArrayList<>())
                .add(context.getLocation(attribute));
    }

    private static String getIdName(String value) {
        int index = value.lastIndexOf('/');
        return index >= 0 ? value.substring(index + 1) : value;
    }

    private void collectReachable(File file, Set<File> reachable) {
        if (!reachable.add(file)) {
            return;
        }
        Set<File> includes = mIncludes.get(file);
        if (includes == null) {
            return;
        }
        for (File included : includes) {
            collectReachable(included, reachable);
        }
    }

    private static String createSignature(String id, List<File> files) {
        List<String> paths = new ArrayList<>(files.size());
        for (File file : files) {
            paths.add(file.getPath());
        }
        Collections.sort(paths);

        StringBuilder sb = new StringBuilder();
        sb.append(id);
        for (String path : paths) {
            sb.append('|').append(path);
        }
        return sb.toString();
    }

    private void reportDuplicate(Context context, String id, List<File> files) {
        List<Location> locations = new ArrayList<>();
        for (File file : files) {
            Map<String, List<Location>> map = mIdLocations.get(file);
            if (map == null) {
                continue;
            }
            List<Location> list = map.get(id);
            if (list != null) {
                locations.addAll(list);
            }
        }

        if (locations.isEmpty()) {
            return;
        }

        Location primary = locations.get(0);
        Location location;
        if (locations.size() == 1) {
            location = primary;
        } else {
            location =
                    Location.create(
                            primary.getFile(),
                            primary.getStart(),
                            primary.getEnd(),
                            locations.subList(1, locations.size()));
        }

        context.report(
                DUPLICATE_INCLUDED_IDS,
                location,
                "Duplicate id @id/" + id + " across layouts connected by <include> tags");
    }

    @Override
    public String toString() {
        return DuplicateIdDetector.class.getName();
    }

    @Override
    public int compareTo(DuplicateIdDetector other) {
        return toString().compareTo(other.toString());
    }
}