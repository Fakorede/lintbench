package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ArraySizeDetector extends ResourceXmlDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "InconsistentArrays",
                    "Inconsistent array element counts",
                    "When an array is translated in a different locale, it should normally have "
                            + "the same number of elements as the original array. When adding or "
                            + "removing elements to an array, it is easy to forget to update all "
                            + "the locales, and this lint warning finds inconsistencies like these."
                            + "\n\nNote however that there may be cases where you really want to "
                            + "declare a different number of array items in each configuration "
                            + "(for example where the array represents available options, and "
                            + "those options differ for different layout orientations and so on), "
                            + "so use your own judgment to decide if this is really an error."
                            + "\n\nYou can suppress this error type if it finds false errors in "
                            + "your project.",
                    Category.CORRECTNESS,
                    7,
                    Severity.WARNING,
                    new Implementation(ArraySizeDetector.class, Scope.ALL_RESOURCES_SCOPE));

    /** Map from array name to a list of (file, count) pairs */
    private Map<String, List<ArrayCount>> mArrays;

    /** Simple holder for a file and element count pair */
    private static class ArrayCount {
        final File file;
        final int count;
        final XmlContext context;
        final Element element;

        ArrayCount(File file, int count, XmlContext context, Element element) {
            this.file = file;
            this.count = count;
            this.context = context;
            this.element = element;
        }
    }

    public ArraySizeDetector() {
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.VALUES;
    }

    @Override
    @Nullable
    public Collection<String> getApplicableElements() {
        return Arrays.asList("string-array", "integer-array", "array");
    }

    @Override
    public void beforeCheckRootProject(@NonNull com.android.tools.lint.detector.api.Project project) {
        mArrays = new HashMap<>();
    }

    @Override
    public void afterCheckRootProject(@NonNull com.android.tools.lint.detector.api.Project project) {
        if (mArrays == null) {
            return;
        }

        for (Map.Entry<String, List<ArrayCount>> entry : mArrays.entrySet()) {
            String name = entry.getKey();
            List<ArrayCount> counts = entry.getValue();

            if (counts.size() < 2) {
                continue;
            }

            // Find the default (non-localized) count if available
            // We look for the one in the "values" folder (no qualifiers)
            int defaultCount = -1;
            ArrayCount defaultEntry = null;
            for (ArrayCount ac : counts) {
                String parentName = ac.file.getParentFile().getName();
                if (parentName.equals("values")) {
                    defaultCount = ac.count;
                    defaultEntry = ac;
                    break;
                }
            }

            if (defaultEntry == null) {
                // No default; pick the first one as reference
                defaultEntry = counts.get(0);
                defaultCount = defaultEntry.count;
            }

            // Check all entries against the default count
            for (ArrayCount ac : counts) {
                if (ac == defaultEntry) {
                    continue;
                }
                if (ac.count != defaultCount) {
                    String message = String.format(
                            "Array `%1$s` has an inconsistent number of items (%2$d in `%3$s`, %4$d in `%5$s`)",
                            name,
                            ac.count,
                            ac.file.getParentFile().getName() + "/" + ac.file.getName(),
                            defaultCount,
                            defaultEntry.file.getParentFile().getName() + "/" + defaultEntry.file.getName());
                    ac.context.report(
                            ISSUE,
                            ac.element,
                            ac.context.getLocation(ac.element),
                            message);
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

        // Count the number of <item> children
        int count = 0;
        NodeList children = element.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                count++;
            }
        }

        if (mArrays == null) {
            mArrays = new HashMap<>();
        }

        List<ArrayCount> list = mArrays.get(name);
        if (list == null) {
            list = new ArrayList<>();
            mArrays.put(name, list);
        }

        list.add(new ArrayCount(context.file, count, context, element));
    }
}