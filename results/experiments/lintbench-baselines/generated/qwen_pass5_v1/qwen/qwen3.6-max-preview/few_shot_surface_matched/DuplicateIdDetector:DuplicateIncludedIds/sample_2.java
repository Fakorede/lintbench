package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
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
                            + "layouts are combined with include tags, then the id's need to be "
                            + "unique within any chain of included layouts, or "
                            + "`Activity#findViewById()` can return an unexpected view.",
                    Category.CORRECTNESS,
                    6,
                    Severity.WARNING,
                    new Implementation(DuplicateIdDetector.class, Scope.ALL_RESOURCE_FILES));

    private static final String ATTR_ID = "id";
    private static final String TAG_INCLUDE = "include";
    private static final String ATTR_LAYOUT = "layout";
    private static final String PREFIX_LAYOUT = "@layout/";
    private static final String PREFIX_ID_NEW = "@+id/";
    private static final String PREFIX_ID_REF = "@id/";

    private Map<String, Map<String, Location>> fileToIdLocations;
    private Map<String, Set<String>> fileToIncludes;
    private String currentResourceName;

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_ID);
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_INCLUDE);
    }

    @Override
    public void beforeCheckFile(Context context) {
        String name = context.file.getName();
        if (name.endsWith(".xml")) {
            currentResourceName = name.substring(0, name.length() - 4);
        } else {
            currentResourceName = name;
        }
    }

    @Override
    public void afterCheckFile(Context context) {
        currentResourceName = null;
    }

    @Override
    public void beforeCheckRootProject(Context context) {
        fileToIdLocations = new HashMap<>();
        fileToIncludes = new HashMap<>();
    }

    @Override
    public void afterCheckRootProject(Context context) {
        for (String file : fileToIdLocations.keySet()) {
            Map<String, Location> seenIds = new HashMap<>();
            checkChain(context, file, seenIds, new HashSet<>());
        }
    }

    private void checkChain(Context context, String file,
                            Map<String, Location> seenIds,
                            Set<String> visited) {
        if (visited.contains(file)) {
            return;
        }
        visited.add(file);

        Map<String, Location> idsInFile = fileToIdLocations.get(file);
        if (idsInFile != null) {
            for (Map.Entry<String, Location> entry : idsInFile.entrySet()) {
                String id = entry.getKey();
                Location loc = entry.getValue();
                if (seenIds.containsKey(id)) {
                    Location original = seenIds.get(id);
                    context.report(ISSUE, loc,
                            "Duplicate id `" + id + "`, already defined in included layout at "
                                    + original);
                } else {
                    seenIds.put(id, loc);
                }
            }
        }

        Set<String> includes = fileToIncludes.get(file);
        if (includes != null) {
            for (String included : includes) {
                checkChain(context, included, seenIds, visited);
            }
        }
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String layoutValue = element.getAttribute(ATTR_LAYOUT);
        if (layoutValue != null && layoutValue.startsWith(PREFIX_LAYOUT)) {
            String includedRes = layoutValue.substring(PREFIX_LAYOUT.length());
            fileToIncludes.computeIfAbsent(currentResourceName, k -> new HashSet<>())
                    .add(includedRes);
        }
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        String value = attribute.getValue();
        String id = null;
        if (value.startsWith(PREFIX_ID_NEW)) {
            id = value.substring(PREFIX_ID_NEW.length());
        } else if (value.startsWith(PREFIX_ID_REF)) {
            id = value.substring(PREFIX_ID_REF.length());
        }

        if (id != null && currentResourceName != null) {
            fileToIdLocations.computeIfAbsent(currentResourceName, k -> new HashMap<>())
                    .put(id, context.getLocation(attribute));
        }
    }

    @Override
    public String toString() {
        return "DuplicateIdDetector";
    }

    @Override
    public int compareTo(Detector other) {
        return getClass().getName().compareTo(other.getClass().getName());
    }
}