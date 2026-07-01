package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.*;
import org.w3c.dom.*;
import org.jetbrains.annotations.NonNull;
import java.util.*;

public class DuplicateIdDetector extends ResourceXmlDetector {

    public static final Issue ISSUE = Issue.create(
            "DuplicateIncludedIds",
            "Duplicate ids across layouts combined with include tags",
            "It's okay for two independent layouts to use the same ids. " +
            "However, if layouts are combined with include tags, then the id's need to be unique " +
            "within any chain of included layouts, or `Activity#findViewById()` can return an unexpected view.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(DuplicateIdDetector.class, Scope.ALL_RESOURCE_FILES_SCOPE)
    );

    private static final String ATTR_ID = "id";
    private static final String ATTR_LAYOUT = "layout";
    private static final String TAG_INCLUDE = "include";
    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";

    // layout name -> (id name -> location)
    private final Map<String, Map<String, Location>> layoutIdLocations = new HashMap<>();
    // layout name -> list of included layouts with include tag locations
    private final Map<String, List<IncludeInfo>> layoutIncludes = new HashMap<>();
    private final Set<String> reportedDuplicates = new HashSet<>();

    private static class IncludeInfo {
        final String includedLayout;
        final Location location;
        IncludeInfo(String l, Location loc) {
            includedLayout = l;
            location = loc;
        }
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_ID);
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_INCLUDE);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        if (!ANDROID_NS.equals(attribute.getNamespaceURI())) {
            return;
        }
        String value = attribute.getValue();
        if (value.startsWith("@+id/")) {
            String id = value.substring(5);
            String layout = getLayoutName(context);
            Location loc = context.getLocation(attribute);
            layoutIdLocations.computeIfAbsent(layout, k -> new HashMap<>()).put(id, loc);
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String layoutValue = element.getAttribute(ATTR_LAYOUT);
        if (layoutValue != null && !layoutValue.isEmpty() && layoutValue.startsWith("@layout/")) {
            String included = layoutValue.substring(8);
            String parent = getLayoutName(context);
            Location loc = context.getLocation(element);
            layoutIncludes.computeIfAbsent(parent, k -> new ArrayList<>()).add(new IncludeInfo(included, loc));
        }
    }

    @Override
    public void afterCheckProject(@NonNull Context context) {
        for (String layout : layoutIncludes.keySet()) {
            Map<String, List<Location>> allIds = new HashMap<>();
            collectIds(layout, new HashSet<>(), allIds);
            for (Map.Entry<String, List<Location>> entry : allIds.entrySet()) {
                List<Location> locs = entry.getValue();
                if (locs.size() > 1) {
                    String id = entry.getKey();
                    String key = layout + ":" + id;
                    if (reportedDuplicates.add(key)) {
                        Location primary = locs.get(0);
                        Location secondary = locs.get(1);
                        String primaryLayout = primary.getFile().getName().replaceFirst("\\.xml$", "");
                        String msg = String.format("Duplicate id `@+id/%s`, already defined in layout `%s`", id, primaryLayout);
                        context.report(ISSUE, secondary, msg);
                    }
                }
            }
        }
    }

    private void collectIds(String layout, Set<String> visited, Map<String, List<Location>> allIds) {
        if (!visited.add(layout)) {
            return;
        }
        Map<String, Location> ownIds = layoutIdLocations.get(layout);
        if (ownIds != null) {
            for (Map.Entry<String, Location> e : ownIds.entrySet()) {
                allIds.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).add(e.getValue());
            }
        }
        List<IncludeInfo> includes = layoutIncludes.get(layout);
        if (includes != null) {
            for (IncludeInfo info : includes) {
                collectIds(info.includedLayout, visited, allIds);
            }
        }
    }

    private static String getLayoutName(XmlContext context) {
        String name = context.file.getName();
        int dot = name.indexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}