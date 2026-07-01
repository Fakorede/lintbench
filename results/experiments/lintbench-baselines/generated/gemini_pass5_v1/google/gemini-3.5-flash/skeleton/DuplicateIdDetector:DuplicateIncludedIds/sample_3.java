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

    private final Map<String, List<IdOccurrence>> mLayoutToIds = new HashMap<>();
    private final Map<String, List<IncludeOccurrence>> mLayoutToIncludes = new HashMap<>();

    private static class IdOccurrence {
        final String id;
        final Location location;

        IdOccurrence(String id, Location location) {
            this.id = id;
            this.location = location;
        }
    }

    private static class IncludeOccurrence {
        final String layout;
        final Location location;

        IncludeOccurrence(String layout, Location location) {
            this.layout = layout;
            this.location = location;
        }
    }

    private static class IdSource {
        final String layoutName;
        final Location location;
        final String path;

        IdSource(String layoutName, Location location, String path) {
            this.layoutName = layoutName;
            this.location = location;
            this.path = path;
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
    public void beforeCheckFile(@NonNull Context context) {
        // No-op
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        // No-op
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mLayoutToIds.clear();
        mLayoutToIncludes.clear();
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        Set<String> includedLayouts = new HashSet<>();
        for (List<IncludeOccurrence> includes : mLayoutToIncludes.values()) {
            for (IncludeOccurrence inc : includes) {
                includedLayouts.add(inc.layout);
            }
        }

        Set<String> processedRoots = new HashSet<>();

        // 1. Process true roots
        for (String layoutName : mLayoutToIncludes.keySet()) {
            if (!includedLayouts.contains(layoutName)) {
                analyzeLayoutHierarchy(context, layoutName, processedRoots);
            }
        }

        // 2. Process any remaining layouts that have includes but weren't visited
        for (String layoutName : mLayoutToIncludes.keySet()) {
            if (!processedRoots.contains(layoutName)) {
                analyzeLayoutHierarchy(context, layoutName, processedRoots);
            }
        }
    }

    private void analyzeLayoutHierarchy(Context context, String rootLayout, Set<String> processedRoots) {
        Map<String, List<IdSource>> idToSources = new HashMap<>();
        Set<String> visited = new HashSet<>();

        collectIdsAndIncludes(rootLayout, idToSources, visited, "");

        processedRoots.addAll(visited);

        for (Map.Entry<String, List<IdSource>> entry : idToSources.entrySet()) {
            String id = entry.getKey();
            List<IdSource> sources = entry.getValue();
            if (sources.size() > 1) {
                reportDuplicate(context, id, sources);
            }
        }
    }

    private void collectIdsAndIncludes(
            String layoutName,
            Map<String, List<IdSource>> idToSources,
            Set<String> visited,
            String path) {

        if (visited.contains(layoutName)) {
            return;
        }
        visited.add(layoutName);

        String currentPath = path.isEmpty() ? layoutName : path + " -> " + layoutName;

        List<IdOccurrence> ids = mLayoutToIds.get(layoutName);
        if (ids != null) {
            for (IdOccurrence occ : ids) {
                idToSources.computeIfAbsent(occ.id, k -> new ArrayList<>())
                        .add(new IdSource(layoutName, occ.location, currentPath));
            }
        }

        List<IncludeOccurrence> includes = mLayoutToIncludes.get(layoutName);
        if (includes != null) {
            for (IncludeOccurrence inc : includes) {
                collectIdsAndIncludes(inc.layout, idToSources, visited, currentPath);
            }
        }

        visited.remove(layoutName);
    }

    private void reportDuplicate(Context context, String id, List<IdSource> sources) {
        if (sources.isEmpty()) return;

        IdSource primary = sources.get(0);
        Location primaryLocation = primary.location;
        if (primaryLocation == null) return;

        Location current = primaryLocation;
        for (int i = 1; i < sources.size(); i++) {
            Location secondary = sources.get(i).location;
            if (secondary != null) {
                secondary.setMessage("Duplicate id `" + id + "` here");
                current.setSecondary(secondary);
                current = secondary;
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Duplicate id `").append(id).append("` in layout ");
        sb.append(primary.layoutName).append(" (via ").append(primary.path).append(")");
        sb.append(" and other included layouts:\n");
        for (int i = 1; i < sources.size(); i++) {
            IdSource src = sources.get(i);
            sb.append("- ").append(src.layoutName).append(" (via ").append(src.path).append(")\n");
        }

        context.report(ISSUE, primaryLocation, sb.toString());
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if ("include".equals(element.getTagName())) {
            String layoutAttr = element.getAttribute("layout");
            if (layoutAttr != null && !layoutAttr.isEmpty()) {
                String includedLayout = stripLayoutPrefix(layoutAttr);
                if (includedLayout != null && !includedLayout.isEmpty()) {
                    String layoutName = getLayoutName(context);
                    Location location = context.getLocation(element);
                    mLayoutToIncludes.computeIfAbsent(layoutName, k -> new ArrayList<>())
                            .add(new IncludeOccurrence(includedLayout, location));
                }
            }
        }
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String namespace = attribute.getNamespaceURI();
        if ("http://schemas.android.com/apk/res/android".equals(namespace)) {
            String value = attribute.getValue();
            String id = stripIdPrefix(value);
            if (id != null && !id.isEmpty()) {
                String layoutName = getLayoutName(context);
                Location location = context.getLocation(attribute);
                mLayoutToIds.computeIfAbsent(layoutName, k -> new ArrayList<>())
                        .add(new IdOccurrence(id, location));
            }
        }
    }

    private String getLayoutName(XmlContext context) {
        String fileName = context.file.getName();
        if (fileName.endsWith(".xml")) {
            return fileName.substring(0, fileName.length() - 4);
        }
        return fileName;
    }

    private String stripIdPrefix(String id) {
        if (id == null) return null;
        if (id.startsWith("@+id/")) {
            return id.substring(5);
        } else if (id.startsWith("@id/")) {
            return id.substring(4);
        }
        return id;
    }

    private String stripLayoutPrefix(String layout) {
        if (layout == null) return null;
        if (layout.startsWith("@layout/")) {
            return layout.substring(8);
        }
        return layout;
    }

    @Override
    @NonNull
    public String toString() {
        return "DuplicateIdDetector";
    }

    public int compareTo(@NonNull DuplicateIdDetector other) {
        return 0;
    }
}