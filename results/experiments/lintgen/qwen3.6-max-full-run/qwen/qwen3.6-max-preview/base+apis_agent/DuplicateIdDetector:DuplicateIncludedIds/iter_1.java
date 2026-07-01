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

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
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
            new Implementation(DuplicateIdDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private final Map<String, Map<String, Location>> layoutIdLocations = new HashMap<>();
    private final Map<String, Set<String>> layoutIncludes = new HashMap<>();
    private final Set<String> reported = new HashSet<>();

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("*");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (context.getResourceFolderType() != ResourceFolderType.LAYOUT) {
            return;
        }

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
                layoutIdLocations.computeIfAbsent(layoutName, k -> new HashMap<>())
                        .put(idName, context.getLocation(element));
            }
        }

        if ("include".equals(element.getTagName())) {
            String layoutAttr = element.getAttribute("layout");
            if (layoutAttr != null && layoutAttr.startsWith("@layout/")) {
                String includedName = layoutAttr.substring(8);
                layoutIncludes.computeIfAbsent(layoutName, k -> new HashSet<>()).add(includedName);
            }
        }
    }

    @Override
    public void afterCheckRootProject(Context context) {
        for (String layoutName : layoutIdLocations.keySet()) {
            checkChain(context, layoutName, new HashSet<>(), new HashMap<>());
        }

        layoutIdLocations.clear();
        layoutIncludes.clear();
        reported.clear();
    }

    private void checkChain(Context context, String layoutName,
                            Set<String> visited, Map<String, Location> currentIds) {
        if (visited.contains(layoutName)) {
            return;
        }
        visited.add(layoutName);

        Map<String, Location> ids = layoutIdLocations.get(layoutName);
        if (ids != null) {
            for (Map.Entry<String, Location> entry : ids.entrySet()) {
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
                }
            }
        }

        Set<String> includes = layoutIncludes.get(layoutName);
        if (includes != null) {
            for (String included : includes) {
                checkChain(context, included, new HashSet<>(visited), new HashMap<>(currentIds));
            }
        }
    }
}