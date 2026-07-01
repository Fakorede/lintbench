package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LintClient;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class DuplicateIdDetector extends LayoutDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(DuplicateIdDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "DuplicateIncludedIds",
                    "Duplicate ids across layouts combined with include tags",
                    "It's okay for two independent layouts to use the same ids. However, if "
                            + "layouts are combined with include tags, then the id's need to be unique "
                            + "within any chain of included layouts, or `Activity#findViewById()` can "
                            + "return an unexpected view.",
                    Category.CORRECTNESS,
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION);

    /** Map from layout name to the set of ids defined in that layout */
    private Map<String, Set<String>> mFileToIds;

    /** Map from layout name to list of layouts included by that layout */
    private Map<String, List<String>> mIncludes;

    /** Current file being analyzed */
    private String mCurrentFile;

    /** Ids defined in the current file */
    private Set<String> mCurrentIds;

    /** Includes in the current file */
    private List<String> mCurrentIncludes;

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
        return Collections.singletonList(VIEW_INCLUDE);
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        File file = context.file;
        mCurrentFile = getLayoutName(file);
        mCurrentIds = new HashSet<>();
        mCurrentIncludes = new ArrayList<>();
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (mCurrentFile != null) {
            if (mFileToIds == null) {
                mFileToIds = new HashMap<>();
            }
            if (mIncludes == null) {
                mIncludes = new HashMap<>();
            }
            mFileToIds.put(mCurrentFile, mCurrentIds);
            if (!mCurrentIncludes.isEmpty()) {
                mIncludes.put(mCurrentFile, mCurrentIncludes);
            }
        }
        mCurrentFile = null;
        mCurrentIds = null;
        mCurrentIncludes = null;
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mFileToIds = new HashMap<>();
        mIncludes = new HashMap<>();
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (mIncludes == null || mIncludes.isEmpty()) {
            return;
        }

        // For each layout that has includes, check for duplicate ids in the include chain
        for (Map.Entry<String, List<String>> entry : mIncludes.entrySet()) {
            String layout = entry.getKey();
            // Collect all ids reachable from this layout through includes
            Set<String> visited = new HashSet<>();
            checkForDuplicates(context, layout, visited);
        }
    }

    private void checkForDuplicates(
            @NonNull Context context, @NonNull String layout, @NonNull Set<String> visited) {
        if (visited.contains(layout)) {
            return;
        }
        visited.add(layout);

        List<String> includes = mIncludes.get(layout);
        if (includes == null || includes.isEmpty()) {
            return;
        }

        // Gather all ids in this layout
        Set<String> ownIds = mFileToIds.get(layout);
        if (ownIds == null) {
            ownIds = Collections.emptySet();
        }

        // Check each included layout against all other ids in the chain
        for (String included : includes) {
            Set<String> includedIds = collectIds(included, new HashSet<>());
            // Check for overlap between own ids and included ids
            for (String id : includedIds) {
                if (ownIds.contains(id)) {
                    // Duplicate found between layout and included layout
                    // We report this at the project level since we don't have location info here
                    context.report(
                            ISSUE,
                            Location.create(context.file),
                            String.format(
                                    "Duplicate id `%1$s` in layout `%2$s` and its included layout `%3$s`",
                                    id, layout, included));
                }
            }
        }

        // Also check for duplicates among included layouts themselves
        if (includes.size() > 1) {
            Map<String, String> seenIds = new HashMap<>();
            for (String included : includes) {
                Set<String> includedIds = collectIds(included, new HashSet<>());
                for (String id : includedIds) {
                    if (seenIds.containsKey(id)) {
                        context.report(
                                ISSUE,
                                Location.create(context.file),
                                String.format(
                                        "Duplicate id `%1$s` in included layouts `%2$s` and `%3$s` in layout `%4$s`",
                                        id, seenIds.get(id), included, layout));
                    } else {
                        seenIds.put(id, included);
                    }
                }
            }
        }

        // Recurse into included layouts
        for (String included : includes) {
            checkForDuplicates(context, included, visited);
        }
    }

    /**
     * Collects all ids defined in the given layout and all layouts it transitively includes.
     */
    private Set<String> collectIds(@NonNull String layout, @NonNull Set<String> visitedLayouts) {
        if (visitedLayouts.contains(layout)) {
            return Collections.emptySet();
        }
        visitedLayouts.add(layout);

        Set<String> result = new HashSet<>();
        Set<String> ownIds = mFileToIds.get(layout);
        if (ownIds != null) {
            result.addAll(ownIds);
        }

        List<String> includes = mIncludes.get(layout);
        if (includes != null) {
            for (String included : includes) {
                result.addAll(collectIds(included, visitedLayouts));
            }
        }

        return result;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Handle <include> tags
        String layout = element.getAttribute(ATTR_LAYOUT);
        if (layout != null && !layout.isEmpty()) {
            // Strip @layout/ prefix
            String includedLayout = stripLayoutPrefix(layout);
            if (includedLayout != null && mCurrentIncludes != null) {
                mCurrentIncludes.add(includedLayout);
            }
        }
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        // Handle android:id attributes
        String value = attribute.getValue();
        if (value != null && !value.isEmpty()) {
            String id = stripIdPrefix(value);
            if (id != null && mCurrentIds != null) {
                if (!mCurrentIds.add(id)) {
                    // Duplicate id within the same file
                    context.report(
                            ISSUE,
                            attribute,
                            context.getLocation(attribute),
                            String.format("Duplicate id `%1$s` in layout", value));
                }
            }
        }
    }

    /**
     * Strips the @+id/ or @id/ prefix from an id reference.
     */
    @Nullable
    private static String stripIdPrefix(@NonNull String id) {
        if (id.startsWith("@+id/")) {
            return id.substring("@+id/".length());
        } else if (id.startsWith("@id/")) {
            return id.substring("@id/".length());
        }
        return null;
    }

    /**
     * Strips the @layout/ prefix from a layout reference.
     */
    @Nullable
    private static String stripLayoutPrefix(@NonNull String layout) {
        if (layout.startsWith("@layout/")) {
            return layout.substring("@layout/".length());
        }
        return null;
    }

    /**
     * Returns the layout name (without extension) from a file.
     */
    @NonNull
    private static String getLayoutName(@NonNull File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        if (dot != -1) {
            return name.substring(0, dot);
        }
        return name;
    }

    /**
     * Returns a string representation of the layout include graph for debugging.
     */
    public String toString(String layout) {
        if (mFileToIds == null) {
            return "<no data>";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Layout: ").append(layout).append("\n");
        Set<String> ids = mFileToIds.get(layout);
        if (ids != null) {
            sb.append("  IDs: ").append(ids).append("\n");
        }
        List<String> includes = mIncludes != null ? mIncludes.get(layout) : null;
        if (includes != null) {
            sb.append("  Includes: ").append(includes).append("\n");
        }
        return sb.toString();
    }

    /**
     * Compares two layout names for ordering purposes.
     */
    public int compareTo(String layout1, String layout2) {
        if (layout1 == null && layout2 == null) {
            return 0;
        }
        if (layout1 == null) {
            return -1;
        }
        if (layout2 == null) {
            return 1;
        }
        return layout1.compareTo(layout2);
    }
}