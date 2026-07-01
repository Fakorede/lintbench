package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ArraySizeDetector extends ResourceXmlDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(ArraySizeDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "InconsistentArrays",
                    "Inconsistencies in array element counts",
                    "When an array is translated in a different locale, it should normally have " +
                    "the same number of elements as the original array. When adding or removing " +
                    "elements to an array, it is easy to forget to update all the locales, and this " +
                    "lint warning finds inconsistencies like these.\n\n" +
                    "Note however that there may be cases where you really want to declare a " +
                    "different number of array items in each configuration (for example where " +
                    "the array represents available options, and those options differ for " +
                    "different layout orientations and so on), so use your own judgment to " +
                    "decide if this is really an error.\n\n" +
                    "You can suppress this error type if it finds false errors in your project.",
                    Category.CORRECTNESS,
                    7,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private Map<String, List<ArrayInfo>> arraySizes;

    private static class ArrayInfo {
        final int count;
        final String qualifier;
        final Location location;

        ArrayInfo(int count, String qualifier, Location location) {
            this.count = count;
            this.qualifier = qualifier;
            this.location = location;
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
        arraySizes = new HashMap<>();
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (arraySizes == null) {
            return;
        }

        for (Map.Entry<String, List<ArrayInfo>> entry : arraySizes.entrySet()) {
            String name = entry.getKey();
            List<ArrayInfo> infos = entry.getValue();
            if (infos.size() <= 1) {
                continue;
            }

            int expectedCount = -1;
            // Prefer the default 'values' folder as the source of truth
            for (ArrayInfo info : infos) {
                if ("values".equals(info.qualifier)) {
                    expectedCount = info.count;
                    break;
                }
            }
            // Fallback to the first encountered configuration if default is missing
            if (expectedCount == -1) {
                expectedCount = infos.get(0).count;
            }

            for (ArrayInfo info : infos) {
                if (info.count != expectedCount) {
                    String message = String.format(
                            "Array \"%s\" has %d items, but the default configuration has %d items",
                            name, info.count, expectedCount);
                    context.report(ISSUE, info.location, message);
                }
            }
        }

        arraySizes = null;
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
            if (child.getNodeType() == Node.ELEMENT_NODE && "item".equals(child.getNodeName())) {
                count++;
            }
        }

        String qualifier = context.file.getParentFile() != null
                ? context.file.getParentFile().getName()
                : "values";

        Location location = Location.create(context.file, element);
        arraySizes.computeIfAbsent(name, k -> new ArrayList<>())
                .add(new ArrayInfo(count, qualifier, location));
    }
}