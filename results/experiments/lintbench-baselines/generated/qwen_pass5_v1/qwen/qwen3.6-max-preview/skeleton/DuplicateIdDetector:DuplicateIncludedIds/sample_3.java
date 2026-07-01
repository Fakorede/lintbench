package com.android.tools.lint.checks;

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
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class DuplicateIdDetector extends LayoutDetector {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    private static final Implementation IMPLEMENTATION =
            new Implementation(DuplicateIdDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "DuplicateIncludedIds",
                    "Duplicate ids across layouts combined with include tags",
                    "It's okay for two independent layouts to use the same ids. However, if " +
                    "layouts are combined with include tags, then the id's need to be unique " +
                    "within any chain of included layouts, or `Activity#findViewById()` can " +
                    "return an unexpected view.",
                    Category.CORRECTNESS,
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private Map<String, Set<String>> layoutToIds;
    private Map<String, Set<String>> layoutToIncludes;
    private Map<String, Map<String, Location>> layoutToIdLocations;
    private Set<String> reportedIssues;

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList("id", "layout");
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("include");
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        super.beforeCheckFile(context);
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        super.afterCheckFile(context);
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        layoutToIds = new HashMap<>();
        layoutToIncludes = new HashMap<>();
        layoutToIdLocations = new HashMap<>();
        reportedIssues = new HashSet<>();
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        for (String layout : layoutToIds.keySet()) {
            Set<String> visited = new HashSet<>();
            Map<String, String> idToSourceLayout = new HashMap<>();
            checkIncludeChain(context, layout, visited, idToSourceLayout);
        }

        layoutToIds = null;
        layoutToIncludes = null;
        layoutToIdLocations = null;
        reportedIssues = null;
    }

    private void checkIncludeChain(@NonNull Context context,
                                   @NonNull String currentLayout,
                                   @NonNull Set<String> visited,
                                   @NonNull Map<String, String> idToSourceLayout) {
        if (!visited.add(currentLayout)) {
            return;
        }

        Set<String> ids = layoutToIds.getOrDefault(currentLayout, Collections.emptySet());
        Map<String, Location> locations = layoutToIdLocations.getOrDefault(currentLayout, Collections.emptyMap());

        for (String id : ids) {
            if (idToSourceLayout.containsKey(id)) {
                String originalLayout = idToSourceLayout.get(id);
                String reportKey = currentLayout + ":" + id + ":" + originalLayout;
                if (reportedIssues.add(reportKey)) {
                    Location location = locations.get(id);
                    if (location != null) {
                        String message = String.format(
                                "Duplicate id `%s`, already defined in layout `%s` which is included in this layout",
                                id, originalLayout);
                        context.report(ISSUE, location, message);
                    }
                }
            } else {
                idToSourceLayout.put(id, currentLayout);
            }
        }

        Set<String> includes = layoutToIncludes.getOrDefault(currentLayout, Collections.emptySet());
        for (String includedLayout : includes) {
            checkIncludeChain(context, includedLayout, visited, idToSourceLayout);
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        super.visitElement(context, element);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String layoutName = context.getResourceName();
        if (layoutName == null) {
            return;
        }

        String localName = attribute.getLocalName();
        String value = attribute.getValue();

        if ("id".equals(localName) && ANDROID_URI.equals(attribute.getNamespaceURI())) {
            String id = extractResourcePrefix(value, "@+id/", "@id/");
            layoutToIds.computeIfAbsent(layoutName, k -> new HashSet<>()).add(id);
            layoutToIdLocations.computeIfAbsent(layoutName, k -> new HashMap<>())
                    .putIfAbsent(id, context.getLocation(attribute));
        } else if ("layout".equals(localName) && "include".equals(attribute.getOwnerElement().getTagName())) {
            String included = extractResourcePrefix(value, "@layout/");
            layoutToIncludes.computeIfAbsent(layoutName, k -> new HashSet<>()).add(included);
        }
    }

    @NonNull
    private static String extractResourcePrefix(@NonNull String value, @NonNull String... prefixes) {
        for (String prefix : prefixes) {
            if (value.startsWith(prefix)) {
                return value.substring(prefix.length());
            }
        }
        return value;
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