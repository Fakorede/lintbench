package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class DuplicateIdDetector extends ResourceXmlDetector {

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
            new Implementation(DuplicateIdDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    private final Map<String, File> layoutNameToMap = new HashMap<>();
    private final Map<File, Set<String>> fileToIds = new HashMap<>();
    private final Map<File, Set<String>> fileToIncludes = new HashMap<>();
    private final Map<File, Map<String, Location>> fileToIdLocations = new HashMap<>();

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Nullable
    @Override
    public java.util.Collection<String> getApplicableElements() {
        return null; // Visit all elements to find IDs and includes
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        File file = context.file;
        String fileName = file.getName();
        int dot = fileName.lastIndexOf('.');
        String layoutName = dot != -1 ? fileName.substring(0, dot) : fileName;
        layoutNameToMap.put(layoutName, file);

        // Extract ID if present
        if (element.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ID)) {
            Attr idAttr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ID);
            if (idAttr != null) {
                String idValue = idAttr.getValue();
                String idName = idValue.substring(idValue.lastIndexOf('/') + 1);
                fileToIds.computeIfAbsent(file, f -> new HashSet<>()).add(idName);
                
                Location location = context.getLocation(idAttr);
                fileToIdLocations.computeIfAbsent(file, f -> new HashMap<>()).put(idName, location);
            }
        }

        // Extract included layout if this is an include tag
        if (element.getTagName().equals(SdkConstants.VIEW_INCLUDE)) {
            Attr layoutAttr = element.getAttributeNode(SdkConstants.ATTR_LAYOUT);
            if (layoutAttr != null) {
                String layoutValue = layoutAttr.getValue();
                if (layoutValue.startsWith("@layout/")) {
                    String includedLayout = layoutValue.substring(layoutValue.lastIndexOf('/') + 1);
                    fileToIncludes.computeIfAbsent(file, f -> new HashSet<>()).add(includedLayout);
                }
            }
        }
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        for (File parentFile : fileToIds.keySet()) {
            Set<File> visited = new HashSet<>();
            Map<String, File> idToOriginatingFile = new HashMap<>();

            // Populate initial parent IDs
            for (String id : fileToIds.getOrDefault(parentFile, Collections.emptySet())) {
                idToOriginatingFile.put(id, parentFile);
            }

            dfsCheck(context, parentFile, visited, idToOriginatingFile);
        }
    }

    private void dfsCheck(Context context, File currentFile, Set<File> visited, Map<String, File> idToOriginatingFile) {
        if (!visited.add(currentFile)) {
            return;
        }

        Set<String> includes = fileToIncludes.get(currentFile);
        if (includes == null) {
            return;
        }

        for (String includedLayoutName : includes) {
            File includedFile = layoutNameToMap.get(includedLayoutName);
            if (includedFile == null || !includedFile.exists()) {
                continue;
            }

            Set<String> childIds = fileToIds.getOrDefault(includedFile, Collections.emptySet());
            for (String childId : childIds) {
                if (idToOriginatingFile.containsKey(childId)) {
                    File conflictingFile = idToOriginatingFile.get(childId);
                    if (conflictingFile.equals(includedFile)) {
                        continue;
                    }

                    Location location = null;
                    Map<String, Location> locationsMap = fileToIdLocations.get(includedFile);
                    if (locationsMap != null) {
                        location = locationsMap.get(childId);
                    }

                    if (location == null) {
                        location = Location.create(includedFile);
                    }

                    Location secondary = null;
                    Map<String, Location> conflictingLocationsMap = fileToIdLocations.get(conflictingFile);
                    if (conflictingLocationsMap != null) {
                        secondary = conflictingLocationsMap.get(childId);
                    }

                    if (secondary != null) {
                        location.setSecondary(secondary);
                        secondary.setMessage("Conflicting ID defined here");
                    }

                    String message = String.format(
                            "Duplicate id `%s` in included layouts: layout `%s` and layout `%s` are combined but both define this ID.",
                            childId,
                            conflictingFile.getName(),
                            includedFile.getName()
                    );

                    context.report(ISSUE, location, message);
                } else {
                    idToOriginatingFile.put(childId, includedFile);
                }
            }

            dfsCheck(context, includedFile, visited, idToOriginatingFile);
        }
    }
}