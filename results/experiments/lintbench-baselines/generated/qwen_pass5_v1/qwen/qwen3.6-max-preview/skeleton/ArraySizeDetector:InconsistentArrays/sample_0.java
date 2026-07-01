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
                    "When an array is translated in a different locale, it should normally have the same number of elements as the original array. When adding or removing elements to an array, it is easy to forget to update all the locales, and this lint warning finds inconsistencies like these.\n\n"
                            + "Note however that there may be cases where you really want to declare a different number of array items in each configuration (for example where the array represents available options, and those options differ for different layout orientations and so on), so use your own judgment to decide if this is really an error.\n\n"
                            + "You can suppress this error type if it finds false errors in your project.",
                    Category.CORRECTNESS,
                    7,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private Map<String, List<ArrayData>> arrays = new HashMap<>();

    private static class ArrayData {
        final int count;
        final Location location;
        final String qualifiers;

        ArrayData(int count, Location location, String qualifiers) {
            this.count = count;
            this.location = location;
            this.qualifiers = qualifiers;
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

        String folderName = context.file.getParentFile() != null
                ? context.file.getParentFile().getName()
                : "";

        arrays.computeIfAbsent(name, k -> new ArrayList<>())
              .add(new ArrayData(count, context.getLocation(element), folderName));
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        for (Map.Entry<String, List<ArrayData>> entry : arrays.entrySet()) {
            String name = entry.getKey();
            List<ArrayData> dataList = entry.getValue();
            if (dataList.size() < 2) {
                continue;
            }

            Integer baseCount = null;
            for (ArrayData data : dataList) {
                if ("values".equals(data.qualifiers)) {
                    baseCount = data.count;
                    break;
                }
            }
            if (baseCount == null) {
                baseCount = dataList.get(0).count;
            }

            for (ArrayData data : dataList) {
                if (data.count != baseCount) {
                    String message = String.format(
                            "Array \"%s\" has %d elements, but the base array has %d elements",
                            name, data.count, baseCount);
                    context.report(ISSUE, data.location, message);
                }
            }
        }
    }
}