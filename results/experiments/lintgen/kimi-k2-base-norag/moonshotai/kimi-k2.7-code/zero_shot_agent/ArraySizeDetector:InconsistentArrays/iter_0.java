package com.android.tools.lint.checks;

import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.TAG_ARRAY;
import static com.android.SdkConstants.TAG_INTEGER_ARRAY;
import static com.android.SdkConstants.TAG_STRING_ARRAY;

import androidx.annotation.NonNull;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceFolderType;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ArraySizeDetector extends ResourceXmlDetector {

    private static final String TAG_ITEM = "item";
    private static final String DEFAULT_CONFIG = "values";

    private final Map<String, List<ArrayInfo>> mArrayCounts = new HashMap<>();

    public static final Issue ISSUE = Issue.create(
            "InconsistentArrays",
            "Inconsistency in array element counts",
            "When an array is translated in a different locale, it should normally have the same number of elements as the original array. When adding or removing elements to an array, it is easy to forget to update all the locales, and this lint warning finds inconsistencies like these.\n\nNote however that there may be cases where you really want to declare a different number of array items in each configuration (for example where the array represents available options, and those options differ for different layout orientations and so on), so use your own judgment to decide if this is really an error.\n\nYou can suppress this error type if it finds false errors in your project.",
            Category.CORRECTNESS,
            3,
            Severity.WARNING,
            new Implementation(ArraySizeDetector.class, Scope.ALL_RESOURCE_FILES)
    );

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.VALUES;
    }

    @NonNull
    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_STRING_ARRAY, TAG_INTEGER_ARRAY, TAG_ARRAY);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttribute(ATTR_NAME);
        if (name.isEmpty()) {
            return;
        }

        String tag = element.getTagName();
        String key = tag + "/" + name;

        int itemCount = 0;
        NodeList childNodes = element.getChildNodes();
        for (int i = 0, n = childNodes.getLength(); i < n; i++) {
            Node child = childNodes.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && TAG_ITEM.equals(child.getNodeName())) {
                itemCount++;
            }
        }

        String folder = context.file.getParentFile() != null
                ? context.file.getParentFile().getName()
                : DEFAULT_CONFIG;

        List<ArrayInfo> list = mArrayCounts.get(key);
        if (list == null) {
            list = new ArrayList<>();
            mArrayCounts.put(key, list);
        }
        list.add(new ArrayInfo(name, folder, itemCount, context.getLocation(element)));
    }

    @Override
    public void afterCheckProject(@NonNull Context context) {
        for (List<ArrayInfo> arrays : mArrayCounts.values()) {
            int defaultCount = -1;
            for (ArrayInfo info : arrays) {
                if (DEFAULT_CONFIG.equals(info.folder)) {
                    defaultCount = info.count;
                    break;
                }
            }

            if (defaultCount == -1) {
                continue;
            }

            for (ArrayInfo info : arrays) {
                if (info.count != defaultCount) {
                    String message = String.format(
                            "Array \"%1$s\" has %2$d items in %3$s but %4$d items in the default configuration",
                            info.name,
                            info.count,
                            info.folder,
                            defaultCount
                    );
                    context.report(ISSUE, info.location, message);
                }
            }
        }

        mArrayCounts.clear();
    }

    private static class ArrayInfo {
        final String name;
        final String folder;
        final int count;
        final Location location;

        ArrayInfo(String name, String folder, int count, Location location) {
            this.name = name;
            this.folder = folder;
            this.count = count;
            this.location = location;
        }
    }
}