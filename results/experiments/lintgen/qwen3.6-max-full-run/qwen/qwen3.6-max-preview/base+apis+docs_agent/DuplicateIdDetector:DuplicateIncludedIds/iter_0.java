package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SdkUtils;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class DuplicateIdDetector extends Detector implements XmlScanner {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_ID = "id";
    private static final String ATTR_LAYOUT = "layout";
    private static final String TAG_INCLUDE = "include";

    public static final Issue ISSUE = Issue.create(
            "DuplicateIncludedIds",
            "Duplicate ids across layouts combined with include tags",
            "It's okay for two independent layouts to use the same ids. However, if layouts are combined with include tags, then the id's need to be unique within any chain of included layouts, or `Activity#findViewById()` can return an unexpected view.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(DuplicateIdDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private final Map<String, Set<String>> layoutIds = new HashMap<>();
    private final Map<String, Map<String, Location>> layoutIdLocations = new HashMap<>();
    private final Map<String, Set<String>> layoutIncludes = new HashMap<>();

    @Override
    public void beforeCheckEachProject(@NotNull Context context) {
        layoutIds.clear();
        layoutIdLocations.clear();
        layoutIncludes.clear();
    }

    @Override
    public void visitElement(@NotNull XmlContext context, @NotNull Element element) {
        String layoutName = context.getResourceName();
        if (layoutName == null) {
            return;
        }

        Attr idAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_ID);
        if (idAttr != null) {
            String idValue = idAttr.getValue();
            String idName = SdkUtils.stripIdPrefix(idValue);
            if (idName != null && !idName.isEmpty()) {
                layoutIds.computeIfAbsent(layoutName, k -> new HashSet<>()).add(idName);
                layoutIdLocations.computeIfAbsent(layoutName, k -> new HashMap<>())
                        .putIfAbsent(idName, context.getLocation(idAttr));
            }
        }

        if (TAG_INCLUDE.equals(element.getTagName())) {
            Attr layoutAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_LAYOUT);
            if (layoutAttr != null) {
                String value = layoutAttr.getValue();
                String includedName = null;
                if (value.startsWith("@layout/")) {
                    includedName = value.substring("@layout/".length());
                } else if (value.startsWith("@+layout/")) {
                    includedName = value.substring("@+layout/".length());
                }
                if (includedName != null && !includedName.isEmpty()) {
                    layoutIncludes.computeIfAbsent(layoutName, k -> new HashSet<>()).add(includedName);
                }
            }
        }
    }

    @Override
    public void afterCheckEachProject(@NotNull Context context) {
        Set<String> reported = new HashSet<>();

        for (String rootLayout : layoutIds.keySet()) {
            Map<String, String> idToLayout = new HashMap<>();
            Map<String, Location> idToLocation = new HashMap<>();
            Set<String> visited = new HashSet<>();
            Deque<String> stack = new ArrayDeque<>();

            stack.push(rootLayout);
            visited.add(rootLayout);

            while (!stack.isEmpty()) {
                String current = stack.pop();

                Set<String> ids = layoutIds.get(current);
                if (ids != null) {
                    for (String id : ids) {
                        if (idToLayout.containsKey(id)) {
                            String otherLayout = idToLayout.get(id);
                            String key = id + ":" + otherLayout + ":" + current;
                            if (reported.add(key)) {
                                Location firstLoc = idToLocation.get(id);
                                Location secondLoc = layoutIdLocations.get(current).get(id);
                                if (secondLoc != null && firstLoc != null) {
                                    secondLoc.setSecondary(firstLoc);
                                    String msg = String.format(
                                            "Duplicate id `%s`, already defined in layout `%s`", id, otherLayout);
                                    context.report(ISSUE, secondLoc, msg);
                                }
                            }
                        } else {
                            idToLayout.put(id, current);
                            Map<String, Location> locs = layoutIdLocations.get(current);
                            if (locs != null) {
                                idToLocation.put(id, locs.get(id));
                            }
                        }
                    }
                }

                Set<String> includes = layoutIncludes.get(current);
                if (includes != null) {
                    for (String inc : includes) {
                        if (!visited.contains(inc)) {
                            visited.add(inc);
                            stack.push(inc);
                        }
                    }
                }
            }
        }
    }
}