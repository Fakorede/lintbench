package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
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
import com.android.tools.lint.detector.api.XmlScanner;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DuplicateIdDetector extends LayoutDetector implements XmlScanner, Comparable<Detector> {

    public static final Issue ISSUE =
            Issue.create(
                    "DuplicateIncludedIds",
                    "Duplicate ids across layouts combined with include tags",
                    "It's okay for two independent layouts to use the same ids. However, if "
                            + "layouts are combined with include tags, then the id's need to be unique "
                            + "within any chain of included layouts, or `Activity#findViewById()` can "
                            + "return an unexpected view.",
                    Category.CORRECTNESS,
                    5,
                    Severity.WARNING,
                    new Implementation(
                            DuplicateIdDetector.class, Scope.ALL_RESOURCES_SCOPE));

    private final Map<String, List<IdOccurrence>> layoutToIds = new HashMap<>();
    private final Map<String, List<String>> layoutToIncludes = new HashMap<>();
    private final Set<Location> reportedLocations = new HashSet<>();

    private static class IdOccurrence {
        final String id;
        final Location location;
        final String layoutName;

        IdOccurrence(String id, Location location, String layoutName) {
            this.id = id;
            this.location = location;
            this.layoutName = layoutName;
        }
    }

    @Override
    public boolean appliesTo(@NonNull Context context, @NonNull File file) {
        return true;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList("id", "layout");
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("include");
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
        layoutToIds.clear();
        layoutToIncludes.clear();
        reportedLocations.clear();
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        checkIncludes(context);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if ("include".equals(element.getTagName())) {
            String layoutValue = element.getAttributeNS("http://schemas.android.com/apk/res/android", "layout");
            if (layoutValue == null || layoutValue.isEmpty()) {
                layoutValue = element.getAttribute("layout");
            }
            if (layoutValue != null && !layoutValue.isEmpty()) {
                String includedLayout = getLayoutFromInclude(layoutValue);
                if (includedLayout != null) {
                    String layoutName = getLayoutName(context.file);
                    List<String> list = layoutToIncludes.computeIfAbsent(layoutName, k -> new ArrayList<>());
                    if (!list.contains(includedLayout)) {
                        list.add(includedLayout);
                    }
                }
            }
        }
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String name = attribute.getLocalName();
        if ("id".equals(name)) {
            String idValue = attribute.getValue();
            String cleanId = cleanId(idValue);
            if (cleanId != null && !cleanId.isEmpty()) {
                String layoutName = getLayoutName(context.file);
                List<IdOccurrence> list = layoutToIds.computeIfAbsent(layoutName, k -> new ArrayList<>());
                list.add(new IdOccurrence(cleanId, context.getLocation(attribute), layoutName));
            }
        } else if ("layout".equals(name)) {
            String elementTag = attribute.getOwnerElement().getTagName();
            if ("include".equals(elementTag)) {
                String layoutValue = attribute.getValue();
                String includedLayout = getLayoutFromInclude(layoutValue);
                if (includedLayout != null) {
                    String layoutName = getLayoutName(context.file);
                    List<String> list = layoutToIncludes.computeIfAbsent(layoutName, k -> new ArrayList<>());
                    if (!list.contains(includedLayout)) {
                        list.add(includedLayout);
                    }
                }
            }
        }
    }

    @Override
    public String toString() {
        return "DuplicateIdDetector";
    }

    @Override
    public int compareTo(Detector other) {
        return this.getClass().getName().compareTo(other.getClass().getName());
    }

    private void checkIncludes(Context context) {
        for (String rootLayout : layoutToIds.keySet()) {
            Set<String> visited = new HashSet<>();
            Map<String, IdOccurrence> seenIds = new HashMap<>();
            dfs(rootLayout, visited, seenIds, context);
        }
    }

    private void dfs(String currentLayout, Set<String> visited, Map<String, IdOccurrence> seenIds, Context context) {
        if (visited.contains(currentLayout)) {
            return;
        }
        visited.add(currentLayout);

        List<IdOccurrence> occurrences = layoutToIds.get(currentLayout);
        if (occurrences != null) {
            for (IdOccurrence occ : occurrences) {
                if (seenIds.containsKey(occ.id)) {
                    IdOccurrence first = seenIds.get(occ.id);
                    if (!reportedLocations.contains(occ.location)) {
                        reportedLocations.add(occ.location);
                        String message = String.format(
                                "Duplicate id %1$s, also defined in %2$s (included here)",
                                occ.id, first.layoutName);
                        context.report(ISSUE, occ.location, message);
                    }
                } else {
                    seenIds.put(occ.id, occ);
                }
            }
        }

        List<String> includes = layoutToIncludes.get(currentLayout);
        if (includes != null) {
            for (String child : includes) {
                dfs(child, visited, seenIds, context);
            }
        }
    }

    private String cleanId(String id) {
        if (id == null) return null;
        if (id.startsWith("@+id/")) {
            return id.substring("@+id/".length());
        } else if (id.startsWith("@id/")) {
            return id.substring("@id/".length());
        }
        return id;
    }

    private String getLayoutFromInclude(String layout) {
        if (layout == null) return null;
        if (layout.startsWith("@layout/")) {
            return layout.substring("@layout/".length());
        }
        return null;
    }

    private String getLayoutName(File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        if (dot != -1) {
            return name.substring(0, dot);
        }
        return name;
    }
}