package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Category;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

public class ArraySizeDetector extends Detector implements XmlScanner {
    private static final Issue ISSUE = Issue.create(
            "ArraySizeInconsistency",
            "Inconsistencies in array element counts",
            "When an array is translated in a different locale, it should normally have the same number of elements as the original array. When adding or removing elements to an array, it is easy to forget to update all the locales, and this lint warning finds inconsistencies like these.\n" +
                    "\n" +
                    "Note however that there may be cases where you really want to declare a different number of array items in each configuration (for example where the array represents available options, and those options differ for different layout orientations and so on), so use your own judgment to decide if this is really an error.\n",
            Category.I18N,
            5,
            Severity.WARNING,
            new Implementation(ArraySizeDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    private final Map<String, Integer> arraySizes = new HashMap<>();

    @Override
    public Issue getIssue() {
        return ISSUE;
    }

    @Override
    public List<String> getApplicableElements() {
        return Collections.singletonList("array");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String arrayName = element.getAttribute("name");

        int itemCount = 0;
        for (org.w3c.dom.Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            if ("item".equals(child.getNodeName())) {
                itemCount++;
            }
        }

        Integer originalSize = arraySizes.put(arrayName, itemCount);
        if (originalSize != null && !originalSize.equals(itemCount)) {
            context.report(ISSUE, element, context.getLocation(element),
                    "Array '" + arrayName + "' has inconsistent item count across locales");
        }
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return ResourceFolderType.VALUES.equals(folderType);
    }
}