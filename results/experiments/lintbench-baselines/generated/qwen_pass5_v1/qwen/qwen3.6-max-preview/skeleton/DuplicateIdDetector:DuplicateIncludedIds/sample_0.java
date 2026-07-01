package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
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

    private Map<String, Map<String, Location>> layoutIdLocations = new HashMap<>();
    private Map<String, Set<String>> layoutIncludes = new HashMap<>();

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
        // No per-file state initialization required
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        // No per-file cleanup required
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        layoutIdLocations.clear();
        layoutIncludes.clear();
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        Set<String> reported = new HashSet<>();
        for (String rootLayout : layoutIdLocations.keySet()) {
            Map<String, List<Location>> collected = new HashMap<>();
            collectIds(rootLayout, new HashSet<>(), collected);

            for (Map.Entry<String, List<Location>> entry : collected.entrySet()) {
                List<Location> locations = entry.getValue();
                if (locations.size() > 1) {
                    String id = entry.getKey();
                    Location primary = locations.get(0);
                    Location secondary = locations.get(1);
                    String dedupKey = id + "|" + primary.file.getPath() + "|" + secondary.file.getPath();
                    if (reported.add(dedupKey)) {
                        String message = String.format(
                                "Duplicate id `%1$s`, already defined in layout `%2$s`",
                                id, secondary.file.getName());
                        context.report(ISSUE, primary, message);
                    }
                }
            }
        }
    }

    private void collectIds(String layout, Set<String> visited, Map<String, List<Location>> outIds) {
        if (!visited.add(layout)) {
            return;
        }
        Map<String, Location> ids = layoutIdLocations.get(layout);
        if (ids != null) {
            for (Map.Entry<String, Location> entry : ids.entrySet()) {
                outIds.computeIfAbsent(entry.getKey(), k -> new ArrayList<>()).add(entry.getValue());
            }
        }
        Set<String> includes = layoutIncludes.get(layout);
        if (includes != null) {
            for (String inc : includes) {
                collectIds(inc, visited, outIds);
            }
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String layoutAttr = element.getAttribute("layout");
        if (layoutAttr != null && !layoutAttr.isEmpty() && layoutAttr.startsWith("@layout/")) {
            String includedLayout = layoutAttr.substring(8);
            String currentLayout = getLayoutName(context);
            layoutIncludes.computeIfAbsent(currentLayout, k -> new HashSet<>()).add(includedLayout);
        }
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String value = attribute.getValue();
        String id = null;
        if (value.startsWith("@+id/")) {
            id = value.substring(5);
        } else if (value.startsWith("@id/")) {
            id = value.substring(4);
        } else {
            return;
        }
        String layoutName = getLayoutName(context);
        layoutIdLocations.computeIfAbsent(layoutName, k -> new HashMap<>())
                .put(id, context.getLocation(attribute));
    }

    private static String getLayoutName(@NonNull XmlContext context) {
        String name = context.file.getName();
        if (name.endsWith(".xml")) {
            name = name.substring(0, name.length() - 4);
        }
        return name;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }

    public int compareTo(@NonNull DuplicateIdDetector other) {
        return 0;
    }
}