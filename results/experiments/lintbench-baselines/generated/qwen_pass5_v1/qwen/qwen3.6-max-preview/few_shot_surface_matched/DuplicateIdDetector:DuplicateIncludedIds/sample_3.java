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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DuplicateIdDetector extends LayoutDetector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "DuplicateIncludedIds",
            "Duplicate ids across layouts combined with include tags",
            "It's okay for two independent layouts to use the same ids. However, if layouts are combined with include tags, then the id's need to be unique within any chain of included layouts, or Activity#findViewById() can return an unexpected view.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(DuplicateIdDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private Map<String, Set<String>> fileIncludes = new HashMap<>();
    private Map<String, Map<String, Location>> fileIdLocations = new HashMap<>();
    private Map<String, XmlContext> fileContexts = new HashMap<>();

    private Set<String> currentIncludes = new HashSet<>();
    private Map<String, Location> currentIdLocations = new HashMap<>();

    @Override
    public boolean appliesTo(Context context, ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList("id");
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("include");
    }

    @Override
    public void beforeCheckFile(XmlContext context) {
        currentIncludes = new HashSet<>();
        currentIdLocations = new HashMap<>();
    }

    @Override
    public void afterCheckFile(XmlContext context) {
        String name = context.file.getName().replace(".xml", "");
        fileIncludes.put(name, currentIncludes);
        fileIdLocations.put(name, currentIdLocations);
        fileContexts.put(name, context);
    }

    @Override
    public void beforeCheckRootProject(Context context) {
        fileIncludes.clear();
        fileIdLocations.clear();
        fileContexts.clear();
    }

    @Override
    public void afterCheckRootProject(Context context) {
        Set<String> reported = new HashSet<>();
        for (String rootFile : fileIncludes.keySet()) {
            Map<String, List<Location>> allIds = new HashMap<>();
            collectIds(rootFile, new HashSet<>(), allIds);

            for (Map.Entry<String, List<Location>> entry : allIds.entrySet()) {
                List<Location> locs = entry.getValue();
                if (locs.size() > 1) {
                    String reportKey = rootFile + ":" + entry.getKey();
                    if (reported.add(reportKey)) {
                        XmlContext ctx = fileContexts.get(rootFile);
                        if (ctx != null) {
                            ctx.report(ISSUE, locs.get(1),
                                    "Duplicate id @" + entry.getKey() + ", already defined in an included layout");
                        }
                    }
                }
            }
        }
    }

    private void collectIds(String file, Set<String> visited, Map<String, List<Location>> allIds) {
        if (!visited.add(file)) return;
        Map<String, Location> ids = fileIdLocations.get(file);
        if (ids != null) {
            for (Map.Entry<String, Location> entry : ids.entrySet()) {
                allIds.computeIfAbsent(entry.getKey(), k -> new ArrayList<>()).add(entry.getValue());
            }
        }
        Set<String> includes = fileIncludes.get(file);
        if (includes != null) {
            for (String inc : includes) {
                collectIds(inc, visited, allIds);
            }
        }
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String layout = element.getAttribute("layout");
        if (layout != null && layout.startsWith("@layout/")) {
            currentIncludes.add(layout.substring("@layout/".length()));
        }
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        String value = attribute.getValue();
        if (value != null && (value.startsWith("@+id/") || value.startsWith("@id/"))) {
            String id = value.substring(value.indexOf('/') + 1);
            currentIdLocations.put(id, context.getLocation(attribute));
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
}