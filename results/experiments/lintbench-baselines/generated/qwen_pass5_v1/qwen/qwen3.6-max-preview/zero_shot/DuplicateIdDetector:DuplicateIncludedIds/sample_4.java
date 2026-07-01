package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class DuplicateIdDetector extends LayoutDetector {

    public static final Issue ISSUE = Issue.create(
            "DuplicateIncludedIds",
            "Duplicate ids across layouts combined with include tags",
            "It's okay for two independent layouts to use the same ids. " +
            "However, if layouts are combined with include tags, then the id's need to be unique " +
            "within any chain of included layouts, or `Activity#findViewById()` can return an unexpected view.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(DuplicateIdDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private final Map<String, Set<String>> layoutIds = new HashMap<>();
    private final Map<String, Set<String>> layoutIncludes = new HashMap<>();
    private final Map<String, Location> idLocations = new HashMap<>();
    private final Set<String> reported = new HashSet<>();

    @Override
    public void beforeCheckProject(Context context) {
        layoutIds.clear();
        layoutIncludes.clear();
        idLocations.clear();
        reported.clear();
    }

    @Override
    public Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String layoutName = getLayoutName(context);
        if (layoutName == null) return;

        Attr idAttr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ID);
        if (idAttr != null) {
            String idValue = idAttr.getValue();
            if (idValue != null && !idValue.startsWith("@android:")) {
                String idName = stripIdPrefix(idValue);
                if (idName != null && !idName.isEmpty()) {
                    layoutIds.computeIfAbsent(layoutName, k -> new HashSet<>()).add(idName);
                    idLocations.put(layoutName + ":" + idName, context.getLocation(idAttr));
                }
            }
        }

        if (SdkConstants.TAG_INCLUDE.equals(element.getTagName())) {
            Attr layoutAttr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT);
            if (layoutAttr != null) {
                String layoutValue = layoutAttr.getValue();
                if (layoutValue != null && layoutValue.startsWith("@layout/")) {
                    String includedName = layoutValue.substring("@layout/".length());
                    layoutIncludes.computeIfAbsent(layoutName, k -> new HashSet<>()).add(includedName);
                }
            }
        }
    }

    @Override
    public void afterCheckProject(Context context) {
        Set<String> allLayouts = new HashSet<>(layoutIds.keySet());
        allLayouts.addAll(layoutIncludes.keySet());

        for (String rootLayout : allLayouts) {
            Map<String, String> idToSource = new HashMap<>();
            Set<String> visited = new HashSet<>();
            checkForDuplicates(context, rootLayout, rootLayout, idToSource, visited);
        }
    }

    private void checkForDuplicates(Context context, String rootLayout, String currentLayout,
                                    Map<String, String> idToSource, Set<String> visited) {
        if (visited.contains(currentLayout)) return;
        visited.add(currentLayout);

        Set<String> ids = layoutIds.get(currentLayout);
        if (ids != null) {
            for (String id : ids) {
                if (idToSource.containsKey(id)) {
                    String otherLayout = idToSource.get(id);
                    if (!otherLayout.equals(currentLayout)) {
                        String reportKey = id + ":" + otherLayout + ":" + currentLayout;
                        if (reported.add(reportKey)) {
                            Location location = idLocations.get(currentLayout + ":" + id);
                            if (location != null) {
                                String message = String.format(
                                        "Duplicate id `%s`, already defined in layout `%s` which is included in `%s`",
                                        id, otherLayout, rootLayout);
                                context.report(ISSUE, location, message);
                            }
                        }
                    }
                } else {
                    idToSource.put(id, currentLayout);
                }
            }
        }

        Set<String> includes = layoutIncludes.get(currentLayout);
        if (includes != null) {
            for (String included : includes) {
                checkForDuplicates(context, rootLayout, included, idToSource, visited);
            }
        }
    }

    private static String getLayoutName(XmlContext context) {
        File file = context.file;
        if (file == null) return null;
        String name = file.getName();
        int dot = name.indexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static String stripIdPrefix(String id) {
        if (id == null) return null;
        if (id.startsWith("@+id/")) return id.substring(5);
        if (id.startsWith("@id/")) return id.substring(4);
        return id;
    }
}