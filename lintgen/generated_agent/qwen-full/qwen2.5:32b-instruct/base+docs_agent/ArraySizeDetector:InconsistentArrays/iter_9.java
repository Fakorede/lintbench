package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.utils.Pair;
import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.xml.sax.Locator;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

public class ArraySizeDetector extends ResourceXmlDetector {

    private static final String ISSUE_ID = "ArraySize";
    private static final String SHORT_DESCRIPTION = "Inconsistencies in array element counts";

    public static final Issue ISSUE = Issue.create(
            ISSUE_ID,
            SHORT_DESCRIPTION,
            "When an array is translated in a different locale, it should normally have the same number of elements as the original array. When adding or removing elements to an array, it is easy to forget to update all the locales, and this lint warning finds inconsistencies like these.\n\n" +
                    "Note however that there may be cases where you really want to declare a different number of array items in each configuration (for example where the array represents available options, and those options differ for different layout orientations and so on), so use your own judgment to decide if this is really an error.",
            Category.I18N,
            6,
            Severity.WARNING,
            new Implementation(ArraySizeDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public List<String> getApplicableElements() {
        return Collections.singletonList(SdkConstants.TAG_ARRAY);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttribute(SdkConstants.ATTR_NAME);
        if (name != null && name.endsWith("array")) {
            checkArraySize(context, element, name);
        }
    }

    private void checkArraySize(XmlContext context, Element element, String arrayName) {
        Map<String, Integer> sizesByLocale = new HashMap<>();

        for (String locale : getLocales(context)) {
            Element arrayElement = context.getResources(locale).get(SdkConstants.FD_RES_VALUES, "array", arrayName);
            if (arrayElement != null) {
                int size = countArrayItems(arrayElement);
                sizesByLocale.put(locale, size);
            }
        }

        Integer baseSize = countArrayItems(element);
        for (Map.Entry<String, Integer> entry : sizesByLocale.entrySet()) {
            String locale = entry.getKey();
            Integer size = entry.getValue();
            if (!size.equals(baseSize)) {
                reportIssue(context, element,
                        "Array '" + arrayName + "' has inconsistent size across locales: base size is " + baseSize +
                                ", but in locale '" + locale + "' it's " + size);
            }
        }
    }

    private List<String> getLocales(XmlContext context) {
        return context.getConfiguration().getLocales();
    }

    private void reportIssue(@NonNull XmlContext context, @NonNull Element element, String message) {
        context.report(ISSUE, element, context.getLocation(element), message);
    }

    private int countArrayItems(Element element) {
        if (element == null) return 0;
        int size = 0;
        for (org.w3c.dom.Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element && SdkConstants.TAG_ITEM.equals(child.getNodeName())) {
                size++;
            }
        }
        return size;
    }

}