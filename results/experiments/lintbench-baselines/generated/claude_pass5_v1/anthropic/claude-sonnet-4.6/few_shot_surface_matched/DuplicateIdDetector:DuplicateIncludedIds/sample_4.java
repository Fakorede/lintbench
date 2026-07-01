package com.android.tools.lint.checks;

import static com.android.SdkConstants.ATTR_ID;
import static com.android.SdkConstants.ATTR_LAYOUT;
import static com.android.SdkConstants.DOT_XML;
import static com.android.SdkConstants.LAYOUT_RESOURCE_PREFIX;
import static com.android.SdkConstants.TAG_INCLUDE;
import static com.android.SdkConstants.VIEW_MERGE;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
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
                    "It's okay for two independent layouts to use the same ids. However, if "
                            + "layouts are combined with include tags, then the id's need to be unique "
                            + "within any chain of included layouts, or `Activity#findViewById()` can "
                            + "return an unexpected view.",
                    Category.CORRECTNESS,
                    7,
                    Severity.WARNING,
                    new Implementation(DuplicateIdDetector.class, Scope.ALL_RESOURCES_SCOPE));

    /** Map from layout name to the set of ids defined in that layout */
    private Map<String, Set<String>> mFileToIds;

    /** Map from layout name to the list of layouts included by that layout */
    private Map<String, List<String>> mIncludes;

    /** Current file being analyzed - set of ids */
    private Set<String> mIds;

    /** Current file being analyzed - list of includes */
    private List<String> mIncluded;

    /** Map from layout name to location of duplicate id */
    private Map<String, Location> mLocations;

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
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
    public void beforeCheckRootProject(@NonNull Context context) {
        mFileToIds = new HashMap<>();
        mIncludes = new HashMap<>();
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mIds = new HashSet<>();
        mIncluded = new ArrayList<>();
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        String name = getLayoutName(context.file);
        if (!mIds.isEmpty()) {
            mFileToIds.put(name, mIds);
        }
        if (!mIncluded.isEmpty()) {
            mIncludes.put(name, mIncluded);
        }
        mIds = null;
        mIncluded = null;
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (mIncludes.isEmpty()) {
            return;
        }

        // For each layout that has includes, check if the combined ids have duplicates
        // We need to do a graph traversal
        for (Map.Entry<String, List<String>> entry : mIncludes.entrySet()) {
            String layout = entry.getKey();
            // Find all ids reachable from this layout through includes
            Set<String> visited = new HashSet<>();
            checkLayout(context, layout, visited);
        }
    }

    private void checkLayout(
            @NonNull Context context,
            @NonNull String layout,
            @NonNull Set<String> visited) {
        if (visited.contains(layout)) {
            return;
        }
        visited.add(layout);

        List<String> includes = mIncludes.get(layout);
        if (includes == null || includes.isEmpty()) {
            return;
        }

        // Collect all ids in the current layout
        Set<String> ownIds = mFileToIds.get(layout);
        if (ownIds == null) {
            ownIds = Collections.emptySet();
        }

        // For each included layout, check for duplicate ids
        for (String included : includes) {
            Set<String> includedIds = getAllIds(included, new HashSet<>());
            if (includedIds == null || includedIds.isEmpty()) {
                continue;
            }

            // Check for overlaps between own ids and included ids
            for (String id : ownIds) {
                if (includedIds.contains(id)) {
                    // Duplicate found
                    context.report(
                            ISSUE,
                            Location.create(context.file),
                            String.format(
                                    "Duplicate id `%1$s`, defined or included multiple times in "
                                            + "layout `%2$s`: "
                                            + "[%3$s, %4$s]",
                                    id, layout, layout, included));
                }
            }

            // Also check across different included layouts
            List<String> allIncludes = new ArrayList<>(includes);
            for (int i = 0; i < allIncludes.size(); i++) {
                String inc1 = allIncludes.get(i);
                Set<String> ids1 = getAllIds(inc1, new HashSet<>());
                if (ids1 == null || ids1.isEmpty()) {
                    continue;
                }
                for (int j = i + 1; j < allIncludes.size(); j++) {
                    String inc2 = allIncludes.get(j);
                    Set<String> ids2 = getAllIds(inc2, new HashSet<>());
                    if (ids2 == null || ids2.isEmpty()) {
                        continue;
                    }
                    for (String id : ids1) {
                        if (ids2.contains(id)) {
                            context.report(
                                    ISSUE,
                                    Location.create(context.file),
                                    String.format(
                                            "Duplicate id `%1$s`, defined or included multiple "
                                                    + "times in layout `%2$s`: "
                                                    + "[%3$s, %4$s]",
                                            id, layout, inc1, inc2));
                        }
                    }
                }
            }
        }
    }

    @Nullable
    private Set<String> getAllIds(@NonNull String layout, @NonNull Set<String> visiting) {
        if (visiting.contains(layout)) {
            return Collections.emptySet();
        }
        visiting.add(layout);

        Set<String> result = new HashSet<>();
        Set<String> ownIds = mFileToIds.get(layout);
        if (ownIds != null) {
            result.addAll(ownIds);
        }

        List<String> includes = mIncludes.get(layout);
        if (includes != null) {
            for (String included : includes) {
                Set<String> childIds = getAllIds(included, visiting);
                if (childIds != null) {
                    result.addAll(childIds);
                }
            }
        }

        return result;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Handle <include> tags
        assert element.getTagName().equals(TAG_INCLUDE);
        Attr layoutAttr = element.getAttributeNode(ATTR_LAYOUT);
        if (layoutAttr != null) {
            String layout = layoutAttr.getValue();
            if (layout.startsWith(LAYOUT_RESOURCE_PREFIX)) {
                String includedLayout = layout.substring(LAYOUT_RESOURCE_PREFIX.length());
                if (mIncluded != null) {
                    mIncluded.add(includedLayout);
                }
            }
        }
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        // Handle android:id attributes
        String id = attribute.getValue();
        if (id != null && !id.isEmpty()) {
            // Strip the @+id/ or @id/ prefix
            String idValue = id;
            if (idValue.startsWith("@+id/")) {
                idValue = idValue.substring("@+id/".length());
            } else if (idValue.startsWith("@id/")) {
                idValue = idValue.substring("@id/".length());
            }
            if (mIds != null && !idValue.isEmpty()) {
                mIds.add(idValue);
            }
        }
    }

    private static String getLayoutName(@NonNull File file) {
        String name = file.getName();
        if (name.endsWith(DOT_XML)) {
            name = name.substring(0, name.length() - DOT_XML.length());
        }
        return name;
    }

    /**
     * A simple class to represent a layout node in the include graph,
     * implementing Comparable so it can be used in sorted collections.
     */
    static class LayoutNode implements Comparable<LayoutNode> {
        private final String mName;

        LayoutNode(@NonNull String name) {
            mName = name;
        }

        public String getName() {
            return mName;
        }

        @Override
        public String toString() {
            return mName;
        }

        @Override
        public int compareTo(@NonNull LayoutNode other) {
            return mName.compareTo(other.mName);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            LayoutNode that = (LayoutNode) o;
            return mName.equals(that.mName);
        }

        @Override
        public int hashCode() {
            return mName.hashCode();
        }
    }
}