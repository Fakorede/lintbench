package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ArraySizeDetector extends Detector implements XmlScanner {

    private static final Issue ISSUE = Issue.create(
            "ArraySizeInconsistency",
            "Inconsistent number of elements in array resource across different locales.",
            "When an array is translated in a different locale, it should normally have the same number of elements as the original array. When adding or removing elements to an array, it is easy to forget to update all the locales, and this lint warning finds inconsistencies like these.\n\n" +
                    "Note however that there may be cases where you really want to declare a different number of array items in each configuration (for example where the array represents available options, and those options differ for different layout orientations and so on), so use your own judgment to decide if this is really an error.",
            Category.I18N,
            5,
            Severity.WARNING,
            new Implementation(ArraySizeDetector.class, Scope.RESOURCE_FILE)
    );

    private final Map<String, Integer> arraySizes = new HashMap<>();

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("array");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String id = getResourceId(element);
        if (id != null) {
            int size = countArrayItems(element);
            Integer previousSize = arraySizes.get(id);
            if (previousSize != null && !previousSize.equals(size)) {
                context.report(
                        ISSUE,
                        element,
                        context.getLocation(element),
                        "Inconsistent number of elements in array resource: " + id
                                + ". Expected " + previousSize + ", found " + size);
            } else {
                arraySizes.put(id, size);
            }
        }
    }

    private String getResourceId(Element element) {
        Attr nameAttr = element.getAttributeNode("name");
        return nameAttr != null ? nameAttr.getValue() : null;
    }

    private int countArrayItems(Element element) {
        int count = 0;
        for (Element child = (Element) element.getFirstChild(); child != null; child = (Element) child.getNextSibling()) {
            if ("item".equals(child.getTagName())) {
                count++;
            }
        }
        return count;
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return ResourceFolderType.VALUES.equals(folderType);
    }
}