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
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Sets;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public class ArraySizeDetector extends ResourceXmlDetector {
    private static final String TAG_ARRAY = "array";
    private static final String TAG_STRING_ARRAY = "string-array";
    private static final String TAG_INTEGER_ARRAY = "integer-array";
    private static final String TAG_TYPED_ARRAY = "typedArray";
    private static final String ATTR_NAME = "name";
    private static final String TAG_ITEM = "item";

    public static final Issue ISSUE = Issue.create(
            "InconsistentArrays",
            "Inconsistencies in array element counts",
            "When an array is translated in a different locale, it should normally have " +
            "the same number of elements as the original array. When adding or removing " +
            "elements to an array, it is easy to forget to update all the locales, and this " +
            "lint warning finds inconsistencies like these.\n" +
            "\n" +
            "Note however that there may be cases where you really want to declare a " +
            "different number of array items in each configuration (for example where " +
            "the array represents available options, and those options differ for " +
            "different layout orientations and so on), so use your own judgment to " +
            "decide if this is really an error.\n" +
            "\n" +
            "You can suppress this error type if it finds false errors in your project.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(ArraySizeDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private static class ArrayInfo {
        final XmlContext context;
        final Element element;
        final String name;
        final int count;

        ArrayInfo(XmlContext context, Element element, String name, int count) {
            this.context = context;
            this.element = element;
            this.name = name;
            this.count = count;
        }
    }

    private final ListMultimap<String, ArrayInfo> mArrays = ArrayListMultimap.create();

    public ArraySizeDetector() {
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.VALUES;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();
        if (!tag.equals(TAG_ARRAY) && !tag.equals(TAG_STRING_ARRAY)
                && !tag.equals(TAG_INTEGER_ARRAY) && !tag.equals(TAG_TYPED_ARRAY)) {
            return;
        }

        String name = element.getAttribute(ATTR_NAME);
        if (name == null || name.isEmpty()) {
            return;
        }

        int count = 0;
        NodeList children = element.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE
                    && child.getNodeName().equals(TAG_ITEM)) {
                count++;
            }
        }

        mArrays.put(name, new ArrayInfo(context, element, name, count));
    }

    @Override
    public void afterCheckProject(@NonNull Context context) {
        for (String name : mArrays.keySet()) {
            List<ArrayInfo> arrays = mArrays.get(name);
            if (arrays.size() < 2) {
                continue;
            }

            Set<Integer> counts = Sets.newHashSet();
            for (ArrayInfo info : arrays) {
                counts.add(info.count);
            }

            if (counts.size() <= 1) {
                continue;
            }

            for (ArrayInfo info : arrays) {
                String message = String.format(
                        "Array \"%1$s\" has %2$d items in %3$s, which is inconsistent with " +
                        "other configurations that have different counts",
                        info.name,
                        info.count,
                        info.context.file.getParentFile().getName());
                info.context.report(ISSUE, info.element, info.context.getLocation(info.element),
                        message);
            }
        }
    }
}