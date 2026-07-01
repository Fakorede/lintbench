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
import java.util.Collections;
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
     * Map from array name to a list of (file, count) pairs across all resource folders.
     * Key: array name
     * Value: list of pairs [file, itemCount]
     */
    private Map<String, List<Object[]>> mFileToArrayCount;

    public ArraySizeDetector() {
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.VALUES;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                "string-array",
                "integer-array",
                "array"
        );
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mFileToArrayCount = new HashMap<>();
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (mFileToArrayCount == null) {
            return;
        }

        for (Map.Entry<String, List<Object[]>> entry : mFileToArrayCount.entrySet()) {
            String arrayName = entry.getKey();
            List<Object[]> locations = entry.getValue();

            if (locations.size() < 2) {
                continue;
            }

            // Find the count from the default (non-localized) folder if available,
            // otherwise use the first entry as reference
            int referenceCount = -1;
            Object[] referenceEntry = null;

            for (Object[] info : locations) {
                File file = (File) info[0];
                String parentName = file.getParentFile().getName();
                // Default values folder has no qualifiers (just "values")
                if (parentName.equals("values")) {
                    referenceCount = (Integer) info[1];
                    referenceEntry = info;
                    break;
                }
            }

            if (referenceEntry == null) {
                // No default folder entry; use the first one
                referenceEntry = locations.get(0);
                referenceCount = (Integer) referenceEntry[1];
            }

            // Check all other entries against the reference count
            for (Object[] info : locations) {
                if (info == referenceEntry) {
                    continue;
                }
                int count = (Integer) info[1];
                if (count != referenceCount) {
                    Location location = (Location) info[2];
                    Location referenceLocation = (Location) referenceEntry[2];
                    File file = (File) info[0];
                    File referenceFile = (File) referenceEntry[0];

                    String message = String.format(
                            "Array `%1$s` has an inconsistent number of items (%2$d in `%3$s`, %4$d in `%5$s`)",
                            arrayName,
                            count,
                            file.getParentFile().getName() + "/" + file.getName(),
                            referenceCount,
                            referenceFile.getParentFile().getName() + "/" + referenceFile.getName());

                    Location linked = referenceLocation.withSecondary(
                            referenceLocation,
                            "Reference array defined here");

                    context.report(ISSUE, location, message);
                }
            }
        }

        mFileToArrayCount = null;
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

        File file = context.file;
        Location location = context.getLocation(element);

        List<Object[]> list = mFileToArrayCount.get(name);
        if (list == null) {
            list = new ArrayList<>();
            mFileToArrayCount.put(name, list);
        }

        // Store [file, count, location]
        list.add(new Object[]{file, count, location});
    }
}