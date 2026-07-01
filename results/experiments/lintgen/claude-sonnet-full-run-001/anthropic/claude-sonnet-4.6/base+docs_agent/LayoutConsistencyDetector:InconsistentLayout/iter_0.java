package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LintMap;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
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
public class LayoutConsistencyDetector extends Detector implements XmlScanner {

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

    // ---- Implements XmlScanner ----

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        // Get the layout file name (without extension)
        File file = context.file;
        String fileName = file.getName();
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex != -1) {
            fileName = fileName.substring(0, dotIndex);
        }

        // Get the folder name (e.g. "layout", "layout-land", "layout-xlarge")
        String folderName = file.getParentFile().getName();

        // Collect all view ids in this layout
        Set<String> ids = new HashSet<>();
        collectIds(document.getDocumentElement(), ids);

        // Store the ids for this layout variant
        Map<String, Set<String>> folderToIds = mLayoutToFolderToIds.get(fileName);
        if (folderToIds == null) {
            folderToIds = new HashMap<>();
            mLayoutToFolderToIds.put(fileName, folderToIds);
        }
        folderToIds.put(folderName, ids);

        // Store the file location for error reporting
        String key = fileName + ":" + folderName;
        mFileLocations.put(key, Location.create(file));
    }

    /**
     * Recursively collects all android:id attributes from the layout.
     */
    private static void collectIds(@Nullable Element element, @NonNull Set<String> ids) {
        if (element == null) {
            return;
        }

        // Check for android:id attribute
        String id = element.getAttributeNS(ANDROID_URI, ATTR_ID);
        if (id != null && !id.isEmpty()) {
            // Normalize the id (strip @+id/ or @id/ prefix)
            ids.add(normalizeId(id));
        }

        // Recurse into children
        NodeList children = element.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                collectIds((Element) child, ids);
            }
        }
    }

    /**
     * Normalizes an id value by stripping the @+id/ or @id/ prefix.
     */
    @NonNull
    private static String normalizeId(@NonNull String id) {
        if (id.startsWith("@+id/")) {
            return id.substring(5);
        } else if (id.startsWith("@id/")) {
            return id.substring(4);
        }
        return id;
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        // Now compare all layout variants for each layout name
        for (Map.Entry<String, Map<String, Set<String>>> entry : mLayoutToFolderToIds.entrySet()) {
            String layoutName = entry.getKey();
            Map<String, Set<String>> folderToIds = entry.getValue();

            // Only check if there are multiple variants
            if (folderToIds.size() < 2) {
                continue;
            }

            // Compute the union of all ids across all variants
            Set<String> allIds = new HashSet<>();
            for (Set<String> ids : folderToIds.values()) {
                allIds.addAll(ids);
            }

            // For each variant, find ids that are missing
            // Sort folder names for deterministic output
            List<String> folders = new ArrayList<>(folderToIds.keySet());
            Collections.sort(folders);

            for (String folder : folders) {
                Set<String> ids = folderToIds.get(folder);
                Set<String> missingIds = new HashSet<>(allIds);
                missingIds.removeAll(ids);

                if (!missingIds.isEmpty()) {
                    // Find which folders have the missing ids
                    List<String> sortedMissingIds = new ArrayList<>(missingIds);
                    Collections.sort(sortedMissingIds);

                    String key = layoutName + ":" + folder;
                    Location location = mFileLocations.get(key);
                    if (location == null) {
                        continue;
                    }

                    // Build a message describing the missing ids
                    StringBuilder sb = new StringBuilder();
                    sb.append("The id ");

                    if (sortedMissingIds.size() == 1) {
                        sb.append('`').append(sortedMissingIds.get(0)).append('`');
                        sb.append(" is missing from the layout in folder `");
                        sb.append(folder).append('`');
                    } else {
                        sb.append("s ");
                        for (int i = 0; i < sortedMissingIds.size(); i++) {
                            if (i > 0) {
                                if (i == sortedMissingIds.size() - 1) {
                                    sb.append(" and ");
                                } else {
                                    sb.append(", ");
                                }
                            }
                            sb.append('`').append(sortedMissingIds.get(i)).append('`');
                        }
                        sb.append(" are missing from the layout in folder `");
                        sb.append(folder).append('`');
                    }

                    // Find which other folders define these ids
                    List<String> definingFolders = new ArrayList<>();
                    for (String otherFolder : folders) {
                        if (!otherFolder.equals(folder)) {
                            Set<String> otherIds = folderToIds.get(otherFolder);
                            // Check if this folder has at least one of the missing ids
                            for (String missingId : missingIds) {
                                if (otherIds.contains(missingId)) {
                                    definingFolders.add(otherFolder);
                                    break;
                                }
                            }
                        }
                    }

                    if (!definingFolders.isEmpty()) {
                        sb.append(" but is defined in ");
                        for (int i = 0; i < definingFolders.size(); i++) {
                            if (i > 0) {
                                if (i == definingFolders.size() - 1) {
                                    sb.append(" and ");
                                } else {
                                    sb.append(", ");
                                }
                            }
                            sb.append('`').append(definingFolders.get(i)).append('`');
                        }
                    }

                    context.report(ISSUE, location, sb.toString());
                }
            }
        }
    }
}