package com.android.tools.lint.checks;

import static com.android.SdkConstants.ATTR_ID;
import static com.android.SdkConstants.ATTR_LAYOUT;
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
                    "DuplicateIds",
                    "Duplicate ids within a single layout",
                    "Within a layout, id's should be unique since otherwise `findViewById()` can"
                            + " return an unexpected view.",
                    Category.CORRECTNESS,
                    7,
                    Severity.FATAL,
                    new Implementation(DuplicateIdDetector.class, Scope.RESOURCE_FILE_SCOPE));

    /** Map from ID to the first location where it was encountered in the current file */
    private Map<String, Location> mFileIds;

    /** Map from include tag to included layout */
    private Map<Element, String> mIncludes;

    /**
     * Map from layout name to the set of ids declared in that layout (including
     * transitively included layouts)
     */
    private Map<String, Set<String>> mLayoutToIds;

    /** Map from layout name to a list of ids that are duplicated across includes */
    private Map<String, List<Location.Handle>> mLayoutToDuplicates;

    public DuplicateIdDetector() {
    }

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
        mFileIds = new HashMap<>();
        mIncludes = new HashMap<>();
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        // Store the ids for this layout file so we can check cross-layout duplicates later
        if (mLayoutToIds == null) {
            mLayoutToIds = new HashMap<>();
        }

        String layoutName = getLayoutName(context.file);
        if (layoutName != null) {
            Set<String> ids = new HashSet<>(mFileIds.keySet());
            mLayoutToIds.put(layoutName, ids);
        }

        mFileIds = null;
        mIncludes = null;
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mLayoutToIds = new HashMap<>();
        mLayoutToDuplicates = new HashMap<>();
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        // Nothing to do here for the basic duplicate-within-file check.
        // Cross-file include checks would be handled here if needed.
        mLayoutToIds = null;
        mLayoutToDuplicates = null;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Handle <include> tags — record the included layout
        if (VIEW_INCLUDE.equals(element.getTagName())) {
            String layout = element.getAttribute(ATTR_LAYOUT);
            if (layout != null && !layout.isEmpty()) {
                if (mIncludes != null) {
                    mIncludes.put(element, layout);
                }
            }
        }
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String id = attribute.getValue();
        if (id == null || id.isEmpty()) {
            return;
        }

        if (mFileIds == null) {
            return;
        }

        if (mFileIds.containsKey(id)) {
            // Duplicate found within the same file
            Location location = context.getLocation(attribute);
            Location previousLocation = mFileIds.get(id);

            // Link the two locations together
            if (previousLocation != null) {
                location.setSecondary(previousLocation);
                previousLocation.setMessage("Duplicate id `" + id + "` originally defined here");
            }

            context.report(
                    ISSUE,
                    attribute,
                    location,
                    "Duplicate id `" + id + "`, already defined earlier in this layout");
        } else {
            mFileIds.put(id, context.getLocation(attribute));
        }
    }

    @Nullable
    private static String getLayoutName(@NonNull File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        if (dot != -1) {
            return name.substring(0, dot);
        }
        return name;
    }

    /**
     * A location handle for tracking duplicate ids across included layouts.
     * Implements Comparable to allow sorting by id name.
     */
    static class Occurrence implements Comparable<Occurrence> {
        final String mId;
        final Location mLocation;
        final String mLayout;

        Occurrence(@NonNull String id, @NonNull Location location, @NonNull String layout) {
            mId = id;
            mLocation = location;
            mLayout = layout;
        }

        @Override
        public String toString() {
            return mId + " in " + mLayout + " at " + mLocation;
        }

        @Override
        public int compareTo(@NonNull Occurrence other) {
            int result = mId.compareTo(other.mId);
            if (result != 0) {
                return result;
            }
            result = mLayout.compareTo(other.mLayout);
            if (result != 0) {
                return result;
            }
            return toString().compareTo(other.toString());
        }
    }
}