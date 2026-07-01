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
                    "Within a layout, id's should be unique since otherwise"
                            + " `findViewById()` can return an unexpected view.",
                    Category.CORRECTNESS,
                    7,
                    Severity.FATAL,
                    new Implementation(DuplicateIdDetector.class, Scope.RESOURCE_FILE_SCOPE));

    public static final Issue CROSS_LAYOUT =
            Issue.create(
                    "DuplicateIncludedIds",
                    "Duplicate ids across layouts combined with include tags",
                    "It's okay for two independent layouts to use the same ids. However, if"
                            + " layouts are combined with include tags, then the ids need to be"
                            + " unique within the combined layout.",
                    Category.CORRECTNESS,
                    6,
                    Severity.WARNING,
                    new Implementation(DuplicateIdDetector.class, Scope.ALL_RESOURCES_SCOPE));

    /** Map from layout file to the ids defined in that layout */
    private Map<File, Set<String>> mFileToIds;

    /** Map from layout file to the list of included layouts */
    private Map<File, List<String>> mIncludes;

    /** Current file's id map */
    private Map<String, Location> mIds;

    /** Current file's list of included layouts */
    private List<String> mIncluded;

    /** Locations of duplicate ids in the current file */
    private Map<String, List<Location>> mDuplicates;

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
        mIds = new HashMap<>();
        mDuplicates = null;
        mIncluded = null;
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        // Store results for cross-layout analysis
        if (mFileToIds != null) {
            mFileToIds.put(context.file, new HashSet<>(mIds.keySet()));
        }
        if (mIncludes != null && mIncluded != null && !mIncluded.isEmpty()) {
            mIncludes.put(context.file, mIncluded);
        }

        // Report duplicates within this file
        if (mDuplicates != null) {
            for (Map.Entry<String, List<Location>> entry : mDuplicates.entrySet()) {
                String id = entry.getKey();
                List<Location> locations = entry.getValue();

                // Build a chained location list
                Location primary = null;
                for (int i = locations.size() - 1; i >= 0; i--) {
                    Location loc = locations.get(i);
                    if (primary == null) {
                        primary = loc;
                    } else {
                        loc.setSecondary(primary);
                        primary = loc;
                    }
                }

                XmlContext xmlContext = (XmlContext) context;
                xmlContext.report(
                        ISSUE,
                        primary,
                        String.format("Duplicate id `%1$s`, already defined earlier in this layout", id));
            }
        }

        mIds = null;
        mDuplicates = null;
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mFileToIds = new HashMap<>();
        mIncludes = new HashMap<>();
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        // Cross-layout duplicate id check via includes
        if (mIncludes.isEmpty()) {
            mFileToIds = null;
            mIncludes = null;
            return;
        }

        // For each file that includes other layouts, check for id conflicts
        // Build a graph and detect conflicts
        for (Map.Entry<File, List<String>> entry : mIncludes.entrySet()) {
            File file = entry.getKey();
            List<String> includes = entry.getValue();

            Set<String> fileIds = mFileToIds.get(file);
            if (fileIds == null) {
                fileIds = Collections.emptySet();
            }

            for (String include : includes) {
                // Find the included file
                File includedFile = findLayoutFile(context, include);
                if (includedFile == null) {
                    continue;
                }
                Set<String> includedIds = mFileToIds.get(includedFile);
                if (includedIds == null) {
                    continue;
                }

                // Check for intersection
                for (String id : includedIds) {
                    if (fileIds.contains(id)) {
                        // Report cross-layout duplicate
                        context.report(
                                CROSS_LAYOUT,
                                Location.create(file),
                                String.format(
                                        "Duplicate id `%1$s` defined in layout `%2$s` is also defined in layout `%3$s` which is included in this layout",
                                        id,
                                        includedFile.getName(),
                                        file.getName()));
                    }
                }
            }
        }

        mFileToIds = null;
        mIncludes = null;
    }

    @Nullable
    private File findLayoutFile(@NonNull Context context, @NonNull String layoutName) {
        Project project = context.getProject();
        List<File> resourceFolders = project.getResourceFolders();
        for (File resFolder : resourceFolders) {
            // Look in layout folders
            File[] layoutDirs = resFolder.listFiles();
            if (layoutDirs == null) {
                continue;
            }
            for (File dir : layoutDirs) {
                if (dir.getName().startsWith("layout")) {
                    File candidate = new File(dir, layoutName + ".xml");
                    if (candidate.exists()) {
                        return candidate;
                    }
                }
            }
        }
        return null;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Handle <include> tags - track which layouts are included
        String layout = element.getAttribute(ATTR_LAYOUT);
        if (layout != null && !layout.isEmpty()) {
            // layout is something like @layout/foo
            String layoutName = layout;
            int slash = layoutName.lastIndexOf('/');
            if (slash >= 0) {
                layoutName = layoutName.substring(slash + 1);
            }
            if (mIncluded == null) {
                mIncluded = new ArrayList<>();
            }
            mIncluded.add(layoutName);
        }
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String id = attribute.getValue();
        if (id == null || id.isEmpty()) {
            return;
        }

        Location location = context.getLocation(attribute);

        if (mIds.containsKey(id)) {
            // Duplicate found
            if (mDuplicates == null) {
                mDuplicates = new HashMap<>();
            }
            List<Location> locations = mDuplicates.get(id);
            if (locations == null) {
                locations = new ArrayList<>();
                // Add the original location
                locations.add(mIds.get(id));
                mDuplicates.put(id, locations);
            }
            locations.add(location);
        } else {
            mIds.put(id, location);
        }
    }

    // ---- Inner class for location tracking ----

    /**
     * A simple entry that pairs an id with a location, used for sorting/comparison.
     */
    static class IdEntry implements Comparable<IdEntry> {
        private final String mId;
        private final Location mLocation;

        IdEntry(@NonNull String id, @NonNull Location location) {
            mId = id;
            mLocation = location;
        }

        @NonNull
        public String getId() {
            return mId;
        }

        @NonNull
        public Location getLocation() {
            return mLocation;
        }

        @Override
        public String toString() {
            return mId;
        }

        @Override
        public int compareTo(@NonNull IdEntry other) {
            return mId.compareTo(other.mId);
        }
    }
}