package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
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

public class DuplicateIdDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "DuplicateIncludedIds",
            "Duplicate ids across layouts combined with include tags",
            "It's okay for two independent layouts to use the same ids. However, if " +
            "layouts are combined with include tags, then the id's need to be unique " +
            "within any chain of included layouts, or `Activity#findViewById()` can " +
            "return an unexpected view.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(
                    DuplicateIdDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    private final Map<File, LayoutInfo> layoutMap = new HashMap<>();
    private final Set<String> reportedConflicts = new HashSet<>();

    private static class LayoutInfo {
        final File file;
        final String layoutName;
        final Map<String, Location> idToLocation = new HashMap<>();
        final List<IncludeTarget> includes = new ArrayList<>();

        LayoutInfo(File file, String layoutName) {
            this.file = file;
            this.layoutName = layoutName;
        }
    }

    private static class IncludeTarget {
        final String targetLayoutName;
        final Location location;

        IncludeTarget(String targetLayoutName, Location location) {
            this.targetLayoutName = targetLayoutName;
            this.location = location;
        }
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(SdkConstants.ATTR_ID);
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        String idValue = attribute.getValue();
        String id = stripIdPrefix(idValue);
        if (id.isEmpty()) {
            return;
        }
        File file = context.file;
        LayoutInfo layoutInfo = getOrCreateLayoutInfo(context, file);
        Location location = context.getLocation(attribute);
        layoutInfo.idToLocation.put(id, location);
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(SdkConstants.VIEW_INCLUDE);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String layoutAttr = element.getAttribute(SdkConstants.ATTR_LAYOUT);
        if (layoutAttr == null || layoutAttr.isEmpty()) {
            return;
        }
        String targetLayout = stripLayoutPrefix(layoutAttr);
        if (targetLayout.isEmpty()) {
            return;
        }
        File file = context.file;
        LayoutInfo layoutInfo = getOrCreateLayoutInfo(context, file);
        Location location = context.getLocation(element);
        layoutInfo.includes.add(new IncludeTarget(targetLayout, location));
    }

    private LayoutInfo getOrCreateLayoutInfo(XmlContext context, File file) {
        LayoutInfo info = layoutMap.get(file);
        if (info == null) {
            String layoutName = file.getName();
            if (layoutName.endsWith(".xml")) {
                layoutName = layoutName.substring(0, layoutName.length() - 4);
            }
            info = new LayoutInfo(file, layoutName);
            layoutMap.put(file, info);
        }
        return info;
    }

    private String stripIdPrefix(String id) {
        if (id == null) return "";
        if (id.startsWith("@+id/")) {
            return id.substring(5);
        } else if (id.startsWith("@id/")) {
            return id.substring(4);
        }
        return id;
    }

    private String stripLayoutPrefix(String layout) {
        if (layout == null) return "";
        if (layout.startsWith("@layout/")) {
            return layout.substring(8);
        }
        return layout;
    }

    @Override
    public void afterCheckEachProject(Context context) {
        Map<String, List<LayoutInfo>> layoutsByName = new HashMap<>();
        for (LayoutInfo info : layoutMap.values()) {
            layoutsByName.computeIfAbsent(info.layoutName, k -> new ArrayList<>()).add(info);
        }

        for (LayoutInfo root : layoutMap.values()) {
            List<Set<LayoutInfo>> hierarchies = getPossibleHierarchies(root, new HashSet<>(), layoutsByName, 0);
            for (Set<LayoutInfo> hierarchy : hierarchies) {
                Map<String, LayoutInfo> idToLayout = new HashMap<>();
                for (LayoutInfo layout : hierarchy) {
                    for (String id : layout.idToLocation.keySet()) {
                        if (idToLayout.containsKey(id)) {
                            LayoutInfo otherLayout = idToLayout.get(id);
                            if (otherLayout != layout) {
                                File f1 = layout.file;
                                File f2 = otherLayout.file;
                                if (shouldReport(f1, f2, id)) {
                                    Location loc1 = layout.idToLocation.get(id);
                                    Location loc2 = otherLayout.idToLocation.get(id);

                                    String message = String.format(
                                        "Duplicate id `%s` in included layouts `%s` and `%s`",
                                        id, layout.layoutName, otherLayout.layoutName
                                    );

                                    Location mainLoc = loc1;
                                    Location secLoc = loc2;
                                    secLoc.setMessage("Also defined here");
                                    mainLoc.setSecondary(secLoc);

                                    context.report(ISSUE, mainLoc, message);
                                }
                            }
                        } else {
                            idToLayout.put(id, layout);
                        }
                    }
                }
            }
        }

        layoutMap.clear();
        reportedConflicts.clear();
    }

    private List<Set<LayoutInfo>> getPossibleHierarchies(
            LayoutInfo current,
            Set<String> visitedLayoutNames,
            Map<String, List<LayoutInfo>> layoutsByName,
            int depth) {

        List<Set<LayoutInfo>> results = new ArrayList<>();
        if (depth > 10 || visitedLayoutNames.contains(current.layoutName)) {
            Set<LayoutInfo> self = new HashSet<>();
            self.add(current);
            results.add(self);
            return results;
        }

        Set<String> newVisited = new HashSet<>(visitedLayoutNames);
        newVisited.add(current.layoutName);

        List<List<Set<LayoutInfo>>> childrenHierarchies = new ArrayList<>();
        for (IncludeTarget include : current.includes) {
            List<LayoutInfo> targets = layoutsByName.get(include.targetLayoutName);
            if (targets == null || targets.isEmpty()) {
                continue;
            }
            List<Set<LayoutInfo>> combinedTargetsHierarchies = new ArrayList<>();
            for (LayoutInfo target : targets) {
                combinedTargetsHierarchies.addAll(
                    getPossibleHierarchies(target, newVisited, layoutsByName, depth + 1)
                );
            }
            if (!combinedTargetsHierarchies.isEmpty()) {
                childrenHierarchies.add(combinedTargetsHierarchies);
            }
        }

        List<Set<LayoutInfo>> product = new ArrayList<>();
        product.add(new HashSet<>());

        for (List<Set<LayoutInfo>> childHierarchies : childrenHierarchies) {
            List<Set<LayoutInfo>> nextProduct = new ArrayList<>();
            for (Set<LayoutInfo> prodSet : product) {
                for (Set<LayoutInfo> childSet : childHierarchies) {
                    Set<LayoutInfo> combined = new HashSet<>(prodSet);
                    combined.addAll(childSet);
                    nextProduct.add(combined);
                }
            }
            product = nextProduct;
            if (product.size() > 50) {
                product = product.subList(0, 50);
                break;
            }
        }

        for (Set<LayoutInfo> prodSet : product) {
            prodSet.add(current);
            results.add(prodSet);
        }

        return results;
    }

    private boolean shouldReport(File f1, File f2, String id) {
        String p1 = f1.getAbsolutePath();
        String p2 = f2.getAbsolutePath();
        String key = p1.compareTo(p2) < 0 ? p1 + ":::" + p2 + ":::" + id : p2 + ":::" + p1 + ":::" + id;
        return reportedConflicts.add(key);
    }
}