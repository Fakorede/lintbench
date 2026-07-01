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
import org.jetbrains.annotations.NonNull;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DuplicateIdDetector extends LayoutDetector {

    public static final Issue ISSUE = Issue.create(
            "DuplicateIncludedIds",
            "Duplicate ids across layouts combined with include tags",
            "It's okay for two independent layouts to use the same ids. However, if " +
            "layouts are combined with include tags, then the id's need to be unique " +
            "within any chain of included layouts, or `Activity#findViewById()` can " +
            "return an unexpected view.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(
                    DuplicateIdDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    private final Map<String, List<LayoutInfo>> mLayouts = new HashMap<>();
    private final Set<String> mReported = new HashSet<>();

    @Override
    public void beforeCheckEachProject(@NonNull Context context) {
        mLayouts.clear();
        mReported.clear();
    }

    @Override
    public Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String fileName = context.file.getName();
        if (!fileName.endsWith(".xml")) {
            return;
        }
        String layoutName = fileName.substring(0, fileName.length() - 4);

        List<LayoutInfo> infos = mLayouts.get(layoutName);
        if (infos == null) {
            infos = new ArrayList<>();
            mLayouts.put(layoutName, infos);
        }

        LayoutInfo layoutInfo = null;
        for (LayoutInfo info : infos) {
            if (info.file.equals(context.file)) {
                layoutInfo = info;
                break;
            }
        }
        if (layoutInfo == null) {
            layoutInfo = new LayoutInfo(layoutName, context.file);
            infos.add(layoutInfo);
        }

        Attr idNode = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ID);
        if (idNode != null) {
            String idAttr = idNode.getValue();
            if (idAttr != null && !idAttr.isEmpty()) {
                String id = stripIdPrefix(idAttr);
                if (!id.isEmpty()) {
                    Location location = context.getValueLocation(idNode);
                    layoutInfo.idToLocation.put(id, location);
                }
            }
        }

        if (element.getTagName().equals(SdkConstants.VIEW_INCLUDE)) {
            Attr layoutNode = element.getAttributeNode(SdkConstants.ATTR_LAYOUT);
            if (layoutNode != null) {
                String layoutAttr = layoutNode.getValue();
                if (layoutAttr != null && !layoutAttr.isEmpty()) {
                    String includedLayout = stripLayoutPrefix(layoutAttr);
                    if (!includedLayout.isEmpty()) {
                        Location location = context.getValueLocation(layoutNode);
                        layoutInfo.includes.add(new IncludeInfo(includedLayout, location));
                    }
                }
            }
        }
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        for (List<LayoutInfo> infoList : mLayouts.values()) {
            for (LayoutInfo rootLayout : infoList) {
                Map<String, String> idToSourceLayout = new HashMap<>();
                Set<String> visitedInPath = new HashSet<>();
                checkInclusions(rootLayout, idToSourceLayout, visitedInPath, context);
            }
        }
    }

    private void checkInclusions(
            LayoutInfo currentLayout,
            Map<String, String> idToSourceLayout,
            Set<String> visitedInPath,
            Context context) {

        if (visitedInPath.contains(currentLayout.name)) {
            return;
        }
        visitedInPath.add(currentLayout.name);

        for (Map.Entry<String, Location> entry : currentLayout.idToLocation.entrySet()) {
            String id = entry.getKey();
            Location location = entry.getValue();

            if (idToSourceLayout.containsKey(id)) {
                String firstSource = idToSourceLayout.get(id);
                if (!firstSource.equals(currentLayout.name)) {
                    String errorKey = location.getFile().getPath() + ":" + 
                                      (location.getStart() != null ? location.getStart().getLine() : 0) + ":" + 
                                      id;
                    if (!mReported.contains(errorKey)) {
                        mReported.add(errorKey);

                        String message = String.format(
                                "Duplicate id `%s` in layout `%s` and included layout `%s`",
                                SdkConstants.ID_PREFIX + id,
                                firstSource,
                                currentLayout.name
                        );

                        List<LayoutInfo> firstLayoutInfos = mLayouts.get(firstSource);
                        if (firstLayoutInfos != null) {
                            for (LayoutInfo firstLayoutInfo : firstLayoutInfos) {
                                Location firstLoc = firstLayoutInfo.idToLocation.get(id);
                                if (firstLoc != null) {
                                    location.setSecondary(firstLoc);
                                    location.setMessage("Original declaration here");
                                    break;
                                }
                            }
                        }
                        context.report(ISSUE, location, message);
                    }
                }
            } else {
                idToSourceLayout.put(id, currentLayout.name);
            }
        }

        for (IncludeInfo include : currentLayout.includes) {
            List<LayoutInfo> includedLayoutInfos = mLayouts.get(include.targetLayout);
            if (includedLayoutInfos != null) {
                for (LayoutInfo includedLayoutInfo : includedLayoutInfos) {
                    Map<String, String> copyMap = new HashMap<>(idToSourceLayout);
                    checkInclusions(includedLayoutInfo, copyMap, visitedInPath, context);
                }
            }
        }

        visitedInPath.remove(currentLayout.name);
    }

    private static String stripIdPrefix(String id) {
        if (id.startsWith(SdkConstants.NEW_ID_PREFIX)) {
            return id.substring(SdkConstants.NEW_ID_PREFIX.length());
        } else if (id.startsWith(SdkConstants.ID_PREFIX)) {
            return id.substring(SdkConstants.ID_PREFIX.length());
        }
        return id;
    }

    private static String stripLayoutPrefix(String layout) {
        if (layout.startsWith(SdkConstants.LAYOUT_RESOURCE_PREFIX)) {
            return layout.substring(SdkConstants.LAYOUT_RESOURCE_PREFIX.length());
        }
        return layout;
    }

    private static class LayoutInfo {
        final String name;
        final File file;
        final Map<String, Location> idToLocation = new HashMap<>();
        final List<IncludeInfo> includes = new ArrayList<>();

        LayoutInfo(String name, File file) {
            this.name = name;
            this.file = file;
        }
    }

    private static class IncludeInfo {
        final String targetLayout;
        final Location location;

        IncludeInfo(String targetLayout, Location location) {
            this.targetLayout = targetLayout;
            this.location = location;
        }
    }
}