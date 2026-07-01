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
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
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
                    "It's okay for two independent layouts to use the same ids. However, if layouts are combined with include tags, then the id's need to be unique within any chain of included layouts, or Activity#findViewById() can return an unexpected view.",
                    Category.CORRECTNESS,
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private Map<String, Map<String, Location>> layoutIdLocations = new HashMap<>();
    private Map<String, Set<String>> layoutIncludes = new HashMap<>();
    private String currentLayout;

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
        currentLayout = context.file.getName().replace(".xml", "");
        layoutIdLocations.put(currentLayout, new HashMap<>());
        layoutIncludes.put(currentLayout, new HashSet<>());
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        // No-op
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        layoutIdLocations.clear();
        layoutIncludes.clear();
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        Set<String> reported = new HashSet<>();
        for (String root : layoutIncludes.keySet()) {
            Set<String> visited = new HashSet<>();
            Map<String, Location> seenIds = new HashMap<>();
            Queue<String> queue = new ArrayDeque<>();
            queue.add(root);

            while (!queue.isEmpty()) {
                String current = queue.poll();
                if (!visited.add(current)) {
                    continue;
                }

                Map<String, Location> ids = layoutIdLocations.get(current);
                if (ids != null) {
                    for (Map.Entry<String, Location> entry : ids.entrySet()) {
                        String id = entry.getKey();
                        Location loc = entry.getValue();
                        if (seenIds.containsKey(id)) {
                            String reportKey = root + ":" + id;
                            if (reported.add(reportKey)) {
                                Location first = seenIds.get(id);
                                String msg = String.format(
                                        "Duplicate id `%s`, already defined in layout `%s` included from `%s`",
                                        id, first.getFile().getName(), root);
                                context.report(ISSUE, loc, msg);
                            }
                        } else {
                            seenIds.put(id, loc);
                        }
                    }
                }

                Set<String> includes = layoutIncludes.get(current);
                if (includes != null) {
                    queue.addAll(includes);
                }
            }
        }
        layoutIdLocations.clear();
        layoutIncludes.clear();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String layoutAttr = element.getAttribute("layout");
        if (layoutAttr != null && layoutAttr.startsWith("@layout/")) {
            String included = layoutAttr.substring(layoutAttr.indexOf('/') + 1);
            Set<String> includes = layoutIncludes.get(currentLayout);
            if (includes != null) {
                includes.add(included);
            }
        }
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String value = attribute.getValue();
        int slash = value.indexOf('/');
        if (slash != -1) {
            String id = value.substring(slash + 1);
            Map<String, Location> ids = layoutIdLocations.get(currentLayout);
            if (ids != null) {
                ids.put(id, context.getLocation(attribute));
            }
        }
    }

    @Override
    public String toString() {
        return "DuplicateIdDetector";
    }

    public int compareTo(DuplicateIdDetector other) {
        return 0;
    }
}