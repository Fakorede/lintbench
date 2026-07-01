package com.android.tools.lint.checks;

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
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class ArraySizeDetector extends ResourceXmlDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "InconsistentArrays",
                    "Inconsistent Array Sizes",
                    "When an array is translated in a different locale, it should normally have "
                            + "the same number of elements as the original array. When adding or "
                            + "removing elements to an array, it is easy to forget to update all "
                            + "the locales, and this lint warning finds inconsistencies like "
                            + "these.\n"
                            + "\n"
                            + "Note however that there may be cases where you really want to "
                            + "declare a different number of array items in each configuration "
                            + "(for example where the array represents available options, and "
                            + "those options differ for different layout orientations and so on), "
                            + "so use your own judgment to decide if this is really an error.\n"
                            + "\n"
                            + "You can suppress this error type if it finds false errors in your "
                            + "project.",
                    Category.CORRECTNESS,
                    5,
                    Severity.WARNING,
                    new Implementation(ArraySizeDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private static final String VALUES_FOLDER = "values";

    private Map<String, Map<String, Occurrence>> mArrays;

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.VALUES;
    }

    @Override
    @NonNull
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_ARRAY, TAG_STRING_ARRAY, TAG_INTEGER_ARRAY);
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mArrays = new HashMap<>();
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        for (Map.Entry<String, Map<String, Occurrence>> entry : mArrays.entrySet()) {
            Map<String, Occurrence> occurrences = entry.getValue();
            if (occurrences.size() < 2) {
                continue;
            }
            Occurrence base = occurrences.get(VALUES_FOLDER);
            if (base == null) {
                continue;
            }
            String name = entry.getKey();
            for (Occurrence occurrence : occurrences.values()) {
                if (occurrence.count != base.count
                        && !VALUES_FOLDER.equals(occurrence.folder)) {
                    String message =
                            String.format(
                                    "Array '%1$s' has %2$d entries in %3$s, but %4$d entries in "
                                            + "the default values folder",
                                    name, occurrence.count, occurrence.folder, base.count);
                    context.report(ISSUE, occurrence.location, message);
                }
            }
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttribute(ATTR_NAME);
        if (name.isEmpty()) {
            return;
        }

        int count = 0;
        NodeList children = element.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE
                    && TAG_ITEM.equals(child.getNodeName())) {
                count++;
            }
        }

        String folder = context.file.getParentFile().getName();
        Map<String, Occurrence> occurrences = mArrays.get(name);
        if (occurrences == null) {
            occurrences = new HashMap<>();
            mArrays.put(name, occurrences);
        }
        occurrences.put(folder, new Occurrence(count, context.getLocation(element), folder));
    }

    private static class Occurrence {
        final int count;
        final Location location;
        final String folder;

        Occurrence(int count, Location location, String folder) {
            this.count = count;
            this.location = location;
            this.folder = folder;
        }
    }
}