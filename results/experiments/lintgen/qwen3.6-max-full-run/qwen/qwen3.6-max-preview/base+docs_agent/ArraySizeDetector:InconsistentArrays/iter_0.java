package com.android.tools.lint.checks;

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
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ArraySizeDetector extends Detector implements XmlScanner {

    private static final String ATTR_NAME = "name";
    private static final String TAG_ITEM = "item";

    private final Map<String, List<ArrayRecord>> mArrays = new HashMap<>();

    private static class ArrayRecord {
        final String folderName;
        final int count;
        final Location location;

        ArrayRecord(String folderName, int count, Location location) {
            this.folderName = folderName;
            this.count = count;
            this.location = location;
        }
    }

    public static final Issue ISSUE = Issue.create(
            "InconsistentArrays",
            "Inconsistencies in array element counts",
            "When an array is translated in a different locale, it should normally have " +
            "the same number of elements as the original array. When adding or removing " +
            "elements to an array, it is easy to forget to update all the locales, and this " +
            "lint warning finds inconsistencies like these.\n\n" +
            "Note however that there may be cases where you really want to declare a " +
            "different number of array items in each configuration (for example where " +
            "the array represents available options, and those options differ for " +
            "different layout orientations and so on), so use your own judgment to " +
            "decide if this is really an error.\n\n" +
            "You can suppress this error type if it finds false errors in your project.",
            Category.I18N,
            5,
            Severity.WARNING,
            new Implementation(ArraySizeDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("string-array", "integer-array", "array");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String name = element.getAttribute(ATTR_NAME);
        if (name == null || name.isEmpty()) {
            return;
        }

        int count = 0;
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && TAG_ITEM.equals(child.getNodeName())) {
                count++;
            }
        }

        String folderName = context.getResourceFolder() != null ? context.getResourceFolder().getName() : "values";
        Location location = context.getLocation(element);
        mArrays.computeIfAbsent(name, k -> new ArrayList<>())
               .add(new ArrayRecord(folderName, count, location));
    }

    @Override
    public void afterCheckProject(Context context) {
        for (Map.Entry<String, List<ArrayRecord>> entry : mArrays.entrySet()) {
            List<ArrayRecord> records = entry.getValue();
            if (records.size() < 2) {
                continue;
            }

            int baseCount = -1;
            ArrayRecord baseRecord = null;

            for (ArrayRecord record : records) {
                if (record.folderName.equals("values")) {
                    baseCount = record.count;
                    baseRecord = record;
                    break;
                }
            }

            if (baseRecord == null) {
                baseRecord = records.get(0);
                baseCount = baseRecord.count;
            }

            for (ArrayRecord record : records) {
                if (record.count != baseCount) {
                    String message = String.format(
                            "Array \"%s\" has %d elements here but %d in %s",
                            entry.getKey(), record.count, baseCount, baseRecord.folderName);
                    context.report(ISSUE, record.location, message);
                }
            }
        }
    }
}