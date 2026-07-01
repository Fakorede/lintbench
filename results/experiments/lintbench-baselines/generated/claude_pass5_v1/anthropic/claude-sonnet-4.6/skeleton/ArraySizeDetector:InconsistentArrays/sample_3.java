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
                            + "lint warning finds inconsistencies like these.\n\n"
                            + "Note however that there may be cases where you really want to declare a "
                            + "different number of array items in each configuration (for example where "
                            + "the array represents available options, and those options differ for "
                            + "different layout orientations and so on), so use your own judgment to "
                            + "decide if this is really an error.\n\n"
                            + "You can suppress this error type if it finds false errors in your project.",
                    Category.CORRECTNESS,
                    7,
                    Severity.WARNING,
                    IMPLEMENTATION);

    /**
     * Map from array name to a list of (file, count) pairs recording how many
     * items each declaration of that array contains.
     */
    private Map<String, List<ArrayEntry>> mArrays;

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
        mArrays = new HashMap<>();
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (mArrays == null) {
            return;
        }

        for (Map.Entry<String, List<ArrayEntry>> entry : mArrays.entrySet()) {
            String name = entry.getKey();
            List<ArrayEntry> entries = entry.getValue();

            if (entries.size() < 2) {
                continue;
            }

            // Find the entry from the default folder (no qualifiers) as the reference
            ArrayEntry defaultEntry = null;
            for (ArrayEntry ae : entries) {
                if (isDefaultFolder(ae.file)) {
                    defaultEntry = ae;
                    break;
                }
            }

            if (defaultEntry == null) {
                // No default folder entry; use the first one as reference
                defaultEntry = entries.get(0);
            }

            int defaultCount = defaultEntry.count;

            for (ArrayEntry ae : entries) {
                if (ae == defaultEntry) {
                    continue;
                }
                if (ae.count != defaultCount) {
                    String message =
                            String.format(
                                    "Array `%1$s` has an inconsistent number of items (%2$d in `%3$s`, %4$d in `%5$s`)",
                                    name,
                                    ae.count,
                                    getFolderName(ae.file),
                                    defaultCount,
                                    getFolderName(defaultEntry.file));
                    context.report(ISSUE, ae.location, message);
                }
            }
        }

        mArrays = null;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttribute("name");
        if (name == null || name.isEmpty()) {
            return;
        }

        int count = countChildren(element);

        List<ArrayEntry> entries = mArrays.get(name);
        if (entries == null) {
            entries = new ArrayList<>();
            mArrays.put(name, entries);
        }

        Location location = context.getLocation(element);
        File file = context.file;
        entries.add(new ArrayEntry(file, count, location));
    }

    private static int countChildren(Element element) {
        int count = 0;
        NodeList children = element.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                count++;
            }
        }
        return count;
    }

    private static boolean isDefaultFolder(File file) {
        File parent = file.getParentFile();
        if (parent == null) {
            return false;
        }
        String folderName = parent.getName();
        // Default values folder has no qualifiers (just "values")
        return folderName.equals("values");
    }

    private static String getFolderName(File file) {
        File parent = file.getParentFile();
        if (parent != null) {
            return parent.getName();
        }
        return file.getName();
    }

    private static class ArrayEntry {
        final File file;
        final int count;
        final Location location;

        ArrayEntry(File file, int count, Location location) {
            this.file = file;
            this.count = count;
            this.location = location;
        }
    }
}