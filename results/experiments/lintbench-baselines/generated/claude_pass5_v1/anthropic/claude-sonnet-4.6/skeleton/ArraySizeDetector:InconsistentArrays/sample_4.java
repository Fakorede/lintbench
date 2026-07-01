package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class ArraySizeDetector extends ResourceXmlDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(ArraySizeDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "InconsistentArrays",
                    "Inconsistencies in array element counts",
                    "When an array is translated in a different locale, it should normally have "
                            + "the same number of elements as the original array. When adding or removing "
                            + "elements to an array, it is easy to forget to update all the locales, and this "
                            + "lint warning finds inconsistencies like these.\n"
                            + "\n"
                            + "Note however that there may be cases where you really want to declare a "
                            + "different number of array items in each configuration (for example where "
                            + "the array represents available options, and those options differ for "
                            + "different layout orientations and so on), so use your own judgment to "
                            + "decide if this is really an error.\n"
                            + "\n"
                            + "You can suppress this error type if it finds false errors in your project.",
                    Category.CORRECTNESS,
                    7,
                    Severity.WARNING,
                    IMPLEMENTATION);

    /**
     * Map from array name to a list of (file, count) pairs recording how many elements
     * each occurrence of the array has, across all resource files.
     */
    private Map<String, List<int[]>> mArrayCount;

    /**
     * Map from array name to a list of locations (one per file) for reporting.
     */
    private Map<String, List<Location>> mArrayLocations;

    /**
     * Map from array name to a list of file paths for reporting.
     */
    private Map<String, List<File>> mArrayFiles;

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.VALUES;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("string-array", "integer-array", "array");
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mArrayCount = new HashMap<>();
        mArrayLocations = new HashMap<>();
        mArrayFiles = new HashMap<>();
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (mArrayCount == null) {
            return;
        }

        for (Map.Entry<String, List<int[]>> entry : mArrayCount.entrySet()) {
            String name = entry.getKey();
            List<int[]> counts = entry.getValue();

            if (counts.size() <= 1) {
                continue;
            }

            // Check if all counts are the same
            int firstCount = counts.get(0)[0];
            boolean allSame = true;
            for (int[] countArr : counts) {
                if (countArr[0] != firstCount) {
                    allSame = false;
                    break;
                }
            }

            if (!allSame) {
                // Report the issue on the first location
                List<Location> locations = mArrayLocations.get(name);
                List<File> files = mArrayFiles.get(name);

                if (locations == null || locations.isEmpty()) {
                    continue;
                }

                // Build a message describing the inconsistency
                StringBuilder sb = new StringBuilder();
                sb.append("Array `").append(name).append("` has an inconsistent number of items (");
                for (int i = 0; i < counts.size(); i++) {
                    if (i > 0) {
                        sb.append(", ");
                    }
                    File file = (files != null && i < files.size()) ? files.get(i) : null;
                    String folderName = file != null ? file.getParentFile().getName() : "unknown";
                    sb.append(counts.get(i)[0]).append(" in ").append(folderName);
                }
                sb.append(")");

                Location location = locations.get(0);

                // Chain secondary locations
                for (int i = 1; i < locations.size(); i++) {
                    Location secondary = locations.get(i);
                    File file = (files != null && i < files.size()) ? files.get(i) : null;
                    String folderName = file != null ? file.getParentFile().getName() : "unknown";
                    secondary.setMessage(
                            counts.get(i)[0] + " elements declared here (" + folderName + ")");
                    location.setSecondary(secondary);
                    location = secondary;
                }

                context.report(
                        ISSUE,
                        locations.get(0),
                        sb.toString());
            }
        }

        mArrayCount = null;
        mArrayLocations = null;
        mArrayFiles = null;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttribute("name");
        if (name == null || name.isEmpty()) {
            return;
        }

        // Count child item elements
        int count = 0;
        NodeList children = element.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                count++;
            }
        }

        List<int[]> counts = mArrayCount.get(name);
        if (counts == null) {
            counts = new ArrayList<>();
            mArrayCount.put(name, counts);
        }
        counts.add(new int[]{count});

        List<Location> locations = mArrayLocations.get(name);
        if (locations == null) {
            locations = new ArrayList<>();
            mArrayLocations.put(name, locations);
        }
        locations.add(context.getLocation(element));

        List<File> files = mArrayFiles.get(name);
        if (files == null) {
            files = new ArrayList<>();
            mArrayFiles.put(name, files);
        }
        files.add(context.file);
    }
}