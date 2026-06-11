package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.utils.Pair;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.HashMap;
import java.util.Map;

public class ArraySizeDetector extends ResourceXmlScanner {

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
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(SdkConstants.ATTR_NAME);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Element element, @NonNull Attr attribute) {
        String name = attribute.getValue();
        if (name != null && name.endsWith("array")) {
            checkArraySize(context, element, name);
        }
    }

    private void checkArraySize(XmlContext context, Element element, String arrayName) {
        Multimap<String, Integer> sizesByLocale = HashMultimap.create();

        Map<String, Pair<Element, Integer>> arraysByName = new HashMap<>();
        for (ResourceFolderType folder : ResourceFolderType.values()) {
            if (folder == ResourceFolderType.VALUES) {
                continue;
            }
            String locale = folder.getQualifierValue();
            Element arrayElement = context.getResourceRepository().getResource(folder, SdkConstants.FD_RES_VALUES, "array", arrayName);
            if (arrayElement != null) {
                int size = countArrayItems(arrayElement);
                sizesByLocale.put(locale, size);
                arraysByName.put(locale + "_" + arrayName, new Pair<>(arrayElement, size));
            }
        }

        Integer baseSize = countArrayItems(element);
        for (String locale : sizesByLocale.keySet()) {
            if (!sizesByLocale.get(locale).contains(baseSize)) {
                reportIssue(context, element, arraysByName.get(locale + "_" + arrayName).first,
                        "Array '" + arrayName + "' has inconsistent size across locales: base size is " + baseSize +
                                ", but in locale '" + locale + "' it's " + sizesByLocale.get(locale));
            }
        }
    }

    private void reportIssue(XmlContext context, Element baseElement, Element otherElement,
                             String message) {
        context.report(ISSUE, baseElement, context.getLocation(baseElement),
                message);
        context.report(ISSUE, otherElement, context.getLocation(otherElement),
                "Corresponding array in another locale");
    }

    private int countArrayItems(Element element) {
        if (element == null) return 0;
        int size = 0;
        for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element && SdkConstants.TAG_ITEM.equals(child.getNodeName())) {
                size++;
            }
        }
        return size;
    }

}