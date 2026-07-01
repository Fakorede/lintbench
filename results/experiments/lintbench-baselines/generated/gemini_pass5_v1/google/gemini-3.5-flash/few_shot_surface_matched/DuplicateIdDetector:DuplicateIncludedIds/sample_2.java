package com.android.tools.lint.checks;

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
                            DuplicateIdDetector.class,
                            Scope.RESOURCE_FILE_SCOPE));

    private final Map<String, Set<String>> layoutToIds = new HashMap<>();
    private final Map<String, List<IncludeInfo>> layoutToIncludeInfos = new HashMap<>();
    private final Set<String> reportedWarnings = new HashSet<>();

    private static class IncludeInfo {
        final String includedLayout;
        final Location location;
        final String xmlFile;

        IncludeInfo(String includedLayout, Location location, String xmlFile) {
            this.includedLayout = includedLayout;
            this.location = location;
            this.xmlFile = xmlFile;
        }
    }

    @Override
    public boolean appliesTo(com.android.resources.ResourceFolderType folderType) {
        return folderType == com.android.resources.ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return java.util.Arrays.asList("id", "layout");
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("include");
    }

    @Override
    public void beforeCheckFile(XmlContext context) {
    }

    @Override
    public void afterCheckFile(XmlContext context) {
    }

    @Override
    public void beforeCheckRootProject(Context context) {
        layoutToIds.clear();
        layoutToIncludeInfos.clear();
        reportedWarnings.clear();
    }

    @Override
    public void afterCheckRootProject(Context context) {
        for (String layoutName : layoutToIds.keySet()) {
            checkLayout(context, layoutName);
        }
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        String name = attribute.getLocalName();
        if (name == null) {
            name = attribute.getName();
        }
        if ("id".equals(name)) {
            String idValue = attribute.getValue();
            String id = extractId(idValue);
            if (id != null) {
                String layoutName = getLayoutName(context);
                layoutToIds.computeIfAbsent(layoutName, k -> new HashSet<>()).add(id);
            }
        } else if ("layout".equals(name)) {
            Element element = attribute.getOwnerElement();
            if ("include".equals(element.getTagName())) {
                String layoutValue = attribute.getValue();
                String includedLayout = extractLayoutName(layoutValue);
                if (includedLayout != null) {
                    String layoutName = getLayoutName(context);
                    IncludeInfo info = new IncludeInfo(
                            includedLayout,
                            context.getLocation(attribute),
                            context.file.getName());
                    layoutToIncludeInfos.computeIfAbsent(layoutName, k -> new ArrayList<>()).add(info);
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

    private String extractId(String value) {
        if (value == null) return null;
        int index = value.indexOf('/');
        if (index != -1) {
            return value.substring(index + 1);
        }
        return null;
    }

    private String extractLayoutName(String value) {
        if (value == null) return null;
        int index = value.indexOf('/');
        if (index != -1) {
            return value.substring(index + 1);
        }
        return null;
    }

    private String getLayoutName(XmlContext context) {
        String name = context.file.getName();
        if (name.endsWith(".xml")) {
            return name.substring(0, name.length() - 4);
        }
        return name;
    }

    private void checkLayout(Context context, String layoutName) {
        Set<String> visitedLayouts = new HashSet<>();
        Map<String, String> idToOriginLayout = new HashMap<>();
        List<IncludeInfo> path = new ArrayList<>();
        dfs(context, layoutName, visitedLayouts, idToOriginLayout, path);
    }

    private void dfs(Context context, String layout, Set<String> visited, Map<String, String> idToOrigin, List<IncludeInfo> path) {
        if (visited.contains(layout)) {
            return;
        }
        visited.add(layout);

        Set<String> ids = layoutToIds.get(layout);
        if (ids != null) {
            for (String id : ids) {
                if (idToOrigin.containsKey(id)) {
                    String originalLayout = idToOrigin.get(id);
                    if (!path.isEmpty()) {
                        IncludeInfo lastInclude = path.get(path.size() - 1);
                        String warningKey = lastInclude.location.toString() + ":" + id;
                        if (!reportedWarnings.contains(warningKey)) {
                            reportedWarnings.add(warningKey);
                            String message = String.format(
                                    "Duplicate id `%s` in the inclusion chain: also defined in `%s` (included here) and `%s`",
                                    id, layout, originalLayout);
                            context.report(ISSUE, lastInclude.location, message);
                        }
                    }
                } else {
                    idToOrigin.put(id, layout);
                }
            }
        }

        List<IncludeInfo> includes = layoutToIncludeInfos.get(layout);
        if (includes != null) {
            for (IncludeInfo include : includes) {
                path.add(include);
                dfs(context, include.includedLayout, visited, idToOrigin, path);
                path.remove(path.size() - 1);
            }
        }

        visited.remove(layout);
    }
}