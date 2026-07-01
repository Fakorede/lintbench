package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_ID;

/**
 * Checks for inconsistencies between different layout versions of the same layout file.
 */
public class LayoutConsistencyDetector extends LayoutDetector {

    /** The main issue discovered by this detector */
    public static final Issue ISSUE = Issue.create(
            "InconsistentLayout",
            "Inconsistent Layouts",
            "This check ensures that a layout resource which is defined in multiple " +
            "resource folders, specifies the same set of widgets.\n" +
            "\n" +
            "This finds cases where you have accidentally forgotten to add " +
            "a widget to all variations of the layout, which could result " +
            "in a runtime crash for some resource configurations when a " +
            "`findViewById()` fails.\n" +
            "\n" +
            "There **are** cases where this is intentional. For example, you " +
            "may have a dedicated large tablet layout which adds some extra " +
            "widgets that are not present in the phone version of the layout. " +
            "As long as the code accessing the layout resource is careful to " +
            "handle this properly, it is valid. In that case, you can suppress " +
            "this lint check for the given extra or missing views, or the whole " +
            "layout",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(
                    LayoutConsistencyDetector.class,
                    Scope.ALL_RESOURCES_SCOPE));

    /**
     * Map from layout name to a map of folder name to list of ids defined in that layout variant.
     * Key: layout file name (e.g. "activity_main")
     * Value: Map from folder qualifier (e.g. "layout", "layout-land") to set of view ids
     */
    private final Map<String, Map<String, Set<String>>> mLayoutToFolderToIds = new HashMap<>();

    /**
     * Map from layout name + folder to the location of the layout file, for error reporting.
     */
    private final Map<String, Location> mFileLocations = new HashMap<>();

    /** Constructs a new {@link LayoutConsistencyDetector} */
    public LayoutConsistencyDetector() {
    }

    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // We collect ids from all elements in layout files
        NamedNodeMap attributes = element.getAttributes();
        if (attributes != null) {
            Attr idAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_ID);
            if (idAttr != null) {
                String id = idAttr.getValue();
                if (id != null && !id.isEmpty()) {
                    // Normalize the id: strip @+id/ or @id/ prefix
                    String normalizedId = normalizeId(id);
                    if (normalizedId != null && !normalizedId.isEmpty()) {
                        recordId(context, normalizedId);
                    }
                }
            }
        }
    }

    /**
     * Normalizes an id value by stripping the @+id/ or @id/ prefix.
     */
    @Nullable
    private static String normalizeId(@NonNull String id) {
        if (id.startsWith("@+id/")) {
            return id.substring(5);
        } else if (id.startsWith("@id/")) {
            return id.substring(4);
        }
        return id;
    }

    /**
     * Records an id found in the given context's layout file.
     */
    private void recordId(@NonNull XmlContext context, @NonNull String id) {
        File file = context.file;
        String layoutName = getLayoutName(file);
        String folderName = file.getParentFile().getName();

        Map<String, Set<String>> folderToIds = mLayoutToFolderToIds.get(layoutName);
        if (folderToIds == null) {
            folderToIds = new HashMap<>();
            mLayoutToFolderToIds.put(layoutName, folderToIds);
        }

        Set<String> ids = folderToIds.get(folderName);
        if (ids == null) {
            ids = new HashSet<>();
            folderToIds.put(folderName, ids);
        }
        ids.add(id);

        // Record file location for error reporting (only need to do this once per file)
        String fileKey = layoutName + ":" + folderName;
        if (!mFileLocations.containsKey(fileKey)) {
            mFileLocations.put(fileKey, Location.create(file));
        }
    }

    /**
     * Gets the layout name (without extension) from a file.
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

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        // Now compare all layout variants and report inconsistencies
        for (Map.Entry<String, Map<String, Set<String>>> entry : mLayoutToFolderToIds.entrySet()) {
            String layoutName = entry.getKey();
            Map<String, Set<String>> folderToIds = entry.getValue();

            if (folderToIds.size() < 2) {
                // Only one variant, nothing to compare
                continue;
            }

            // Compute the union of all ids across all variants
            Set<String> allIds = new HashSet<>();
            for (Set<String> ids : folderToIds.values()) {
                allIds.addAll(ids);
            }

            // For each folder, find ids that are missing compared to other folders
            List<String> folders = new ArrayList<>(folderToIds.keySet());
            Collections.sort(folders);

            for (String folder : folders) {
                Set<String> idsInFolder = folderToIds.get(folder);
                Set<String> missingIds = new HashSet<>(allIds);
                missingIds.removeAll(idsInFolder);

                if (!missingIds.isEmpty()) {
                    // Find which folders have the missing ids
                    List<String> sortedMissingIds = new ArrayList<>(missingIds);
                    Collections.sort(sortedMissingIds);

                    String fileKey = layoutName + ":" + folder;
                    Location location = mFileLocations.get(fileKey);
                    if (location == null) {
                        location = Location.create(context.getProject().getDir());
                    }

                    // Build a message describing which ids are missing and where they exist
                    StringBuilder message = new StringBuilder();
                    message.append("The id-list for layout `").append(layoutName)
                           .append("` is not consistent; layout `").append(folder)
                           .append("` is missing the following id");
                    if (sortedMissingIds.size() > 1) {
                        message.append("s");
                    }
                    message.append(": ");

                    for (int i = 0; i < sortedMissingIds.size(); i++) {
                        if (i > 0) {
                            message.append(", ");
                        }
                        message.append("`@+id/").append(sortedMissingIds.get(i)).append("`");
                    }

                    // Find which folders define the missing ids
                    Set<String> definingFolders = new HashSet<>();
                    for (String missingId : sortedMissingIds) {
                        for (Map.Entry<String, Set<String>> folderEntry : folderToIds.entrySet()) {
                            if (!folderEntry.getKey().equals(folder) &&
                                    folderEntry.getValue().contains(missingId)) {
                                definingFolders.add(folderEntry.getKey());
                            }
                        }
                    }

                    if (!definingFolders.isEmpty()) {
                        List<String> sortedDefiningFolders = new ArrayList<>(definingFolders);
                        Collections.sort(sortedDefiningFolders);
                        message.append(" (defined in ");
                        for (int i = 0; i < sortedDefiningFolders.size(); i++) {
                            if (i > 0) {
                                message.append(", ");
                            }
                            message.append(sortedDefiningFolders.get(i));
                        }
                        message.append(")");
                    }

                    context.report(ISSUE, location, message.toString());
                }
            }
        }
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }
}