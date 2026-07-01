package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
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

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_ID;
import static com.android.SdkConstants.ATTR_LAYOUT;
import static com.android.SdkConstants.VIEW_INCLUDE;

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

    /** Map from layout name to set of ids defined in that layout */
    private Map<String, Set<String>> mFileToIds;

    /** Map from layout name to list of layouts included by that layout */
    private Map<String, List<String>> mIncludes;

    /** Current file being processed */
    private String mCurrentLayout;

    /** Ids defined in the current file */
    private Set<String> mCurrentIds;

    /** Includes in the current file */
    private List<String> mCurrentIncludes;

    /** Map from layout name to location of the include tag */
    private Map<String, Map<String, Location>> mIncludeLocations;

    /** Map from layout name to map of id to location */
    private Map<String, Map<String, Location>> mIdLocations;

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
        String name = file.getName();
        // Strip the .xml extension
        if (name.endsWith(".xml")) {
            name = name.substring(0, name.length() - 4);
        }
        mCurrentLayout = name;
        mCurrentIds = new HashSet<>();
        mCurrentIncludes = new ArrayList<>();

        if (mFileToIds == null) {
            mFileToIds = new HashMap<>();
        }
        if (mIncludes == null) {
            mIncludes = new HashMap<>();
        }
        if (mIncludeLocations == null) {
            mIncludeLocations = new HashMap<>();
        }
        if (mIdLocations == null) {
            mIdLocations = new HashMap<>();
        }
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (mCurrentLayout != null) {
            mFileToIds.put(mCurrentLayout, mCurrentIds);
            if (!mCurrentIncludes.isEmpty()) {
                mIncludes.put(mCurrentLayout, mCurrentIncludes);
            }
        }
        mCurrentLayout = null;
        mCurrentIds = null;
        mCurrentIncludes = null;
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mFileToIds = new HashMap<>();
        mIncludes = new HashMap<>();
        mIncludeLocations = new HashMap<>();
        mIdLocations = new HashMap<>();
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        // Now analyze the include chains to find duplicate ids
        // For each layout that has includes, check if there are duplicate ids
        // across the included layouts and the parent layout

        // We need to find all layouts that include other layouts and check
        // for duplicate ids in the combined set
        for (Map.Entry<String, List<String>> entry : mIncludes.entrySet()) {
            String layout = entry.getKey();
            List<String> includes = entry.getValue();

            // Collect all ids reachable from this layout via includes
            Set<String> visited = new HashSet<>();
            checkForDuplicates(context, layout, visited);
        }
    }

    private void checkForDuplicates(Context context, String layout, Set<String> visited) {
        if (visited.contains(layout)) {
            return;
        }
        visited.add(layout);

        List<String> includes = mIncludes.get(layout);
        if (includes == null || includes.isEmpty()) {
            return;
        }

        // Get ids from this layout
        Set<String> layoutIds = mFileToIds.get(layout);
        if (layoutIds == null) {
            layoutIds = Collections.emptySet();
        }

        // For each included layout, check for duplicate ids
        for (String included : includes) {
            Set<String> includedIds = getAllIds(included, new HashSet<>());

            // Check for duplicates between the parent layout ids and included ids
            for (String id : includedIds) {
                if (layoutIds.contains(id)) {
                    // Found a duplicate!
                    reportDuplicate(context, layout, included, id);
                }
            }

            // Also check for duplicates among multiple included layouts
            for (String otherIncluded : includes) {
                if (otherIncluded.equals(included)) {
                    continue;
                }
                Set<String> otherIds = getAllIds(otherIncluded, new HashSet<>());
                for (String id : includedIds) {
                    if (otherIds.contains(id) && !layoutIds.contains(id)) {
                        // Duplicate between two included layouts
                        reportDuplicateInclude(context, layout, included, otherIncluded, id);
                    }
                }
            }

            // Recurse
            checkForDuplicates(context, included, visited);
        }
    }

    private Set<String> getAllIds(String layout, Set<String> visited) {
        if (visited.contains(layout)) {
            return Collections.emptySet();
        }
        visited.add(layout);

        Set<String> ids = new HashSet<>();
        Set<String> ownIds = mFileToIds.get(layout);
        if (ownIds != null) {
            ids.addAll(ownIds);
        }

        List<String> includes = mIncludes.get(layout);
        if (includes != null) {
            for (String included : includes) {
                ids.addAll(getAllIds(included, visited));
            }
        }

        return ids;
    }

    private void reportDuplicate(Context context, String layout, String included, String id) {
        // Try to get location information
        Map<String, Location> idLocs = mIdLocations.get(layout);
        Location location = null;
        if (idLocs != null) {
            location = idLocs.get(id);
        }

        String message = String.format(
                "Duplicate id `%1$s`, defined or included multiple times in `%2$s.xml`: "
                        + "[`%2$s.xml`, `%3$s.xml`]",
                id, layout, included);

        if (location != null) {
            context.report(ISSUE, location, message);
        } else {
            // Try to find any location
            Map<String, Location> includedIdLocs = mIdLocations.get(included);
            if (includedIdLocs != null) {
                location = includedIdLocs.get(id);
            }
            if (location != null) {
                context.report(ISSUE, location, message);
            }
        }
    }

    private void reportDuplicateInclude(Context context, String layout,
            String included1, String included2, String id) {
        Map<String, Location> idLocs1 = mIdLocations.get(included1);
        Location location = null;
        if (idLocs1 != null) {
            location = idLocs1.get(id);
        }

        String message = String.format(
                "Duplicate id `%1$s`, defined or included multiple times in `%2$s.xml`: "
                        + "[`%3$s.xml`, `%4$s.xml`]",
                id, layout, included1, included2);

        if (location != null) {
            context.report(ISSUE, location, message);
        } else {
            Map<String, Location> idLocs2 = mIdLocations.get(included2);
            if (idLocs2 != null) {
                location = idLocs2.get(id);
            }
            if (location != null) {
                context.report(ISSUE, location, message);
            }
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Handle <include> tags
        String layout = element.getAttribute(ATTR_LAYOUT);
        if (layout != null && !layout.isEmpty()) {
            // Strip the @layout/ prefix
            if (layout.startsWith("@layout/")) {
                layout = layout.substring("@layout/".length());
            }
            if (mCurrentIncludes != null) {
                mCurrentIncludes.add(layout);
            }

            // Store include location
            if (mCurrentLayout != null) {
                Map<String, Location> locMap = mIncludeLocations.get(mCurrentLayout);
                if (locMap == null) {
                    locMap = new HashMap<>();
                    mIncludeLocations.put(mCurrentLayout, locMap);
                }
                locMap.put(layout, context.getLocation(element));
            }
        }
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        // Handle android:id attributes
        if (ANDROID_URI.equals(attribute.getNamespaceURI())
                && ATTR_ID.equals(attribute.getLocalName())) {
            String id = attribute.getValue();
            if (id != null && !id.isEmpty()) {
                // Strip @+id/ or @id/ prefix
                if (id.startsWith("@+id/")) {
                    id = id.substring("@+id/".length());
                } else if (id.startsWith("@id/")) {
                    id = id.substring("@id/".length());
                }

                if (mCurrentIds != null) {
                    mCurrentIds.add(id);
                }

                // Store location for error reporting
                if (mCurrentLayout != null) {
                    Map<String, Location> locMap = mIdLocations.get(mCurrentLayout);
                    if (locMap == null) {
                        locMap = new HashMap<>();
                        mIdLocations.put(mCurrentLayout, locMap);
                    }
                    if (!locMap.containsKey(id)) {
                        locMap.put(id, context.getLocation(attribute));
                    }
                }
            }
        }
    }

    /**
     * Returns a string representation of the given layout and its id set.
     *
     * @param layout the layout name
     * @param ids the set of ids
     * @return a string representation
     */
    public String toString(String layout, Set<String> ids) {
        return layout + ": " + ids;
    }

    /**
     * Compares two layout names for ordering purposes.
     *
     * @param layout1 the first layout name
     * @param layout2 the second layout name
     * @return comparison result
     */
    public int compareTo(String layout1, String layout2) {
        return layout1.compareTo(layout2);
    }
}