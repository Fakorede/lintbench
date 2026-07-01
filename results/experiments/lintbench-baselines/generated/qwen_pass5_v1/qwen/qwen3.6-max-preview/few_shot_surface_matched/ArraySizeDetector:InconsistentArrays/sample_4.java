package com.android.tools.lint.checks;

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
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.TAG_ARRAY;
import static com.android.SdkConstants.TAG_INTEGER_ARRAY;
import static com.android.SdkConstants.TAG_ITEM;
import static com.android.SdkConstants.TAG_STRING_ARRAY;

public class ArraySizeDetector extends ResourceXmlDetector implements XmlScanner {

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
                    6,
                    Severity.WARNING,
                    new Implementation(ArraySizeDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private Map<String, List<ArrayRecord>> arrays;

    private static class ArrayRecord {
        final Location location;
        final int count;

        ArrayRecord(Location location, int count) {
            this.location = location;
            this.count = count;
        }
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
    public void beforeCheckRootProject(@NonNull Context context) {
        arrays = new HashMap<>();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttribute(ATTR_NAME);
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

        arrays.computeIfAbsent(name, k -> new ArrayList<>())
              .add(new ArrayRecord(context.getLocation(element), count));
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (arrays == null) {
            return;
        }

        for (Map.Entry<String, List<ArrayRecord>> entry : arrays.entrySet()) {
            List<ArrayRecord> records = entry.getValue();
            if (records.size() < 2) {
                continue;
            }

            int firstCount = records.get(0).count;
            boolean inconsistent = false;
            for (ArrayRecord record : records) {
                if (record.count != firstCount) {
                    inconsistent = true;
                    break;
                }
            }

            if (inconsistent) {
                String name = entry.getKey();
                for (ArrayRecord record : records) {
                    context.report(ISSUE, record.location,
                            String.format("Array \"%s\" has %d elements, but other configurations have different counts",
                                    name, record.count));
                }
            }
        }
    }
}