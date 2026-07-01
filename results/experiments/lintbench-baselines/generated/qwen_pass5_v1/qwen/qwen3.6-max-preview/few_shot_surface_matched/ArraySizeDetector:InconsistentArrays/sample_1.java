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

    private Map<String, Integer> mDefaultSizes;
    private Map<String, List<QualifiedArray>> mQualifiedArrays;

    private static class QualifiedArray {
        final Element element;
        final XmlContext context;
        final int count;

        QualifiedArray(Element element, XmlContext context, int count) {
            this.element = element;
            this.context = context;
            this.count = count;
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
        mDefaultSizes = new HashMap<>();
        mQualifiedArrays = new HashMap<>();
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

        String parentFolder = context.file.getParentFile().getName();
        if ("values".equals(parentFolder)) {
            mDefaultSizes.put(name, count);
        } else {
            mQualifiedArrays.computeIfAbsent(name, k -> new ArrayList<>())
                    .add(new QualifiedArray(element, context, count));
        }
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        for (Map.Entry<String, List<QualifiedArray>> entry : mQualifiedArrays.entrySet()) {
            String name = entry.getKey();
            Integer defaultSize = mDefaultSizes.get(name);
            if (defaultSize != null) {
                for (QualifiedArray qa : entry.getValue()) {
                    if (qa.count != defaultSize) {
                        qa.context.report(ISSUE, qa.element, qa.context.getLocation(qa.element),
                                String.format("The array \"%s\" has %d elements, but the default array has %d elements",
                                        name, qa.count, defaultSize));
                    }
                }
            }
        }
        mDefaultSizes = null;
        mQualifiedArrays = null;
    }
}