package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
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

import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DuplicateIdDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "DuplicateIncludedIds",
            "Duplicate ids across layouts combined with include tags",
            "It's okay for two independent layouts to use the same ids. However, if layouts are combined with include tags, then the id's need to be unique within any chain of included layouts, or `Activity#findViewById()` can return an unexpected view.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(DuplicateIdDetector.class, EnumSet.of(Scope.ALL_RESOURCE_FILES)));

    private final Map<String, List<String>> layoutIds = new HashMap<>();
    private final Map<String, Map<String, Location>> layoutIdLocations = new HashMap<>();
    private final Map<String, List<String>> layoutIncludes = new HashMap<>();

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("*");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String fileName = context.file.getName();
        if (!fileName.endsWith(".xml")) {
            return;
        }
        String layoutName = fileName.substring(0, fileName.length() - 4);

        String id = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ID);
        if (id != null && !id.isEmpty()) {
            String idName = null;
            if (id.startsWith("@+id/")) {
                idName = id.substring(5);
            } else if (id.startsWith("@id/")) {
                idName = id.substring(4);
            }
            if (idName != null && !idName.isEmpty() && !idName.equals("null")) {
                layoutIds.computeIfAbsent(layoutName, k -> new ArrayList<>()).add(idName);
                layoutIdLocations.computeIfAbsent(layoutName, k -> new HashMap<>()).put(idName, context.getLocation(element));
            }
        }

        if (SdkConstants.TAG_INCLUDE.equals(element.getTagName())) {
            String layoutAttr = element.getAttribute(SdkConstants.ATTR_LAYOUT);
            if (layoutAttr != null && !layoutAttr.isEmpty() && layoutAttr.startsWith("@layout/")) {
                String includedName = layoutAttr.substring(8);
                if (includedName.endsWith(".xml")) {
                    includedName = includedName.substring(0, includedName.length() - 4);
                }
                layoutIncludes.computeIfAbsent(layoutName, k -> new ArrayList<>()).add(includedName);
            }
        }
    }

    @Override
    public void afterCheckProject(Context context) {
        if (layoutIds.isEmpty()) {
            return;
        }

        Set<String> reported = new HashSet<>();
        Set<String> path = new HashSet<>();
        Map<String, Location> currentIds = new HashMap<>();

        for (String layoutName : layoutIds.keySet()) {
            checkChain(context, layoutName, path, currentIds, reported);
        }

        layoutIds.clear();
        layoutIdLocations.clear();
        layoutIncludes.clear();
    }

    private void checkChain(Context context, String layoutName, Set<String> path,
                            Map<String, Location> currentIds, Set<String> reported) {
        if (path.contains(layoutName)) {
            return;
        }
        path.add(layoutName);

        Map<String, Location> idsInLayout = layoutIdLocations.get(layoutName);
        List<String> addedIds = new ArrayList<>();
        if (idsInLayout != null) {
            for (Map.Entry<String, Location> entry : idsInLayout.entrySet()) {
                String idName = entry.getKey();
                Location loc = entry.getValue();
                if (currentIds.containsKey(idName)) {
                    String reportKey = loc.getFile().getAbsolutePath() + ":" + idName;
                    if (reported.add(reportKey)) {
                        Location original = currentIds.get(idName);
                        String msg = String.format("Duplicate id `@+id/%s`, already defined in `%s`",
                                idName, original.getFile().getName());
                        loc.setSecondary(original);
                        context.report(ISSUE, loc, msg);
                    }
                } else {
                    currentIds.put(idName, loc);
                    addedIds.add(idName);
                }
            }
        }

        List<String> includes = layoutIncludes.get(layoutName);
        if (includes != null) {
            for (String included : includes) {
                checkChain(context, included, path, currentIds, reported);
            }
        }

        path.remove(layoutName);
        for (String id : addedIds) {
            currentIds.remove(id);
        }
    }
}