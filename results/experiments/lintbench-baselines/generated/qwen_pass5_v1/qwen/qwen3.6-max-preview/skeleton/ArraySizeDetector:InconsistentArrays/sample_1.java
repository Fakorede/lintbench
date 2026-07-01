package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
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

    private Map<String, List<ArrayEntry>> arrays;

    private static class ArrayEntry {
        final int count;
        final XmlContext context;
        final Element element;
        final boolean isDefault;

        ArrayEntry(int count, XmlContext context, Element element, boolean isDefault) {
            this.count = count;
            this.context = context;
            this.element = element;
            this.isDefault = isDefault;
        }
    }

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
        arrays = new HashMap<>();
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (arrays == null) {
            return;
        }

        for (Map.Entry<String, List<ArrayEntry>> entry : arrays.entrySet()) {
            List<ArrayEntry> entries = entry.getValue();
            if (entries.size() <= 1) {
                continue;
            }

            // Determine baseline count from the default (unqualified) values folder
            Integer baselineCount = null;
            for (ArrayEntry e : entries) {
                if (e.isDefault) {
                    baselineCount = e.count;
                    break;
                }
            }
            // Fallback to first encountered if no default exists
            if (baselineCount == null) {
                baselineCount = entries.get(0).count;
            }

            final int expected = baselineCount;
            for (ArrayEntry e : entries) {
                if (e.count != expected) {
                    String message = String.format(
                            "This array has %d elements, but the default array has %d elements",
                            e.count, expected);
                    e.context.report(ISSUE, e.context.getLocation(e.element), message);
                }
            }
        }
        arrays = null;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttribute("name");
        if (name == null || name.isEmpty()) {
            return;
        }

        int count = 0;
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if ("item".equals(child.getNodeName())) {
                count++;
            }
        }

        boolean isDefault = context.getFolder() != null
                && context.getFolder().getName().equals("values");

        arrays.computeIfAbsent(name, k -> new ArrayList<>())
              .add(new ArrayEntry(count, context, element, isDefault));
    }
}