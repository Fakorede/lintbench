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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ArraySizeDetector extends Detector implements Detector.XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "InconsistentArrays",
            "Inconsistent number of elements in arrays",
            "When an array is translated in a different locale, it should normally have "
                    + "the same number of elements as the original array. When adding or removing "
                    + "elements to an array, it is easy to forget to update all the locales, and "
                    + "this lint warning finds inconsistencies like these.\n\n"
                    + "Note however that there may be cases where you really want to declare a "
                    + "different number of array items in each configuration (for example where "
                    + "the array represents available options, and those options differ for "
                    + "different layout orientations and so on), so use your own judgment to "
                    + "decide if this is really an error.\n\n"
                    + "You can suppress this error type if it finds false errors in your project.",
            Category.MESSAGES,
            5,
            Severity.WARNING,
            new Implementation(ArraySizeDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    private Map<String, List<ArrayEntry>> mArrays;

    @Override
    public void beforeCheckEachProject(@NonNull Context context) {
        mArrays = new HashMap<>();
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.VALUES;
    }

    @Override
    @NonNull
    public List<String> getApplicableElements() {
        return Arrays.asList(
                SdkConstants.TAG_ARRAY,
                SdkConstants.TAG_STRING_ARRAY,
                SdkConstants.TAG_INTEGER_ARRAY
        );
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttribute(SdkConstants.ATTR_NAME);
        if (name == null || name.isEmpty()) {
            return;
        }

        int count = 0;
        NodeList children = element.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE
                    && SdkConstants.TAG_ITEM.equals(child.getNodeName())) {
                count++;
            }
        }

        String folder = context.file.getParentFile().getName();
        Location location = context.getElementLocation(element);

        String key = name + "/" + element.getTagName();
        List<ArrayEntry> entries = mArrays.get(key);
        if (entries == null) {
            entries = new ArrayList<>();
            mArrays.put(key, entries);
        }
        entries.add(new ArrayEntry(name, element.getTagName(), folder, count, location));
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        for (List<ArrayEntry> entries : mArrays.values()) {
            if (entries.size() <= 1) {
                continue;
            }

            ArrayEntry baseline = null;
            for (ArrayEntry entry : entries) {
                if ("values".equals(entry.folder)) {
                    baseline = entry;
                    break;
                }
            }
            if (baseline == null) {
                baseline = entries.get(0);
            }

            for (ArrayEntry entry : entries) {
                if (entry.count != baseline.count) {
                    String message = String.format(
                            "Array '%1$s' has %2$d items in %3$s, expected %4$d (%5$s)",
                            entry.name,
                            entry.count,
                            entry.folder,
                            baseline.count,
                            baseline.folder
                    );
                    context.report(ISSUE, entry.location, message);
                }
            }
        }
    }

    private static class ArrayEntry {
        final String name;
        final String tag;
        final String folder;
        final int count;
        final Location location;

        ArrayEntry(String name, String tag, String folder, int count, Location location) {
            this.name = name;
            this.tag = tag;
            this.folder = folder;
            this.count = count;
            this.location = location;
        }
    }
}