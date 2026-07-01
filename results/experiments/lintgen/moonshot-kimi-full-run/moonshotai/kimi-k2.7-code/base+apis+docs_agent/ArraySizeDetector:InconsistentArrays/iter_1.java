package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

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

public class ArraySizeDetector extends Detector implements Detector.XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "InconsistentArrays",
            "Inconsistencies in array element counts",
            "When an array is translated in a different locale, it should normally have the same "
                    + "number of elements as the original array. When adding or removing elements "
                    + "to an array, it is easy to forget to update all the locales.\n"
                    + "\n"
                    + "Note however that there may be cases where you really want to declare a "
                    + "different number of array items in each configuration (for example where "
                    + "the array represents available options, and those options differ for "
                    + "different layout orientations and so on), so use your own judgment to "
                    + "decide if this is really an error.\n"
                    + "\n"
                    + "You can suppress this error type if it finds false errors in your project.",
            Category.CORRECTNESS,
            4,
            Severity.WARNING,
            new Implementation(ArraySizeDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    private static final String TAG_ARRAY = "array";
    private static final String TAG_STRING_ARRAY = "string-array";
    private static final String TAG_INTEGER_ARRAY = "integer-array";
    private static final String TAG_ITEM = "item";
    private static final String FOLDER_VALUES = "values";

    private final Map<String, List<Variant>> mArrays = new HashMap<>();

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.VALUES;
    }

    @Override
    public @NonNull Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_ARRAY, TAG_STRING_ARRAY, TAG_INTEGER_ARRAY);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttribute(SdkConstants.ATTR_NAME);
        if (name == null || name.isEmpty()) {
            return;
        }

        int size = countItems(element);

        File parent = context.file.getParentFile();
        String folder = parent != null ? parent.getName() : FOLDER_VALUES;

        String key = element.getNodeName() + "/" + name;

        List<Variant> list = mArrays.get(key);
        if (list == null) {
            list = new ArrayList<>();
            mArrays.put(key, list);
        }
        list.add(new Variant(name, size, folder, context.createLocationHandle(element)));
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        for (List<Variant> list : mArrays.values()) {
            if (list.size() < 2) {
                continue;
            }

            Variant reference = null;
            for (Variant variant : list) {
                if (FOLDER_VALUES.equals(variant.folder)) {
                    reference = variant;
                    break;
                }
            }
            if (reference == null) {
                reference = list.get(0);
            }

            for (Variant variant : list) {
                if (variant == reference) {
                    continue;
                }
                if (variant.size != reference.size) {
                    String message = String.format(
                            "Array \"%1$s\" has %2$d items in %3$s but %4$d in %5$s",
                            variant.name,
                            variant.size,
                            variant.folder,
                            reference.size,
                            reference.folder);
                    context.report(ISSUE, variant.handle.resolve(), message);
                }
            }
        }

        mArrays.clear();
    }

    private static int countItems(@NonNull Element array) {
        int count = 0;
        NodeList children = array.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE
                    && TAG_ITEM.equals(child.getNodeName())) {
                count++;
            }
        }
        return count;
    }

    private static class Variant {
        final String name;
        final int size;
        final String folder;
        final Location.Handle handle;

        Variant(String name, int size, String folder, Location.Handle handle) {
            this.name = name;
            this.size = size;
            this.folder = folder;
            this.handle = handle;
        }
    }
}