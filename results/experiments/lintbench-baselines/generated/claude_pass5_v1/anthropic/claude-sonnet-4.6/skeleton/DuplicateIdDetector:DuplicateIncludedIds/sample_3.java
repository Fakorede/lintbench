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
import static com.android.SdkConstants.TAG_INCLUDE;

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

    /** Current file being analyzed */
    private String mCurrentFile;

    /** Ids defined in the current file */
    private Set<String> mCurrentIds;

    /** Includes in the current file */
    private List<String> mCurrentIncludes;

    /** Map from id to location for the current file */
    private Map<String, Location> mIdToLocation;

    /** Map from layout name to map of id to location */
    private Map<String, Map<String, Location>> mFileToIdLocations;

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
    public void beforeCheckFile(@NonNull Context context) {
        File file = context.file;
        mCurrentFile = getLayoutName(file);
        mCurrentIds = new HashSet<>();
        mCurrentIncludes = new ArrayList<>();
        mIdToLocation = new HashMap<>();
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (mFileToIds == null) {
            mFileToIds = new HashMap<>();
        }
        if (mIncludes == null) {
            mIncludes = new HashMap<>();
        }
        if (mFileToIdLocations == null) {
            mFileToIdLocations = new HashMap<>();
        }

        mFileToIds.put(mCurrentFile, mCurrentIds);
        if (!mCurrentIncludes.isEmpty()) {
            mIncludes.put(mCurrentFile, mCurrentIncludes);
        }
        mFileToIdLocations.put(mCurrentFile, mIdToLocation);

        mCurrentFile = null;
        mCurrentIds = null;
        mCurrentIncludes = null;
        mIdToLocation = null;
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mFileToIds = new HashMap<>();
        mIncludes = new HashMap<>();
        mFileToIdLocations = new HashMap<>();
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (mIncludes == null || mIncludes.isEmpty()) {
            return;
        }

        // For each layout that includes other layouts, check for duplicate ids
        // in the chain of includes
        for (Map.Entry<String, List<String>> entry : mIncludes.entrySet()) {
            String layout = entry.getKey();
            List<String> includes = entry.getValue();

            // Collect all ids reachable from this layout via includes
            Set<String> visited = new HashSet<>();
            checkLayout(context, layout, visited);
        }
    }

    /**
     * Check a layout and all its included layouts for duplicate ids.
     */
    private void checkLayout(@NonNull Context context, String layout, Set<String> visited) {
        if (visited.contains(layout)) {
            return;
        }
        visited.add(layout);

        List<String> includes = mIncludes.get(layout);
        if (includes == null || includes.isEmpty()) {
            return;
        }

        // Collect ids from this layout and all directly/transitively included layouts
        // Check for duplicates
        Set<String> idsInLayout = mFileToIds.get(layout);
        if (idsInLayout == null) {
            idsInLayout = Collections.emptySet();
        }

        // For each include, gather all ids from included layout (transitively)
        // and check for overlap with our own ids and other included layouts
        List<String> allIncludes = getAllIncludes(layout, new HashSet<>());

        // Build a map from id -> list of layouts that define it
        Map<String, List<String>> idToLayouts = new HashMap<>();

        // Add ids from the root layout
        for (String id : idsInLayout) {
            List<String> layouts = new ArrayList<>();
            layouts.add(layout);
            idToLayouts.put(id, layouts);
        }

        // Add ids from all included layouts
        for (String included : allIncludes) {
            Set<String> includedIds = mFileToIds.get(included);
            if (includedIds == null) {
                continue;
            }
            for (String id : includedIds) {
                List<String> layouts = idToLayouts.get(id);
                if (layouts == null) {
                    layouts = new ArrayList<>();
                    idToLayouts.put(id, layouts);
                }
                layouts.add(included);
            }
        }

        // Report duplicates
        for (Map.Entry<String, List<String>> entry : idToLayouts.entrySet()) {
            String id = entry.getKey();
            List<String> layoutsWithId = entry.getValue();
            if (layoutsWithId.size() > 1) {
                // Found duplicate id across included layouts
                // Find a location to report
                Location location = null;
                Map<String, Location> rootLocations = mFileToIdLocations.get(layout);
                if (rootLocations != null) {
                    location = rootLocations.get(id);
                }

                // Build secondary locations
                Location secondaryLocation = null;
                for (int i = layoutsWithId.size() - 1; i >= 0; i--) {
                    String layoutWithId = layoutsWithId.get(i);
                    Map<String, Location> locs = mFileToIdLocations.get(layoutWithId);
                    if (locs != null) {
                        Location loc = locs.get(id);
                        if (loc != null) {
                            if (location == null) {
                                location = loc;
                            } else if (!layoutWithId.equals(layout)) {
                                loc.setMessage("Duplicate id @+id/" + id + " in layout " + layoutWithId);
                                if (secondaryLocation == null) {
                                    secondaryLocation = loc;
                                } else {
                                    loc.setSecondary(secondaryLocation);
                                    secondaryLocation = loc;
                                }
                            }
                        }
                    }
                }

                if (location != null && secondaryLocation != null) {
                    location.setSecondary(secondaryLocation);
                }

                if (location != null) {
                    StringBuilder sb = new StringBuilder();
                    sb.append("Duplicate id @+id/").append(id).append(", defined or included in ");
                    sb.append(layout);
                    sb.append(" and in ");
                    boolean first = true;
                    for (String l : layoutsWithId) {
                        if (!l.equals(layout)) {
                            if (!first) {
                                sb.append(", ");
                            }
                            sb.append(l);
                            first = false;
                        }
                    }

                    context.report(ISSUE, location, sb.toString());
                }
            }
        }
    }

    /**
     * Get all layouts transitively included by the given layout.
     */
    private List<String> getAllIncludes(String layout, Set<String> visited) {
        if (visited.contains(layout)) {
            return Collections.emptyList();
        }
        visited.add(layout);

        List<String> result = new ArrayList<>();
        List<String> directIncludes = mIncludes.get(layout);
        if (directIncludes != null) {
            for (String included : directIncludes) {
                result.add(included);
                result.addAll(getAllIncludes(included, visited));
            }
        }
        return result;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Handle <include> tags
        if (TAG_INCLUDE.equals(element.getTagName())) {
            String layout = element.getAttribute(ATTR_LAYOUT);
            if (layout != null && layout.startsWith("@layout/")) {
                String includedLayout = layout.substring("@layout/".length());
                if (mCurrentIncludes != null) {
                    mCurrentIncludes.add(includedLayout);
                }
            }
        }
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        // Handle android:id attributes
        if (ANDROID_URI.equals(attribute.getNamespaceURI()) && ATTR_ID.equals(attribute.getLocalName())) {
            String id = attribute.getValue();
            if (id != null) {
                // Normalize the id: strip @+id/ or @id/ prefix
                String normalizedId = normalizeId(id);
                if (normalizedId != null && mCurrentIds != null) {
                    mCurrentIds.add(normalizedId);
                    if (mIdToLocation != null) {
                        mIdToLocation.put(normalizedId, context.getLocation(attribute));
                    }
                }
            }
        }
    }

    /**
     * Normalize an id by stripping the @+id/ or @id/ prefix.
     */
    private static String normalizeId(String id) {
        if (id.startsWith("@+id/")) {
            return id.substring("@+id/".length());
        } else if (id.startsWith("@id/")) {
            return id.substring("@id/".length());
        }
        return id;
    }

    /**
     * Get the layout name from a file (without extension).
     */
    private static String getLayoutName(File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        if (dot != -1) {
            name = name.substring(0, dot);
        }
        return name;
    }

    /**
     * Returns a string representation of this detector (for debugging).
     */
    public String toString(String layout) {
        if (mFileToIds != null) {
            Set<String> ids = mFileToIds.get(layout);
            if (ids != null) {
                return layout + ": " + ids.toString();
            }
        }
        return layout + ": (no ids)";
    }

    /**
     * Compares two layout names lexicographically.
     */
    public int compareTo(String layout1, String layout2) {
        return layout1.compareTo(layout2);
    }
}