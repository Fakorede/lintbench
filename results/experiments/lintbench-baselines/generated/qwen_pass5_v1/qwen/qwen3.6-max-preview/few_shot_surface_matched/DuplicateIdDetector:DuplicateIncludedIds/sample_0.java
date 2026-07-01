package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
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

import java.io.File;
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
                    "It's okay for two independent layouts to use the same ids. However, if layouts are combined with include tags, then the id's need to be unique within any chain of included layouts, or Activity#findViewById() can return an unexpected view.",
                    Category.CORRECTNESS,
                    6,
                    Severity.WARNING,
                    new Implementation(DuplicateIdDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private Map<File, Set<String>> fileIds;
    private Map<File, Set<String>> fileIncludes;
    private Map<File, Map<String, Location>> fileIdLocations;
    private Map<String, File> layoutNameToFile;
    private Set<String> reportedDuplicates;

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(SdkConstants.ATTR_ID);
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(SdkConstants.TAG_INCLUDE);
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        fileIds = new HashMap<>();
        fileIncludes = new HashMap<>();
        fileIdLocations = new HashMap<>();
        layoutNameToFile = new HashMap<>();
        reportedDuplicates = new HashSet<>();
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        String name = getLayoutName(context.file);
        layoutNameToFile.put(name, context.file);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String value = attribute.getValue();
        if (value != null && (value.startsWith("@+id/") || value.startsWith("@id/"))) {
            String id = value.substring(value.indexOf('/') + 1);
            File file = context.file;
            fileIds.computeIfAbsent(file, k -> new HashSet<>()).add(id);
            fileIdLocations.computeIfAbsent(file, k -> new HashMap<>()).put(id, context.getLocation(attribute));
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String layoutAttr = element.getAttribute(SdkConstants.ATTR_LAYOUT);
        if (layoutAttr != null && layoutAttr.startsWith("@layout/")) {
            String includedName = layoutAttr.substring(layoutAttr.indexOf('/') + 1);
            fileIncludes.computeIfAbsent(context.file, k -> new HashSet<>()).add(includedName);
        }
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        // No-op
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (fileIds == null) return;

        for (File rootFile : layoutNameToFile.values()) {
            Set<String> seenIds = new HashSet<>();
            Map<String, Location> firstSeenLocations = new HashMap<>();
            Set<File> visited = new HashSet<>();
            checkChain(rootFile, seenIds, firstSeenLocations, visited, context);
        }
    }

    private void checkChain(File file, Set<String> seenIds, Map<String, Location> firstSeenLocations, Set<File> visited, Context context) {
        if (file == null || visited.contains(file)) return;
        visited.add(file);

        Set<String> ids = fileIds.get(file);
        if (ids != null) {
            Map<String, Location> locations = fileIdLocations.get(file);
            for (String id : ids) {
                if (seenIds.contains(id)) {
                    String reportKey = id + ":" + file.getPath();
                    if (!reportedDuplicates.contains(reportKey)) {
                        reportedDuplicates.add(reportKey);
                        Location location = locations != null ? locations.get(id) : Location.create(file);
                        Location secondary = firstSeenLocations.get(id);
                        if (secondary != null) {
                            location.setSecondary(secondary);
                        }
                        context.report(ISSUE, location, "Duplicate id @" + id + ", already defined in this layout chain");
                    }
                } else {
                    seenIds.add(id);
                    if (locations != null) {
                        firstSeenLocations.put(id, locations.get(id));
                    }
                }
            }
        }

        Set<String> includes = fileIncludes.get(file);
        if (includes != null) {
            for (String incName : includes) {
                File incFile = layoutNameToFile.get(incName);
                checkChain(incFile, seenIds, firstSeenLocations, visited, context);
            }
        }
    }

    private static String getLayoutName(File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    @Override
    public String toString() {
        return "DuplicateIdDetector for DuplicateIncludedIds";
    }

    @Override
    public int compareTo(Detector other) {
        return this.getClass().getName().compareTo(other.getClass().getName());
    }
}