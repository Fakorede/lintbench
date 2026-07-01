package com.android.tools.lint.checks;

import static com.android.SdkConstants.ATTR_ID;
import static com.android.SdkConstants.ATTR_LAYOUT;
import static com.android.SdkConstants.LAYOUT_RESOURCE_PREFIX;
import static com.android.SdkConstants.VIEW_INCLUDE;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.LintMap;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DuplicateIdDetector extends LayoutDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "DuplicateIncludedIds",
                    "Duplicate ids across layouts combined with include tags",
                    "It's okay for two independent layouts to use the same ids. However, if"
                            + " layouts are combined with include tags, then the id's need to be"
                            + " unique within any chain of included layouts, or"
                            + " `Activity#findViewById()` can return an unexpected view.",
                    Category.CORRECTNESS,
                    7,
                    Severity.WARNING,
                    new Implementation(DuplicateIdDetector.class, Scope.ALL_RESOURCES_SCOPE));

    /** Map from layout name to the set of ids defined in that layout */
    private Map<String, Set<String>> mFileToIds;

    /** Map from layout name to the list of layouts it includes */
    private Map<String, List<String>> mIncludes;

    /** Current file being processed - ids found so far */
    private Set<String> mIds;

    /** Current file being processed - includes found so far */
    private List<String> mIncludes2;

    /** Location map for ids within the current file */
    private Map<String, Location> mIdToLocation;

    /** Map from layout name to location of include */
    private Map<String, Location> mIncludeToLocation;

    public DuplicateIdDetector() {}

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    @Nullable
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList(ATTR_ID, ATTR_LAYOUT);
    }

    @Override
    @Nullable
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(VIEW_INCLUDE);
    }

    @Override
    public void beforeCheckRootProject(@NonNull com.android.tools.lint.detector.api.Context context) {
        mFileToIds = new HashMap<>();
        mIncludes = new HashMap<>();
    }

    @Override
    public void beforeCheckFile(@NonNull com.android.tools.lint.detector.api.Context context) {
        mIds = new HashSet<>();
        mIncludes2 = new ArrayList<>();
        mIdToLocation = new HashMap<>();
        mIncludeToLocation = new HashMap<>();
    }

    @Override
    public void afterCheckFile(@NonNull com.android.tools.lint.detector.api.Context context) {
        // Store the ids and includes for this file
        String layoutName = getLayoutName(context.file);
        if (layoutName != null) {
            mFileToIds.put(layoutName, mIds);
            if (!mIncludes2.isEmpty()) {
                mIncludes.put(layoutName, mIncludes2);
            }
        }

        mIds = null;
        mIncludes2 = null;
        mIdToLocation = null;
        mIncludeToLocation = null;
    }

    @Override
    public void afterCheckRootProject(@NonNull com.android.tools.lint.detector.api.Context context) {
        // Now check for duplicate ids across included layouts
        // For each layout that has includes, check if any ids overlap with included layouts
        if (mIncludes.isEmpty()) {
            return;
        }

        // Build the graph and check for duplicates
        // We need to find all chains of included layouts and check for id collisions
        for (Map.Entry<String, List<String>> entry : mIncludes.entrySet()) {
            String layout = entry.getKey();
            List<String> includes = entry.getValue();

            Set<String> idsInLayout = mFileToIds.get(layout);
            if (idsInLayout == null) {
                idsInLayout = Collections.emptySet();
            }

            // Check each included layout for id overlaps
            for (String included : includes) {
                Set<String> idsInIncluded = mFileToIds.get(included);
                if (idsInIncluded == null) {
                    continue;
                }

                // Find duplicates
                Set<String> duplicates = new HashSet<>(idsInLayout);
                duplicates.retainAll(idsInIncluded);

                if (!duplicates.isEmpty()) {
                    // Report the issue
                    StringBuilder sb = new StringBuilder();
                    sb.append("Duplicate id");
                    if (duplicates.size() > 1) {
                        sb.append("s");
                    }
                    sb.append(" ");
                    List<String> sortedDuplicates = new ArrayList<>(duplicates);
                    Collections.sort(sortedDuplicates);
                    boolean first = true;
                    for (String dup : sortedDuplicates) {
                        if (!first) {
                            sb.append(", ");
                        }
                        sb.append("`").append(dup).append("`");
                        first = false;
                    }
                    sb.append(" in layout `").append(included).append("`");
                    sb.append(" and layout `").append(layout).append("`");
                    sb.append(": Included by `").append(layout).append("`");

                    // We report at the project level since we may not have exact locations
                    // across files
                    context.report(
                            ISSUE,
                            Location.create(context.getProject().getDir()),
                            sb.toString());
                }
            }

            // Also check transitive includes
            Set<String> visited = new HashSet<>();
            visited.add(layout);
            checkTransitiveIncludes(context, layout, idsInLayout, includes, visited);
        }
    }

    private void checkTransitiveIncludes(
            @NonNull com.android.tools.lint.detector.api.Context context,
            @NonNull String rootLayout,
            @NonNull Set<String> rootIds,
            @NonNull List<String> includes,
            @NonNull Set<String> visited) {

        for (String included : includes) {
            if (visited.contains(included)) {
                continue;
            }
            visited.add(included);

            List<String> transitiveIncludes = mIncludes.get(included);
            if (transitiveIncludes == null) {
                continue;
            }

            for (String transitive : transitiveIncludes) {
                if (visited.contains(transitive)) {
                    continue;
                }

                Set<String> idsInTransitive = mFileToIds.get(transitive);
                if (idsInTransitive == null) {
                    continue;
                }

                Set<String> duplicates = new HashSet<>(rootIds);
                duplicates.retainAll(idsInTransitive);

                if (!duplicates.isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    sb.append("Duplicate id");
                    if (duplicates.size() > 1) {
                        sb.append("s");
                    }
                    sb.append(" ");
                    List<String> sortedDuplicates = new ArrayList<>(duplicates);
                    Collections.sort(sortedDuplicates);
                    boolean first = true;
                    for (String dup : sortedDuplicates) {
                        if (!first) {
                            sb.append(", ");
                        }
                        sb.append("`").append(dup).append("`");
                        first = false;
                    }
                    sb.append(" in layout `").append(transitive).append("`");
                    sb.append(" and layout `").append(rootLayout).append("`");
                    sb.append(": Transitively included via `").append(included).append("`");

                    context.report(
                            ISSUE,
                            Location.create(context.getProject().getDir()),
                            sb.toString());
                }

                checkTransitiveIncludes(
                        context, rootLayout, rootIds,
                        Collections.singletonList(transitive), visited);
            }
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Handle <include> elements
        if (VIEW_INCLUDE.equals(element.getTagName())) {
            String layout = element.getAttribute(ATTR_LAYOUT);
            if (layout != null && layout.startsWith(LAYOUT_RESOURCE_PREFIX)) {
                String layoutName = layout.substring(LAYOUT_RESOURCE_PREFIX.length());
                mIncludes2.add(layoutName);

                // Record the location of this include
                mIncludeToLocation.put(layoutName, context.getLocation(element));
            }
        }
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String name = attribute.getLocalName();
        if (ATTR_ID.equals(name)) {
            String id = attribute.getValue();
            if (id != null && !id.isEmpty()) {
                if (mIds.contains(id)) {
                    // Duplicate id within the same file
                    context.report(
                            ISSUE,
                            attribute,
                            context.getLocation(attribute),
                            "Duplicate id `" + id + "` within the same layout");
                } else {
                    mIds.add(id);
                    mIdToLocation.put(id, context.getLocation(attribute));
                }
            }
        } else if (ATTR_LAYOUT.equals(name)) {
            // Handle layout attribute on include tags (already handled in visitElement)
            Element owner = attribute.getOwnerElement();
            if (VIEW_INCLUDE.equals(owner.getTagName())) {
                String layout = attribute.getValue();
                if (layout != null && layout.startsWith(LAYOUT_RESOURCE_PREFIX)) {
                    String layoutName = layout.substring(LAYOUT_RESOURCE_PREFIX.length());
                    if (!mIncludes2.contains(layoutName)) {
                        mIncludes2.add(layoutName);
                        mIncludeToLocation.put(layoutName, context.getLocation(attribute));
                    }
                }
            }
        }
    }

    @Nullable
    private static String getLayoutName(@NonNull File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        if (dot != -1) {
            name = name.substring(0, dot);
        }
        return name;
    }

    /** A simple class to represent a layout reference with its location */
    static class LayoutReference implements Comparable<LayoutReference> {
        final String layoutName;
        final Location location;

        LayoutReference(String layoutName, Location location) {
            this.layoutName = layoutName;
            this.location = location;
        }

        @Override
        public String toString() {
            return layoutName;
        }

        @Override
        public int compareTo(@NonNull LayoutReference other) {
            return this.layoutName.compareTo(other.layoutName);
        }
    }
}