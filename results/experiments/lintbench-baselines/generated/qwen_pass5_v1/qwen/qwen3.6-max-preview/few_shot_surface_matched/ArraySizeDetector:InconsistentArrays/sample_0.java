package com.android.tools.lint.checks;

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
import com.android.tools.lint.detector.api.XmlScanner;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ArraySizeDetector extends ResourceXmlDetector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
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
            5,
            Severity.WARNING,
            new Implementation(ArraySizeDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private Map<String, List<ArrayRecord>> arrayCounts;

    private static class ArrayRecord {
        final int count;
        final String folder;
        final Location location;

        ArrayRecord(int count, String folder, Location location) {
            this.count = count;
            this.folder = folder;
            this.location = location;
        }
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.VALUES;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("array", "string-array", "integer-array");
    }

    @Override
    public void beforeCheckRootProject(Context context) {
        arrayCounts = new HashMap<>();
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String name = element.getAttribute("name");
        if (name == null || name.isEmpty()) {
            return;
        }

        int count = 0;
        NodeList children = element.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && "item".equals(child.getNodeName())) {
                count++;
            }
        }

        String folder = context.file.getParentFile() != null ? context.file.getParentFile().getName() : "";
        ArrayRecord record = new ArrayRecord(count, folder, context.getLocation(element));
        arrayCounts.computeIfAbsent(name, k -> new ArrayList<>()).add(record);
    }

    @Override
    public void afterCheckRootProject(Context context) {
        if (arrayCounts == null) {
            return;
        }

        for (List<ArrayRecord> records : arrayCounts.values()) {
            if (records.size() < 2) {
                continue;
            }

            ArrayRecord base = null;
            for (ArrayRecord r : records) {
                if ("values".equals(r.folder)) {
                    base = r;
                    break;
                }
            }
            if (base == null) {
                base = records.get(0);
            }

            for (ArrayRecord r : records) {
                if (r != base && r.count != base.count) {
                    context.report(ISSUE, r.location,
                            String.format("This array has a different number of elements (%d) than the default array (%d)",
                                    r.count, base.count));
                }
            }
        }
        arrayCounts = null;
    }
}