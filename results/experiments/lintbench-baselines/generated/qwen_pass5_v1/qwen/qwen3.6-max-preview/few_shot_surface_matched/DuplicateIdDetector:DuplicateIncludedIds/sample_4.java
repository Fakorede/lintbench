package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
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
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class DuplicateIdDetector extends LayoutDetector implements XmlScanner {

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

    private static final String ATTR_ID = "id";
    private static final String TAG_INCLUDE = "include";
    private static final String ATTR_LAYOUT = "layout";
    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String DOT_XML = ".xml";
    private static final String ID_PREFIX_NEW = "@+id/";
    private static final String ID_PREFIX_REF = "@id/";
    private static final String LAYOUT_PREFIX = "@layout/";

    private Map<String, Map<String, Location>> layoutToIds;
    private Map<String, Set<String>> layoutToIncludes;
    private String currentLayout;
    private Map<String, Location> currentIds;
    private Set<String> currentIncludes;

    @Override
    public boolean appliesTo(@NonNull XmlContext context, @NonNull File file) {
        String parent = file.getParentFile() != null ? file.getParentFile().getName() : "";
        return parent.startsWith("layout") && file.getName().endsWith(DOT_XML);
    }

    @Nullable
    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_ID);
    }

    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_INCLUDE);
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        layoutToIds = new HashMap<>();
        layoutToIncludes = new HashMap<>();
    }

    @Override
    public void beforeCheckFile(@NonNull XmlContext context) {
        currentLayout = context.file.getName();
        currentIds = new HashMap<>();
        currentIncludes = new HashSet<>();
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String value = attribute.getValue();
        if (value.startsWith(ID_PREFIX_NEW)) {
            String id = value.substring(ID_PREFIX_NEW.length());
            currentIds.put(id, context.getLocation(attribute));
        } else if (value.startsWith(ID_PREFIX_REF)) {
            String id = value.substring(ID_PREFIX_REF.length());
            currentIds.put(id, context.getLocation(attribute));
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String layoutAttr = element.getAttributeNS(ANDROID_URI, ATTR_LAYOUT);
        if (layoutAttr != null && layoutAttr.startsWith(LAYOUT_PREFIX)) {
            String included = layoutAttr.substring(LAYOUT_PREFIX.length()) + DOT_XML;
            currentIncludes.add(included);
        }
    }

    @Override
    public void afterCheckFile(@NonNull XmlContext context) {
        if (!currentIds.isEmpty()) {
            layoutToIds.put(currentLayout, currentIds);
        }
        if (!currentIncludes.isEmpty()) {
            layoutToIncludes.put(currentLayout, currentIncludes);
        }
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        for (Map.Entry<String, Map<String, Location>> entry : layoutToIds.entrySet()) {
            String rootLayout = entry.getKey();
            Map<String, Location> rootIds = entry.getValue();
            Set<String> visited = new HashSet<>();
            Set<String> allIncludedIds = new HashSet<>();
            Map<String, Location> includedIdLocations = new HashMap<>();

            collectIncludedIds(rootLayout, visited, allIncludedIds, includedIdLocations);

            for (String id : rootIds.keySet()) {
                if (allIncludedIds.contains(id)) {
                    Location location = rootIds.get(id);
                    Location secondary = includedIdLocations.get(id);
                    if (secondary != null) {
                        location.setSecondary(secondary);
                    }
                    context.report(ISSUE, location,
                            "Duplicate id `" + id + "`, already defined in an included layout");
                }
            }
        }
        layoutToIds = null;
        layoutToIncludes = null;
    }

    private void collectIncludedIds(String layout, Set<String> visited, Set<String> allIds,
                                    Map<String, Location> locations) {
        if (visited.contains(layout)) {
            return;
        }
        visited.add(layout);

        Set<String> includes = layoutToIncludes.get(layout);
        if (includes != null) {
            for (String inc : includes) {
                Map<String, Location> incIds = layoutToIds.get(inc);
                if (incIds != null) {
                    for (Map.Entry<String, Location> e : incIds.entrySet()) {
                        allIds.add(e.getKey());
                        locations.put(e.getKey(), e.getValue());
                    }
                }
                collectIncludedIds(inc, visited, allIds, locations);
            }
        }
    }

    @NonNull
    @Override
    public String toString() {
        return "DuplicateIdDetector";
    }

    @Override
    public int compareTo(@NonNull Detector other) {
        return toString().compareTo(other.toString());
    }
}