package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

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

public class DuplicateIdDetector extends ResourceXmlDetector {

    public static final Issue ISSUE = Issue.create(
            "DuplicateIncludedIds",
            "Duplicate ids across layouts combined with include tags",
            "It's okay for two independent layouts to use the same ids. However, if "
                    + "layouts are combined with include tags, then the ids need to be unique "
                    + "within any chain of included layouts, or Activity#findViewById() can "
                    + "return an unexpected view.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(DuplicateIdDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    private final Map<String, Set<String>> mLayoutIds = new HashMap<>();
    private final Map<String, List<String>> mLayoutIncludes = new HashMap<>();
    private final Map<String, Map<String, List<Location>>> mIncludeTagLocations = new HashMap<>();
    private final Map<String, Map<String, List<Location>>> mIdLocations = new HashMap<>();

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mLayoutIds.clear();
        mLayoutIncludes.clear();
        mIncludeTagLocations.clear();
        mIdLocations.clear();
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(SdkConstants.TAG_INCLUDE);
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(SdkConstants.ATTR_ID);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        if (context.getResourceFolderType() != ResourceFolderType.LAYOUT) {
            return;
        }

        String layoutName = getBaseName(context.file.getName());
        String id = getIdName(attribute.getValue());
        if (id == null) {
            return;
        }

        mLayoutIds.computeIfAbsent(layoutName, k -> new HashSet<>()).add(id);
        mIdLocations
                .computeIfAbsent(layoutName, k -> new HashMap<>())
                .computeIfAbsent(id, k -> new ArrayList<>())
                .add(context.getValueLocation(attribute));
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (context.getResourceFolderType() != ResourceFolderType.LAYOUT) {
            return;
        }

        String layoutName = getBaseName(context.file.getName());
        String includedLayout = getIncludedLayoutName(
                element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT));
        if (includedLayout == null) {
            return;
        }

        mLayoutIncludes.computeIfAbsent(layoutName, k -> new ArrayList<>()).add(includedLayout);
        mIncludeTagLocations
                .computeIfAbsent(layoutName, k -> new HashMap<>())
                .computeIfAbsent(includedLayout, k -> new ArrayList<>())
                .add(context.getLocation(element));
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        checkIncludedIds(context);
    }

    private void checkIncludedIds(@NonNull Context context) {
        Map<String, Set<String>> closureCache = new HashMap<>();

        for (Map.Entry<String, List<String>> entry : mLayoutIncludes.entrySet()) {
            String parentLayout = entry.getKey();
            List<String> directIncludes = entry.getValue();
            Set<String> parentIds = mLayoutIds.getOrDefault(parentLayout, Collections.emptySet());

            Map<String, List<Location>> parentIncludeLocations =
                    mIncludeTagLocations.get(parentLayout);
            if (parentIncludeLocations == null) {
                continue;
            }

            for (String includedLayout : directIncludes) {
                Set<String> includedClosure = computeClosureIds(
                        includedLayout, closureCache, new HashSet<>());

                Set<String> siblingIds = new HashSet<>(parentIds);
                for (String otherInclude : directIncludes) {
                    if (otherInclude.equals(includedLayout)) {
                        continue;
                    }
                    siblingIds.addAll(computeClosureIds(
                            otherInclude, closureCache, new HashSet<>()));
                }

                Set<String> duplicates = new HashSet<>(includedClosure);
                duplicates.retainAll(siblingIds);
                if (duplicates.isEmpty()) {
                    continue;
                }

                List<Location> includeLocations = parentIncludeLocations.get(includedLayout);
                if (includeLocations == null) {
                    continue;
                }

                String message = "The following ids are duplicated in this include chain: "
                        + String.join(", ", duplicates);
                for (Location location : includeLocations) {
                    context.report(ISSUE, location, message);
                }
            }
        }
    }

    @NonNull
    private Set<String> computeClosureIds(
            @NonNull String layoutName,
            @NonNull Map<String, Set<String>> cache,
            @NonNull Set<String> visiting) {
        if (visiting.contains(layoutName)) {
            return Collections.emptySet();
        }

        Set<String> cached = cache.get(layoutName);
        if (cached != null) {
            return cached;
        }

        visiting.add(layoutName);

        Set<String> ids = new HashSet<>(mLayoutIds.getOrDefault(layoutName, Collections.emptySet()));
        List<String> includes = mLayoutIncludes.getOrDefault(layoutName, Collections.emptyList());
        for (String include : includes) {
            ids.addAll(computeClosureIds(include, cache, visiting));
        }

        visiting.remove(layoutName);
        cache.put(layoutName, ids);
        return ids;
    }

    @NonNull
    private static String getBaseName(@NonNull String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    private static String getIdName(@NonNull String value) {
        int slash = value.lastIndexOf('/');
        if (slash == -1 || slash == value.length() - 1) {
            return null;
        }
        if (value.startsWith("@+id/") || value.startsWith("@id/")) {
            return value.substring(slash + 1);
        }
        return null;
    }

    private static String getIncludedLayoutName(@NonNull String value) {
        int slash = value.lastIndexOf('/');
        if (slash == -1 || slash == value.length() - 1) {
            return null;
        }
        if (value.startsWith("@layout/") || value.startsWith("@+layout/")) {
            return value.substring(slash + 1);
        }
        return null;
    }
}