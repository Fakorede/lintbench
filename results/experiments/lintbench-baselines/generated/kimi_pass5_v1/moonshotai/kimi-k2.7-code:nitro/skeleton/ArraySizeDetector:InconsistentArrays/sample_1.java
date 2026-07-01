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

    private static final String EXPLANATION =
            "Array resources should generally have the same number of elements across all "
                    + "locales and configurations. When translating or maintaining arrays, it is "
                    + "easy to add or remove items in one configuration and forget to update the "
                    + "others. This check flags arrays whose element counts differ across resource "
                    + "folders. Note that in some legitimate cases you may intentionally vary the "
                    + "number of items (for example, a list of available options that depends on "
                    + "orientation), so use your judgment before changing the resource.";

    public static final Issue ISSUE =
            Issue.create(
                    "InconsistentArrays",
                    "Inconsistencies in array element counts",
                    EXPLANATION,
                    Category.CORRECTNESS,
                    7,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private Map<String, List<ArrayData>> mArrays;

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.VALUES;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("array", "string-array", "integer-array");
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mArrays = new HashMap<>();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttribute("name");
        if (name == null || name.isEmpty()) {
            return;
        }

        int itemCount = countItems(element);
        Location location = context.getLocation(element);
        List<ArrayData> list = mArrays.computeIfAbsent(name, k -> new ArrayList<>());
        list.add(new ArrayData(name, itemCount, location));
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        for (List<ArrayData> definitions : mArrays.values()) {
            if (definitions.size() < 2) {
                continue;
            }

            if (allSameCount(definitions)) {
                continue;
            }

            int expectedCount = computeExpectedCount(definitions);

            for (ArrayData data : definitions) {
                if (data.count == expectedCount) {
                    continue;
                }

                String message =
                        "Array '"
                                + data.name
                                + "' has "
                                + data.count
                                + " items, but other configurations have "
                                + expectedCount
                                + " items. Arrays should usually have the same number of elements "
                                + "across locales.";
                context.report(ISSUE, data.location, message);
            }
        }
    }

    private static int countItems(@NonNull Element element) {
        int count = 0;
        NodeList children = element.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE
                    && "item".equals(((Element) child).getTagName())) {
                count++;
            }
        }
        return count;
    }

    private static boolean allSameCount(@NonNull List<ArrayData> definitions) {
        int count = definitions.get(0).count;
        for (ArrayData data : definitions) {
            if (data.count != count) {
                return false;
            }
        }
        return true;
    }

    private static int computeExpectedCount(@NonNull List<ArrayData> definitions) {
        Map<Integer, Integer> frequency = new HashMap<>();
        for (ArrayData data : definitions) {
            frequency.put(data.count, frequency.getOrDefault(data.count, 0) + 1);
        }

        int expected = definitions.get(0).count;
        int max = 0;
        for (Map.Entry<Integer, Integer> entry : frequency.entrySet()) {
            if (entry.getValue() > max) {
                max = entry.getValue();
                expected = entry.getKey();
            }
        }
        return expected;
    }

    private static class ArrayData {
        final String name;
        final int count;
        final Location location;

        ArrayData(String name, int count, Location location) {
            this.name = name;
            this.count = count;
            this.location = location;
        }
    }
}