package com.android.tools.lint.checks;

import com.android.SdkConstants;
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
                    "It's okay for two independent layouts to use the same ids. However, if"
                            + " layouts are combined with include tags, then the id's need to be"
                            + " unique within any chain of included layouts, or"
                            + " `Activity#findViewById()` can return an unexpected view.",
                    Category.CORRECTNESS,
                    7,
                    Severity.WARNING,
                    new Implementation(DuplicateIdDetector.class, Scope.ALL_RESOURCES_SCOPE));

    /** Map from layout file to the set of ids defined in that layout */
    private Map<File, Set<String>> mFileToIds;

    /** Map from layout file to the list of included layouts */
    private Map<File, List<String>> mIncludes;

    /** Map from layout file to the ids defined locally (not via includes) */
    private Map<File, Set<String>> mLocalIds;

    /** Location map for ids */
    private Map<File, Map<String, Location>> mIdLocations;

    /** Current file being processed */
    private File mCurrentFile;

    /** Ids seen in the current file */
    private Set<String> mCurrentIds;

    /** Includes seen in the current file */
    private List<String> mCurrentIncludes;

    /** Location map for ids in the current file */
    private Map<String, Location> mCurrentIdLocations;

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(SdkConstants.ATTR_ID);
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(SdkConstants.VIEW_INCLUDE);
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mCurrentFile = context.file;
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
        if (mLocalIds == null) {
            mLocalIds = new HashMap<>();
        }
        if (mIdLocations == null) {
            mIdLocations = new HashMap<>();
        }

        mFileToIds.put(mCurrentFile, mCurrentIds);
        mLocalIds.put(mCurrentFile, new HashSet<>(mCurrentIds));
        if (!mCurrentIncludes.isEmpty()) {
            mIncludes.put(mCurrentFile, mCurrentIncludes);
        }
        mIdLocations.put(mCurrentFile, mCurrentIdLocations);

        mCurrentFile = null;
        mCurrentIds = null;
        mCurrentIncludes = null;
        mCurrentIdLocations = null;
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mFileToIds = new HashMap<>();
        mIncludes = new HashMap<>();
        mLocalIds = new HashMap<>();
        mIdLocations = new HashMap<>();
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (mIncludes == null || mIncludes.isEmpty()) {
            return;
        }

        // Build a map from layout name to file
        Map<String, File> nameToFile = new HashMap<>();
        if (mFileToIds != null) {
            for (File file : mFileToIds.keySet()) {
                String name = getLayoutName(file);
                nameToFile.put(name, file);
            }
        }

        // For each file that has includes, check for duplicate ids
        for (Map.Entry<File, List<String>> entry : mIncludes.entrySet()) {
            File file = entry.getKey();
            List<String> includes = entry.getValue();

            // Collect all ids from this file and all included layouts (recursively)
            Set<File> visited = new HashSet<>();
            visited.add(file);

            Set<String> allIds = new HashSet<>();
            Map<String, List<File>> idToFiles = new HashMap<>();

            // Add ids from the current file
            Set<String> localIds = mLocalIds.get(file);
            if (localIds != null) {
                for (String id : localIds) {
                    allIds.add(id);
                    List<File> files = idToFiles.get(id);
                    if (files == null) {
                        files = new ArrayList<>();
                        idToFiles.put(id, files);
                    }
                    files.add(file);
                }
            }

            // Add ids from included layouts
            for (String include : includes) {
                File includedFile = nameToFile.get(include);
                if (includedFile != null && !visited.contains(includedFile)) {
                    collectIds(includedFile, nameToFile, visited, idToFiles, allIds);
                }
            }

            // Report duplicates
            for (Map.Entry<String, List<File>> idEntry : idToFiles.entrySet()) {
                String id = idEntry.getKey();
                List<File> filesWithId = idEntry.getValue();
                if (filesWithId.size() > 1) {
                    // Find the location in the main file if it has this id
                    Map<String, Location> locations = mIdLocations.get(file);
                    Location location = null;
                    if (locations != null) {
                        location = locations.get(id);
                    }

                    if (location == null) {
                        // Try to find location in any of the files
                        for (File f : filesWithId) {
                            Map<String, Location> locs = mIdLocations.get(f);
                            if (locs != null) {
                                location = locs.get(id);
                                if (location != null) {
                                    break;
                                }
                            }
                        }
                    }

                    if (location != null) {
                        // Build secondary locations
                        Location secondary = null;
                        for (int i = filesWithId.size() - 1; i >= 0; i--) {
                            File f = filesWithId.get(i);
                            Map<String, Location> locs = mIdLocations.get(f);
                            if (locs != null) {
                                Location loc = locs.get(id);
                                if (loc != null && loc != location) {
                                    loc.setSecondary(secondary);
                                    secondary = loc;
                                }
                            }
                        }
                        if (secondary != null) {
                            location.setSecondary(secondary);
                        }

                        StringBuilder sb = new StringBuilder();
                        sb.append("Duplicate id `").append(id)
                                .append("`, defined or included multiple times in layout: ");
                        for (int i = 0; i < filesWithId.size(); i++) {
                            if (i > 0) {
                                sb.append(", ");
                            }
                            sb.append(getLayoutName(filesWithId.get(i)));
                        }

                        context.report(ISSUE, location, sb.toString());
                    }
                }
            }
        }
    }

    private void collectIds(
            @NonNull File file,
            @NonNull Map<String, File> nameToFile,
            @NonNull Set<File> visited,
            @NonNull Map<String, List<File>> idToFiles,
            @NonNull Set<String> allIds) {
        visited.add(file);

        Set<String> localIds = mLocalIds.get(file);
        if (localIds != null) {
            for (String id : localIds) {
                allIds.add(id);
                List<File> files = idToFiles.get(id);
                if (files == null) {
                    files = new ArrayList<>();
                    idToFiles.put(id, files);
                }
                files.add(file);
            }
        }

        List<String> includes = mIncludes.get(file);
        if (includes != null) {
            for (String include : includes) {
                File includedFile = nameToFile.get(include);
                if (includedFile != null && !visited.contains(includedFile)) {
                    collectIds(includedFile, nameToFile, visited, idToFiles, allIds);
                }
            }
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Handle include tags
        String layout = element.getAttribute(SdkConstants.ATTR_LAYOUT);
        if (layout != null && !layout.isEmpty()) {
            // Strip @layout/ prefix
            if (layout.startsWith(SdkConstants.LAYOUT_RESOURCE_PREFIX)) {
                layout = layout.substring(SdkConstants.LAYOUT_RESOURCE_PREFIX.length());
            } else if (layout.startsWith("@android:layout/")) {
                // Skip framework layouts
                return;
            }
            if (mCurrentIncludes != null) {
                mCurrentIncludes.add(layout);
            }
        }
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String id = attribute.getValue();
        if (id == null || id.isEmpty()) {
            return;
        }

        // Strip @+id/ or @id/ prefix
        if (id.startsWith(SdkConstants.NEW_ID_PREFIX)) {
            id = id.substring(SdkConstants.NEW_ID_PREFIX.length());
        } else if (id.startsWith(SdkConstants.ID_PREFIX)) {
            id = id.substring(SdkConstants.ID_PREFIX.length());
        } else {
            return;
        }

        if (mCurrentIds != null) {
            if (mCurrentIds.contains(id)) {
                // Duplicate within the same file - report immediately
                Map<String, Location> locations = mCurrentIdLocations;
                Location existing = locations != null ? locations.get(id) : null;
                Location location = context.getLocation(attribute);
                if (existing != null) {
                    location.setSecondary(existing);
                }
                context.report(
                        ISSUE,
                        attribute,
                        location,
                        "Duplicate id `@+id/" + id + "`, already defined earlier in this layout");
            } else {
                mCurrentIds.add(id);
                if (mCurrentIdLocations != null) {
                    mCurrentIdLocations.put(id, context.getLocation(attribute));
                }
            }
        }
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