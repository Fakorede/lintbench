package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
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

public class ArraySizeDetector extends Detector implements XmlScanner {

    private static final String ATTR_NAME = "name";
    private static final String TAG_ITEM = "item";

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
            Category.I18N,
            5,
            Severity.WARNING,
            new Implementation(ArraySizeDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    private Map<String, List<ArrayInfo>> arrays = new HashMap<>();

    private static class ArrayInfo {
        final XmlContext context;
        final Element element;
        final int count;
        final String folderName;

        ArrayInfo(XmlContext context, Element element, int count, String folderName) {
            this.context = context;
            this.element = element;
            this.count = count;
            this.folderName = folderName;
        }
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("string-array", "integer-array", "array");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String name = element.getAttribute(ATTR_NAME);
        if (name == null || name.isEmpty()) {
            return;
        }

        int count = countItems(element);
        String folderName = context.getFolderName();
        if (folderName == null) {
            folderName = context.file.getParentFile() != null ? context.file.getParentFile().getName() : "";
        }
        arrays.computeIfAbsent(name, k -> new ArrayList<>()).add(new ArrayInfo(context, element, count, folderName));
    }

    @Override
    public void afterCheckProject(Context context) {
        for (Map.Entry<String, List<ArrayInfo>> entry : arrays.entrySet()) {
            List<ArrayInfo> list = entry.getValue();
            if (list.size() < 2) {
                continue;
            }

            ArrayInfo baseInfo = null;
            for (ArrayInfo info : list) {
                if ("values".equals(info.folderName)) {
                    baseInfo = info;
                    break;
                }
            }

            if (baseInfo == null) {
                continue;
            }

            for (ArrayInfo info : list) {
                if (info == baseInfo) {
                    continue;
                }
                if (info.count != baseInfo.count) {
                    String message = String.format(
                            "Array \"%s\" has %d elements here but %d in values/%s",
                            entry.getKey(), info.count, baseInfo.count, baseInfo.context.file.getName());
                    info.context.report(ISSUE, info.element, message);
                }
            }
        }
        arrays.clear();
    }

    private static int countItems(Element element) {
        int count = 0;
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && TAG_ITEM.equals(child.getNodeName())) {
                count++;
            }
        }
        return count;
    }
}