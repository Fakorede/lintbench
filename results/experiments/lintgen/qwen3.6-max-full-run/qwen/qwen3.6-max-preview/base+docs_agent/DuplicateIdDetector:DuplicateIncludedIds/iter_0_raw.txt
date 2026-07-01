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
import org.jetbrains.annotations.NonNull;
import org.w3c.dom.Element;

import java.io.File;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
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

    private final Map<String, Set<String>> layoutIds = new HashMap<>();
    private final Map<String, Set<String>> layoutIncludes = new HashMap<>();
    private final Map<String, Map<String, Location>> layoutIncludeLocations = new HashMap<>();
    private final Map<String, File> layoutFiles = new HashMap<>();

    @NonNull
    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("*");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        ResourceFolderType type = context.getResourceFolderType();
        if (type != ResourceFolderType.LAYOUT) {
            return;
        }

        String layoutName = context.file.getName().replace(".xml", "");
        layoutFiles.put(layoutName, context.file);
        layoutIds.computeIfAbsent(layoutName, k -> new HashSet<>());
        layoutIncludes.computeIfAbsent(layoutName, k -> new HashSet<>());
        layoutIncludeLocations.computeIfAbsent(layoutName, k -> new HashMap<>());

        String id = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ID);
        if (id != null && !id.isEmpty()) {
            layoutIds.get(layoutName).add(extractIdName(id));
        }

        if (SdkConstants.TAG_INCLUDE.equals(element.getTagName())) {
            String layoutAttr = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT);
            if (layoutAttr != null && layoutAttr.startsWith("@layout/")) {
                String includedLayout = layoutAttr.substring(8);
                layoutIncludes.get(layoutName).add(includedLayout);
                layoutIncludeLocations.get(layoutName).put(includedLayout, context.getLocation(element));
            }
        }
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        for (String rootLayout : layoutIncludes.keySet()) {
            Set<String> directIncludes = layoutIncludes.get(rootLayout);
            if (directIncludes.isEmpty()) {
                continue;
            }

            Map<String, String> idToLayout = new HashMap<>();
            for (String id : layoutIds.getOrDefault(rootLayout, Collections.emptySet())) {
                idToLayout.put(id, rootLayout);
            }

            Set<String> visited = new HashSet<>();
            Queue<String> queue = new ArrayDeque<>(directIncludes);
            visited.add(rootLayout);

            while (!queue.isEmpty()) {
                String currentLayout = queue.poll();
                if (!visited.add(currentLayout)) {
                    continue;
                }

                for (String id : layoutIds.getOrDefault(currentLayout, Collections.emptySet())) {
                    if (idToLayout.containsKey(id)) {
                        String originalLayout = idToLayout.get(id);
                        Location location = layoutIncludeLocations.get(rootLayout).get(currentLayout);
                        if (location == null) {
                            File file = layoutFiles.get(rootLayout);
                            location = file != null ? Location.create(file) : Location.create(context.getProject().getDir());
                        }
                        String message = String.format(
                                "Duplicate id `@+id/%s`, already defined in layout `@layout/%s`",
                                id, originalLayout);
                        context.report(ISSUE, location, message);
                    } else {
                        idToLayout.put(id, currentLayout);
                    }
                }

                Set<String> nextIncludes = layoutIncludes.getOrDefault(currentLayout, Collections.emptySet());
                queue.addAll(nextIncludes);
            }
        }

        layoutIds.clear();
        layoutIncludes.clear();
        layoutIncludeLocations.clear();
        layoutFiles.clear();
    }

    @NonNull
    private static String extractIdName(@NonNull String id) {
        if (id.startsWith("@+id/")) {
            return id.substring(5);
        }
        if (id.startsWith("@id/")) {
            return id.substring(4);
        }
        if (id.startsWith("@android:id/")) {
            return id.substring(12);
        }
        return id;
    }
}