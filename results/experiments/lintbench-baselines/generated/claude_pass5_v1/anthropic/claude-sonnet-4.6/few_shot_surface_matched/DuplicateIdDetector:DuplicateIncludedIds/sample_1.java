package com.android.tools.lint.checks;

import static com.android.SdkConstants.ATTR_ID;
import static com.android.SdkConstants.ATTR_LAYOUT;
import static com.android.SdkConstants.LAYOUT_RESOURCE_PREFIX;
import static com.android.SdkConstants.VIEW_INCLUDE;

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
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

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

    /** Current file being analyzed - ids collected so far */
    private Set<String> mCurrentIds;

    /** Current file being analyzed - includes collected so far */
    private List<String> mCurrentIncludes;

    /** Current file name (without extension) */
    private String mCurrentLayoutName;

    /** Location map: layout name -> id -> location */
    private Map<String, Map<String, Location>> mLocations;

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList(ATTR_ID, ATTR_LAYOUT);
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(VIEW_INCLUDE);
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mFileToIds = new HashMap<>();
        mIncludes = new HashMap<>();
        mLocations = new HashMap<>();
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        File file = context.file;
        String name = getLayoutName(file);
        mCurrentLayoutName = name;
        mCurrentIds = new HashSet<>();
        mCurrentIncludes = new ArrayList<>();
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (mCurrentLayoutName != null) {
            mFileToIds.put(mCurrentLayoutName, mCurrentIds);
            if (!mCurrentIncludes.isEmpty()) {
                mIncludes.put(mCurrentLayoutName, mCurrentIncludes);
            }
        }
        mCurrentLayoutName = null;
        mCurrentIds = null;
        mCurrentIncludes = null;
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        // Now that we have all layouts analyzed, check for duplicate ids in include chains
        if (mIncludes.isEmpty()) {
            return;
        }

        // For each layout that has includes, walk the include graph and check for duplicate ids
        // across the chain
        for (String layout : mIncludes.keySet()) {
            // Check for duplicates starting from this layout as a root
            checkLayout(context, layout, new ArrayList<>(), new HashMap<>(), new HashSet<>());
        }
    }

    private void checkLayout(
            @NonNull Context context,
            @NonNull String layout,
            @NonNull List<String> chain,
            @NonNull Map<String, String> idToLayout,
            @NonNull Set<String> visited) {

        if (visited.contains(layout)) {
            // Cycle detected, stop
            return;
        }

        visited.add(layout);
        chain.add(layout);

        Set<String> ids = mFileToIds.get(layout);
        if (ids != null) {
            for (String id : ids) {
                if (idToLayout.containsKey(id)) {
                    // Duplicate found!
                    String firstLayout = idToLayout.get(id);
                    reportDuplicate(context, id, firstLayout, layout, chain);
                } else {
                    idToLayout.put(id, layout);
                }
            }
        }

        List<String> includes = mIncludes.get(layout);
        if (includes != null) {
            for (String included : includes) {
                checkLayout(context, included, chain, idToLayout, visited);
            }
        }

        // Remove ids added by this layout when backtracking
        if (ids != null) {
            for (String id : ids) {
                if (layout.equals(idToLayout.get(id))) {
                    idToLayout.remove(id);
                }
            }
        }

        chain.remove(chain.size() - 1);
        visited.remove(layout);
    }

    private void reportDuplicate(
            @NonNull Context context,
            @NonNull String id,
            @NonNull String firstLayout,
            @NonNull String secondLayout,
            @NonNull List<String> chain) {

        Location location = null;
        Location secondary = null;

        Map<String, Location> secondLocations = mLocations.get(secondLayout);
        if (secondLocations != null) {
            location = secondLocations.get(id);
        }

        Map<String, Location> firstLocations = mLocations.get(firstLayout);
        if (firstLocations != null) {
            secondary = firstLocations.get(id);
        }

        if (location == null) {
            return;
        }

        String message =
                String.format(
                        "Duplicate id `%1$s`, defined in layout `%2$s`, included from layout `%3$s`",
                        id, secondLayout, firstLayout);

        if (secondary != null) {
            secondary.setMessage(
                    String.format("Defined here in `%1$s`", firstLayout));
            location.setSecondary(secondary);
        }

        context.report(ISSUE, location, message);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Handle <include> elements - the layout attribute is handled in visitAttribute
        // This is a fallback in case the layout attribute wasn't picked up
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String name = attribute.getLocalName();
        if (name == null) {
            name = attribute.getName();
        }

        if (ATTR_ID.equals(name)) {
            String id = attribute.getValue();
            if (id != null && !id.isEmpty()) {
                // Strip @+id/ or @id/ prefix for comparison
                String strippedId = stripIdPrefix(id);
                if (mCurrentIds != null) {
                    mCurrentIds.add(strippedId);
                }

                // Store location for later reporting
                if (mCurrentLayoutName != null) {
                    Map<String, Location> locationMap =
                            mLocations.computeIfAbsent(mCurrentLayoutName, k -> new HashMap<>());
                    if (!locationMap.containsKey(strippedId)) {
                        locationMap.put(strippedId, context.getLocation(attribute));
                    }
                }
            }
        } else if (ATTR_LAYOUT.equals(name)) {
            // This is a layout attribute on an include element
            Element owner = attribute.getOwnerElement();
            if (VIEW_INCLUDE.equals(owner.getTagName()) || VIEW_INCLUDE.equals(owner.getNodeName())) {
                String layoutRef = attribute.getValue();
                if (layoutRef != null && layoutRef.startsWith(LAYOUT_RESOURCE_PREFIX)) {
                    String includedLayout = layoutRef.substring(LAYOUT_RESOURCE_PREFIX.length());
                    if (mCurrentIncludes != null) {
                        mCurrentIncludes.add(includedLayout);
                    }
                }
            }
        }
    }

    @NonNull
    private static String stripIdPrefix(@NonNull String id) {
        if (id.startsWith("@+id/")) {
            return id.substring("@+id/".length());
        } else if (id.startsWith("@id/")) {
            return id.substring("@id/".length());
        }
        return id;
    }

    @NonNull
    private static String getLayoutName(@NonNull File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        if (dot != -1) {
            name = name.substring(0, dot);
        }
        return name;
    }

    @Override
    public String toString() {
        return "DuplicateIdDetector";
    }

    public int compareTo(@NonNull DuplicateIdDetector other) {
        return toString().compareTo(other.toString());
    }
}