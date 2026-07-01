package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.jetbrains.annotations.NotNull;
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

public class ArraySizeDetector extends ResourceXmlDetector {

    public static final Issue ISSUE = Issue.create(
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
            6,
            Severity.WARNING,
            new Implementation(ArraySizeDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private Map<String, List<ArrayData>> arrays;

    @Override
    public void beforeCheckProject(@NotNull Context context) {
        arrays = new HashMap<>();
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                SdkConstants.TAG_ARRAY,
                SdkConstants.TAG_STRING_ARRAY,
                SdkConstants.TAG_INTEGER_ARRAY
        );
    }

    @Override
    public void visitElement(@NotNull XmlContext context, @NotNull Element element) {
        String name = element.getAttribute(SdkConstants.ATTR_NAME);
        if (name == null || name.isEmpty()) {
            return;
        }

        int count = 0;
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE &&
                    SdkConstants.TAG_ITEM.equals(child.getNodeName())) {
                count++;
            }
        }

        arrays.computeIfAbsent(name, k -> new ArrayList<>())
                .add(new ArrayData(name, count, context, element));
    }

    @Override
    public void afterCheckProject(@NotNull Context context) {
        if (arrays == null) {
            return;
        }

        for (List<ArrayData> list : arrays.values()) {
            if (list.size() <= 1) {
                continue;
            }

            ArrayData base = null;
            for (ArrayData data : list) {
                File parent = data.context.file.getParentFile();
                String folderName = parent != null ? parent.getName() : "";
                if (SdkConstants.FD_RES_VALUES.equals(folderName)) {
                    base = data;
                    break;
                }
            }

            if (base == null) {
                continue;
            }

            for (ArrayData data : list) {
                if (data == base) {
                    continue;
                }
                if (data.count != base.count) {
                    File dataParent = data.context.file.getParentFile();
                    File baseParent = base.context.file.getParentFile();
                    String dataFolder = dataParent != null ? dataParent.getName() : "unknown";
                    String baseFolder = baseParent != null ? baseParent.getName() : "unknown";

                    String message = String.format(
                            "Array `%s` has %d elements in %s but %d elements in %s",
                            data.name, data.count, dataFolder, base.count, baseFolder);

                    data.context.report(ISSUE, data.element,
                            data.context.getLocation(data.element), message);
                }
            }
        }
        arrays = null;
    }

    private static class ArrayData {
        final String name;
        final int count;
        final XmlContext context;
        final Element element;

        ArrayData(String name, int count, XmlContext context, Element element) {
            this.name = name;
            this.count = count;
            this.context = context;
            this.element = element;
        }
    }
}