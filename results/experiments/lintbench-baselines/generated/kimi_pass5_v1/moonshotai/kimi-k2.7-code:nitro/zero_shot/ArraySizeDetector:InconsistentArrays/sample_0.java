package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.TAG_ARRAY;
import static com.android.SdkConstants.TAG_INTEGER_ARRAY;
import static com.android.SdkConstants.TAG_ITEM;
import static com.android.SdkConstants.TAG_STRING_ARRAY;

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
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

public class ArraySizeDetector extends ResourceXmlDetector {
    public static final Issue ISSUE = Issue.create(
            "InconsistentArrays",
            "Inconsistencies in array element counts",
            "When an array is translated in a different locale, it should normally have the same number of elements as the original array. When adding or removing elements to an array, it is easy to forget to update all the locales, and this lint warning finds inconsistencies like these.\n\nNote however that there may be cases where you really want to declare a different number of array items in each configuration (for example where the array represents available options, and those options differ for different layout orientations and so on), so use your own judgment to decide if this is really an error.\n\nYou can suppress this error type if it finds false errors in your project.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(ArraySizeDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private final ListMultimap<String, ArrayInfo> mArrays = ArrayListMultimap.create();

    public ArraySizeDetector() {
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.VALUES;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_ARRAY, TAG_STRING_ARRAY, TAG_INTEGER_ARRAY);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
        if (name == null || name.isEmpty()) {
            return;
        }

        int count = 0;
        NodeList children = element.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && TAG_ITEM.equals(child.getNodeName())) {
                count++;
            }
        }

        File file = context.file;
        File parent = file.getParentFile();
        String folderName = parent != null ? parent.getName() : "";
        Location location = context.getLocation(element);
        mArrays.put(name, new ArrayInfo(name, count, location, folderName));
    }

    @Override
    public void afterCheckProject(@NonNull Context context) {
        for (Map.Entry<String, Collection<ArrayInfo>> entry : mArrays.asMap().entrySet()) {
            Collection<ArrayInfo> infos = entry.getValue();
            if (infos.size() < 2) {
                continue;
            }

            int expected = -1;
            boolean consistent = true;
            for (ArrayInfo info : infos) {
                if (expected == -1) {
                    expected = info.count;
                } else if (expected != info.count) {
                    consistent = false;
                    break;
                }
            }

            if (consistent) {
                continue;
            }

            for (ArrayInfo info : infos) {
                String folder = info.folderName.isEmpty() ? "values" : info.folderName;
                String message = String.format(
                        "Array \"%1$s\" has %2$d items in %3$s, which does not match the count in other configurations",
                        info.name, info.count, folder);
                context.report(ISSUE, info.location, message);
            }
        }

        mArrays.clear();
    }

    private static class ArrayInfo {
        final String name;
        final int count;
        final Location location;
        final String folderName;

        ArrayInfo(String name, int count, Location location, String folderName) {
            this.name = name;
            this.count = count;
            this.location = location;
            this.folderName = folderName;
        }
    }
}