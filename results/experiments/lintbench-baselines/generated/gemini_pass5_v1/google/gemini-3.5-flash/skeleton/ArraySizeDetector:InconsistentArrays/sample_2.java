package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class ArraySizeDetector extends ResourceXmlDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(ArraySizeDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
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
                    7,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private final Map<String, List<ArrayInfo>> mArrays = new HashMap<>();

    private static class ArrayInfo {
        final String name;
        final int count;
        final File file;
        final Location location;

        ArrayInfo(String name, int count, File file, Location location) {
            this.name = name;
            this.count = count;
            this.file = file;
            this.location = location;
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
        mArrays.clear();
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        for (Map.Entry<String, List<ArrayInfo>> entry : mArrays.entrySet()) {
            List<ArrayInfo> list = entry.getValue();
            if (list.size() <= 1) {
                continue;
            }

            ArrayInfo defaultArray = null;
            for (ArrayInfo info : list) {
                if (isDefaultFolder(info.file.getParentFile())) {
                    defaultArray = info;
                    break;
                }
            }

            if (defaultArray != null) {
                int defaultCount = defaultArray.count;
                for (ArrayInfo info : list) {
                    if (info.count != defaultCount) {
                        String message = String.format(
                                "Array `%1$s` has an inconsistent number of items (%2$d vs %3$d in %4$s)",
                                info.name, info.count, defaultCount, "default (" + defaultArray.file.getName() + ")");
                        context.report(ISSUE, info.location, message);
                    }
                }
            } else {
                Map<Integer, Integer> counts = new HashMap<>();
                for (ArrayInfo info : list) {
                    counts.put(info.count, counts.getOrDefault(info.count, 0) + 1);
                }
                int majorityCount = -1;
                int maxFrequency = -1;
                for (Map.Entry<Integer, Integer> countEntry : counts.entrySet()) {
                    if (countEntry.getValue() > maxFrequency) {
                        maxFrequency = countEntry.getValue();
                        majorityCount = countEntry.getKey();
                    }
                }

                for (ArrayInfo info : list) {
                    if (info.count != majorityCount) {
                        String message = String.format(
                                "Array `%1$s` has an inconsistent number of items (%2$d vs %3$d in other configurations)",
                                info.name, info.count, majorityCount);
                        context.report(ISSUE, info.location, message);
                    }
                }
            }
        }
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
        Location location = context.getLocation(element);
        List<ArrayInfo> list = mArrays.computeIfAbsent(name, k -> new ArrayList<>());
        list.add(new ArrayInfo(name, count, context.file, location));
    }

    private boolean isDefaultFolder(File parent) {
        return parent != null && "values".equals(parent.getName());
    }
}