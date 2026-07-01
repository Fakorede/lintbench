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
                            DuplicateIdDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private final Map<String, List<IdInfo>> layoutToIds = new HashMap<>();
    private final Map<String, List<IncludeInfo>> layoutToIncludes = new HashMap<>();

    private static class IdInfo {
        final String id;
        final Location location;
        final String layout;

        IdInfo(String id, Location location, String layout) {
            this.id = id;
            this.location = location;
            this.layout = layout;
        }
    }

    private static class IncludeInfo {
        final String includedLayout;
        final Location location;

        IncludeInfo(String includedLayout, Location location) {
            this.includedLayout = includedLayout;
            this.location = location;
        }
    }

    @Override
    public boolean appliesTo(@NonNull com.android.resources.ResourceFolderType folderType) {
        return folderType == com.android.resources.ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(com.android.SdkConstants.ATTR_ID);
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(com.android.SdkConstants.VIEW_INCLUDE);
    }

    @Override
    public void beforeCheckFile(@NonNull XmlContext context) {
        super.beforeCheckFile(context);
    }

    @Override
    public void afterCheckFile(@NonNull XmlContext context) {
        super.afterCheckFile(context);
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        super.beforeCheckRootProject(context);
        layoutToIds.clear();
        layoutToIncludes.clear();
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        super.afterCheckRootProject(context);
        checkDuplicateIds(context);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (com.android.SdkConstants.VIEW_INCLUDE.equals(element.getTagName())) {
            String layout = element.getAttributeNS(com.android.SdkConstants.ANDROID_URI, com.android.SdkConstants.ATTR_LAYOUT);
            if (layout == null || layout.isEmpty()) {
                layout = element.getAttribute(com.android.SdkConstants.ATTR_LAYOUT);
            }
            if (layout != null && !layout.isEmpty()) {
                int index = layout.indexOf('/');
                if (index != -1) {
                    layout = layout.substring(index + 1);
                }
                String currentLayout = getLayoutName(context);
                if (currentLayout != null) {
                    Location location = context.getNameLocation(element);
                    layoutToIncludes.computeIfAbsent(currentLayout, k -> new ArrayList<>())
                            .add(new IncludeInfo(layout, location));
                }
            }
        }
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String name = attribute.getLocalName();
        if (name == null) {
            name = attribute.getName();
        }
        if (com.android.SdkConstants.ATTR_ID.equals(name)) {
            String id = attribute.getValue();
            int index = id.indexOf('/');
            if (index != -1) {
                id = id.substring(index + 1);
            }
            String currentLayout = getLayoutName(context);
            if (currentLayout != null) {
                Location location = context.getLocation(attribute);
                layoutToIds.computeIfAbsent(currentLayout, k -> new ArrayList<>())
                        .add(new IdInfo(id, location, currentLayout));
            }
        }
    }

    private String getLayoutName(XmlContext context) {
        String name = context.file.getName();
        if (name.endsWith(".xml")) {
            return name.substring(0, name.length() - 4);
        }
        return null;
    }

    private void checkDuplicateIds(Context context) {
        Set<String> reportedKeys = new HashSet<>();
        for (String layout : layoutToIds.keySet()) {
            Set<String> visitedLayouts = new HashSet<>();
            Map<String, IdInfo> idsInChain = new HashMap<>();
            collectIds(layout, visitedLayouts, idsInChain, reportedKeys, context);
        }
    }

    private void collectIds(
            String layout,
            Set<String> visitedLayouts,
            Map<String, IdInfo> idsInChain,
            Set<String> reportedKeys,
            Context context) {
        if (visitedLayouts.contains(layout)) {
            return;
        }
        visitedLayouts.add(layout);

        List<IdInfo> ids = layoutToIds.get(layout);
        if (ids != null) {
            for (IdInfo idInfo : ids) {
                if (idsInChain.containsKey(idInfo.id)) {
                    IdInfo existing = idsInChain.get(idInfo.id);
                    String message = String.format(
                            "Duplicate id `%s` in inclusion chain (defined in `%s` and `%s`)",
                            idInfo.id, existing.layout, idInfo.layout);
                    String key = idInfo.id + "@" + getLocationKey(idInfo.location);
                    if (reportedKeys.add(key)) {
                        context.report(ISSUE, idInfo.location, message);
                    }
                } else {
                    idsInChain.put(idInfo.id, idInfo);
                }
            }
        }

        List<IncludeInfo> includes = layoutToIncludes.get(layout);
        if (includes != null) {
            for (IncludeInfo include : includes) {
                collectIds(include.includedLayout, visitedLayouts, idsInChain, reportedKeys, context);
            }
        }

        visitedLayouts.remove(layout);
    }

    private String getLocationKey(Location location) {
        if (location == null) {
            return "";
        }
        com.android.tools.lint.detector.api.Position start = location.getStart();
        int offset = start != null ? start.getOffset() : -1;
        File file = location.getFile();
        String path = file != null ? file.getPath() : "";
        return path + ":" + offset;
    }

    @Override
    public String toString() {
        return "DuplicateIdDetector";
    }

    public int compareTo(@NonNull Detector other) {
        return this.getClass().getName().compareTo(other.getClass().getName());
    }
}