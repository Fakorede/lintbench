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

    /** Map from layout file name (without extension) to set of ids defined in that layout */
    private Map<String, Set<String>> mFileToIds;

    /** Map from layout file name (without extension) to list of layouts it includes */
    private Map<String, List<String>> mIncludes;

    /** Current file being processed */
    private String mCurrentFile;

    /** Ids defined in the current file */
    private Set<String> mCurrentIds;

    /** Layouts included in the current file */
    private List<String> mCurrentIncludes;

    /** Locations for ids in the current file */
    private Map<String, Location> mCurrentIdLocations;

    /** Map from layout file to map of id to location */
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
        return Collections.singletonList(VIEW_INCLUDE);
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        File file = context.file;
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        if (dot != -1) {
            name = name.substring(0, dot);
        }
        mCurrentFile = name;
        mCurrentIds = new HashSet<>();
        mCurrentIncludes = new ArrayList<>();
        mCurrentIdLocations = new HashMap<>();
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
        mFileToIdLocations.put(mCurrentFile, mCurrentIdLocations);

        mCurrentFile = null;
        mCurrentIds = null;
        mCurrentIncludes = null;
        mCurrentIdLocations = null;
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

        // For each layout that has includes, check if any included layout has duplicate ids
        // with the including layout or with other included layouts
        for (Map.Entry<String, List<String>> entry : mIncludes.entrySet()) {
            String layout = entry.getKey();
            List<String> includes = entry.getValue();

            // Collect all ids reachable from this layout
            Set<String> visited = new HashSet<>();
            checkForDuplicates(context, layout, visited);
        }
    }

    private void checkForDuplicates(
            @NonNull Context context, String layout, Set<String> visited) {
        if (visited.contains(layout)) {
            return;
        }
        visited.add(layout);

        List<String> includes = mIncludes.get(layout);
        if (includes == null || includes.isEmpty()) {
            return;
        }

        // Get ids from the including layout
        Set<String> layoutIds = mFileToIds.get(layout);
        if (layoutIds == null) {
            layoutIds = Collections.emptySet();
        }

        // For each include, check for duplicate ids
        for (String included : includes) {
            Set<String> includedIds = mFileToIds.get(included);
            if (includedIds == null) {
                continue;
            }

            // Check for duplicates between the including layout and the included layout
            for (String id : includedIds) {
                if (layoutIds.contains(id)) {
                    // Found a duplicate id
                    Map<String, Location> includingLocations = mFileToIdLocations.get(layout);
                    Map<String, Location> includedLocations = mFileToIdLocations.get(included);

                    Location location = null;
                    Location secondary = null;

                    if (includingLocations != null) {
                        location = includingLocations.get(id);
                    }
                    if (includedLocations != null) {
                        secondary = includedLocations.get(id);
                    }

                    if (location != null && secondary != null) {
                        secondary.setMessage("Duplicate id @+id/" + id + " defined here");
                        location.setSecondary(secondary);
                    } else if (secondary != null) {
                        location = secondary;
                    }

                    if (location != null) {
                        context.report(
                                ISSUE,
                                location,
                                String.format(
                                        "Duplicate id `@+id/%1$s`, defined or included multiple "
                                                + "times in `%2$s.xml`: [%3$s, %4$s]",
                                        id, layout, layout, included));
                    }
                }
            }

            // Also check for duplicates among multiple includes within the same layout
            for (String otherIncluded : includes) {
                if (otherIncluded.equals(included)) {
                    continue;
                }
                Set<String> otherIds = mFileToIds.get(otherIncluded);
                if (otherIds == null) {
                    continue;
                }
                // Only report once per pair (use string comparison to avoid double reporting)
                if (included.compareTo(otherIncluded) >= 0) {
                    continue;
                }
                for (String id : includedIds) {
                    if (otherIds.contains(id)) {
                        Map<String, Location> includedLocations = mFileToIdLocations.get(included);
                        Map<String, Location> otherLocations = mFileToIdLocations.get(otherIncluded);

                        Location location = null;
                        Location secondary = null;

                        if (includedLocations != null) {
                            location = includedLocations.get(id);
                        }
                        if (otherLocations != null) {
                            secondary = otherLocations.get(id);
                        }

                        if (location != null && secondary != null) {
                            secondary.setMessage("Duplicate id @+id/" + id + " defined here");
                            location.setSecondary(secondary);
                        } else if (secondary != null) {
                            location = secondary;
                        }

                        if (location != null) {
                            context.report(
                                    ISSUE,
                                    location,
                                    String.format(
                                            "Duplicate id `@+id/%1$s`, defined or included multiple "
                                                    + "times in `%2$s.xml`: [%3$s, %4$s]",
                                            id, layout, included, otherIncluded));
                        }
                    }
                }
            }
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Handle <include> elements
        Attr layoutAttr = element.getAttributeNode(ATTR_LAYOUT);
        if (layoutAttr != null) {
            String layoutValue = layoutAttr.getValue();
            // Strip @layout/ prefix
            if (layoutValue.startsWith("@layout/")) {
                String includedLayout = layoutValue.substring("@layout/".length());
                if (mCurrentIncludes != null) {
                    mCurrentIncludes.add(includedLayout);
                }
            }
        }
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        // Handle android:id attributes
        if (ANDROID_URI.equals(attribute.getNamespaceURI())
                && ATTR_ID.equals(attribute.getLocalName())) {
            String id = attribute.getValue();
            // Normalize id: strip @+id/ or @id/ prefix
            if (id.startsWith("@+id/")) {
                id = id.substring("@+id/".length());
            } else if (id.startsWith("@id/")) {
                id = id.substring("@id/".length());
            }

            if (mCurrentIds != null) {
                mCurrentIds.add(id);
            }
            if (mCurrentIdLocations != null) {
                mCurrentIdLocations.put(id, context.getLocation(attribute));
            }
        }
    }

    /**
     * Returns a string representation of this detector (for debugging).
     */
    public String toString(String layout) {
        if (mFileToIds != null) {
            Set<String> ids = mFileToIds.get(layout);
            if (ids != null) {
                List<String> sorted = new ArrayList<>(ids);
                Collections.sort(sorted);
                return layout + ": " + sorted.toString();
            }
        }
        return layout + ": []";
    }

    /**
     * Compares two layout names for ordering purposes.
     */
    public int compareTo(String layout1, String layout2) {
        return layout1.compareTo(layout2);
    }
}