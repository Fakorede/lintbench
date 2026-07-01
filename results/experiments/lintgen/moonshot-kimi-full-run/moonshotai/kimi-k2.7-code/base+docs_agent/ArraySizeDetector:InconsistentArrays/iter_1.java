package com.android.tools.lint.checks;

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
import com.android.tools.lint.detector.api.XmlScanner;

import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ArraySizeDetector extends Detector implements XmlScanner {

    private static final String TAG_STRING_ARRAY = "string-array";
    private static final String TAG_INTEGER_ARRAY = "integer-array";
    private static final String TAG_ARRAY = "array";
    private static final String TAG_ITEM = "item";
    private static final String ATTR_NAME = "name";

    private final Map<String, Map<String, ArrayInfo>> mArrays = new HashMap<>();

    public static final Issue ISSUE = Issue.create(
            "InconsistentArrays",
            "Inconsistent array sizes",
            "When an array is translated in a different locale, it should normally have the same number of elements as the original array. When adding or removing elements to an array, it is easy to forget to update all the locales, and this lint warning finds inconsistencies like these.\n\nNote however that there may be cases where you really want to declare a different number of array items in each configuration (for example where the array represents available options, and those options differ for different layout orientations and so on), so use your own judgment to decide if this is really an error.\n\nYou can suppress this error type if it finds false errors in your project.",
            Category.I18N,
            3,
            Severity.WARNING,
            new Implementation(ArraySizeDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public void beforeCheckProject(@NotNull Context context) {
        mArrays.clear();
    }

    @Override
    @NotNull
    public List<String> getApplicableElements() {
        return Arrays.asList(TAG_STRING_ARRAY, TAG_INTEGER_ARRAY, TAG_ARRAY);
    }

    @Override
    public void visitElement(@NotNull XmlContext context, @NotNull Element element) {
        if (context.file == null) {
            return;
        }

        ResourceFolderType folderType = ResourceFolderType.getFolderType(context.file.getParentFile());
        if (folderType != ResourceFolderType.VALUES) {
            return;
        }

        String name = element.getAttribute(ATTR_NAME);
        if (name.isEmpty()) {
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

        String project = context.getProject().getName();
        String config = context.file.getParentFile().getName();
        String key = project + "/" + name;

        Map<String, ArrayInfo> configs = mArrays.computeIfAbsent(key, k -> new HashMap<>());
        if (!configs.containsKey(config)) {
            configs.put(config, new ArrayInfo(config, count, context.getLocation(element)));
        }
    }

    @Override
    public void afterCheckProject(@NotNull Context context) {
        for (Map.Entry<String, Map<String, ArrayInfo>> entry : mArrays.entrySet()) {
            Map<String, ArrayInfo> configs = entry.getValue();
            if (configs.size() < 2) {
                continue;
            }

            int expected = -1;
            boolean consistent = true;
            for (ArrayInfo info : configs.values()) {
                if (expected == -1) {
                    expected = info.count;
                } else if (info.count != expected) {
                    consistent = false;
                    break;
                }
            }

            if (consistent) {
                continue;
            }

            String arrayName = entry.getKey().substring(entry.getKey().indexOf('/') + 1);
            for (ArrayInfo info : configs.values()) {
                String message = String.format(Locale.US,
                        "Array \"%s\" has %d items in %s", arrayName, info.count, info.config);
                context.report(ISSUE, info.location, message);
            }
        }
    }

    private static class ArrayInfo {
        final String config;
        final int count;
        final Location location;

        ArrayInfo(String config, int count, Location location) {
            this.config = config;
            this.count = count;
            this.location = location;
        }
    }
}