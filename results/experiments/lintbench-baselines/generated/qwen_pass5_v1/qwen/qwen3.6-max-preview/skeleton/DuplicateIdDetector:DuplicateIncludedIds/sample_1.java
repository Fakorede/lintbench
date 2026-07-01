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
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
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
                    "It's okay for two independent layouts to use the same ids. However, if " +
                    "layouts are combined with include tags, then the id's need to be unique " +
                    "within any chain of included layouts, or `Activity#findViewById()` can " +
                    "return an unexpected view.",
                    Category.CORRECTNESS,
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    private Map<String, Map<String, Location>> layoutIdLocations;
    private Map<String, Set<String>> layoutIncludes;
    private Set<String> reported;

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
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        layoutIdLocations = new HashMap<>();
        layoutIncludes = new HashMap<>();
        reported = new HashSet<>();
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        for (String layout : layoutIdLocations.keySet()) {
            Map<String, String> seenIds = new HashMap<>();
            Set<String> visited = new HashSet<>();
            collectAndCheck(layout, seenIds, visited, context);
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String layoutAttr = element.getAttributeNS(ANDROID_URI, "layout");
        if (layoutAttr != null && layoutAttr.startsWith("@layout/")) {
            String included = layoutAttr.substring("@layout/".length());
            String layout = getBaseName(context);
            layoutIncludes.computeIfAbsent(layout, k -> new HashSet<>()).add(included);
        }
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String ns = attribute.getNamespaceURI();
        if (ns == null || !ns.equals(ANDROID_URI)) {
            return;
        }
        String value = attribute.getValue();
        if (value.startsWith("@+id/") || value.startsWith("@id/")) {
            String id = value.substring(value.indexOf('/') + 1);
            String layout = getBaseName(context);
            layoutIdLocations.computeIfAbsent(layout, k -> new HashMap<>())
                    .put(id, Location.create(context.file, attribute));
        }
    }

    private String getBaseName(XmlContext context) {
        String name = context.file.getName();
        if (name.endsWith(".xml")) {
            return name.substring(0, name.length() - 4);
        }
        return name;
    }

    private void collectAndCheck(String layout, Map<String, String> seenIds, Set<String> visited, Context context) {
        if (visited.contains(layout)) {
            return;
        }
        visited.add(layout);

        Map<String, Location> ids = layoutIdLocations.get(layout);
        if (ids != null) {
            for (Map.Entry<String, Location> entry : ids.entrySet()) {
                String id = entry.getKey();
                Location loc = entry.getValue();
                if (seenIds.containsKey(id)) {
                    String first = seenIds.get(id);
                    String key = layout + ":" + id;
                    if (!reported.contains(key)) {
                        reported.add(key);
                        context.report(ISSUE, loc, String.format("Duplicate id `%s`, already defined in layout `%s`", id, first));
                    }
                } else {
                    seenIds.put(id, layout);
                }
            }
        }

        Set<String> includes = layoutIncludes.get(layout);
        if (includes != null) {
            for (String inc : includes) {
                collectAndCheck(inc, seenIds, visited, context);
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