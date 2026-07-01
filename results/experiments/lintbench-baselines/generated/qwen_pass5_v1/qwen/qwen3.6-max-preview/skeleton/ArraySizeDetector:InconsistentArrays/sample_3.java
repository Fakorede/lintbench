package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
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

    private final Map<String, List<ArrayData>> arrays = new HashMap<>();

    private static class ArrayData {
        final String name;
        final int size;
        final XmlContext context;
        final Element element;
        final String folderName;

        ArrayData(String name, int size, XmlContext context, Element element, String folderName) {
            this.name = name;
            this.size = size;
            this.context = context;
            this.element = element;
            this.folderName = folderName;
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
        arrays.clear();
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        for (List<ArrayData> list : arrays.values()) {
            if (list.size() < 2) {
                continue;
            }

            ArrayData base = null;
            for (ArrayData data : list) {
                if ("values".equals(data.folderName)) {
                    base = data;
                    break;
                }
            }
            if (base == null) {
                base = list.get(0);
            }

            int expectedSize = base.size;
            for (ArrayData data : list) {
                if (data.size != expectedSize) {
                    String message = String.format(
                            "Array \"%s\" has %d elements in %s but %d elements in %s",
                            data.name, data.size, data.folderName, expectedSize, base.folderName);
                    data.context.report(ISSUE, data.element, data.context.getLocation(data.element), message);
                }
            }
        }
        arrays.clear();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttribute("name");
        if (name == null || name.isEmpty()) {
            return;
        }

        int size = 0;
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if ("item".equals(child.getNodeName())) {
                size++;
            }
        }

        String folderName = context.file.getParentFile() != null
                ? context.file.getParentFile().getName()
                : "values";

        arrays.computeIfAbsent(name, k -> new ArrayList<>())
              .add(new ArrayData(name, size, context, element, folderName));
    }
}