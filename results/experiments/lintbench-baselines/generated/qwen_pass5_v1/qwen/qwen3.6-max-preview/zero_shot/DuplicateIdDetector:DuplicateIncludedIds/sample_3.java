package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.*;
import com.android.SdkConstants;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.io.File;
import java.util.*;

public class DuplicateIdDetector extends ResourceXmlDetector {

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
            new Implementation(DuplicateIdDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    private Map<String, LayoutInfo> layoutMap = new HashMap<>();
    private Set<String> reportedDuplicates = new HashSet<>();

    @Override
    public Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (context.getResourceFolderType() != ResourceFolderType.LAYOUT) {
            return;
        }

        String resName = getLayoutResourceName(context);
        LayoutInfo info = layoutMap.computeIfAbsent(resName, k -> new LayoutInfo(k));

        Attr idAttr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ID);
        if (idAttr != null) {
            String idValue = idAttr.getValue();
            if (idValue.startsWith("@+id/") || idValue.startsWith("@id/")) {
                String idName = idValue.substring(idValue.indexOf('/') + 1);
                info.ids.add(idName);
                info.idLocations.put(idName, context.getLocation(idAttr));
            }
        }

        if (SdkConstants.TAG_INCLUDE.equals(element.getTagName())) {
            Attr layoutAttr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT);
            if (layoutAttr != null) {
                String layoutValue = layoutAttr.getValue();
                if (layoutValue.startsWith("@layout/")) {
                    String incName = layoutValue.substring(layoutValue.indexOf('/') + 1);
                    info.includes.add(incName);
                    info.includeLocations.put(incName, context.getLocation(layoutAttr));
                }
            }
        }
    }

    private String getLayoutResourceName(XmlContext context) {
        String name = context.file.getName();
        int dot = name.indexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    @Override
    public void afterCheckProject(Context context) {
        for (String layoutName : layoutMap.keySet()) {
            checkChain(context, layoutName, new HashSet<>(), new HashMap<>());
        }
        layoutMap.clear();
        reportedDuplicates.clear();
    }

    private void checkChain(Context context, String layoutName, Set<String> path, Map<String, Location> seenIds) {
        if (path.contains(layoutName)) {
            return;
        }
        path.add(layoutName);

        LayoutInfo info = layoutMap.get(layoutName);
        if (info != null) {
            for (String id : info.ids) {
                Location currentLoc = info.idLocations.get(id);
                if (seenIds.containsKey(id)) {
                    Location prevLoc = seenIds.get(id);
                    String key = id + ":" + prevLoc.getFile().getPath() + ":" + currentLoc.getFile().getPath();
                    if (reportedDuplicates.add(key)) {
                        String msg = String.format(
                                "Duplicate id `%s`, already defined in layout `%s`",
                                id, getBaseName(prevLoc.getFile()));
                        context.report(ISSUE, currentLoc, msg);
                    }
                } else {
                    seenIds.put(id, currentLoc);
                }
            }

            for (String inc : info.includes) {
                checkChain(context, inc, path, seenIds);
            }
        }
        path.remove(layoutName);
    }

    private String getBaseName(File file) {
        String name = file.getName();
        int dot = name.indexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static class LayoutInfo {
        final String name;
        final List<String> ids = new ArrayList<>();
        final Map<String, Location> idLocations = new HashMap<>();
        final List<String> includes = new ArrayList<>();
        final Map<String, Location> includeLocations = new HashMap<>();

        LayoutInfo(String name) {
            this.name = name;
        }
    }
}