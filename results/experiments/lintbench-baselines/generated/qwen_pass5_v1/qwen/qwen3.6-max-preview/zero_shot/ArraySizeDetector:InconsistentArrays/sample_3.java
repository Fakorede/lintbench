package com.android.tools.lint.checks;

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
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(ArraySizeDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private static final String TAG_STRING_ARRAY = "string-array";
    private static final String TAG_INTEGER_ARRAY = "integer-array";
    private static final String TAG_ARRAY = "array";
    private static final String TAG_ITEM = "item";
    private static final String ATTR_NAME = "name";

    private Map<String, List<ArrayRecord>> mArrays = new HashMap<>();

    private static class ArrayRecord {
        final String name;
        final int count;
        final Location location;
        final String folderName;

        ArrayRecord(String name, int count, Location location, String folderName) {
            this.name = name;
            this.count = count;
            this.location = location;
            this.folderName = folderName;
        }
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_STRING_ARRAY, TAG_INTEGER_ARRAY, TAG_ARRAY);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (context.getResourceFolderType() != ResourceFolderType.VALUES) {
            return;
        }

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

        String folderName = context.file.getParentFile() != null 
                ? context.file.getParentFile().getName() 
                : "values";
                
        List<ArrayRecord> records = mArrays.get(name);
        if (records == null) {
            records = new ArrayList<>();
            mArrays.put(name, records);
        }
        records.add(new ArrayRecord(name, count, context.getLocation(element), folderName));
    }

    @Override
    public void afterCheckProject(Context context) {
        for (List<ArrayRecord> records : mArrays.values()) {
            if (records.size() < 2) {
                continue;
            }

            ArrayRecord defaultRecord = null;
            for (ArrayRecord record : records) {
                if (record.folderName.equals("values")) {
                    defaultRecord = record;
                    break;
                }
            }

            int expectedCount = defaultRecord != null ? defaultRecord.count : records.get(0).count;
            String expectedFolder = defaultRecord != null ? defaultRecord.folderName : records.get(0).folderName;

            for (ArrayRecord record : records) {
                if (record.count != expectedCount) {
                    String message = String.format(
                            "Inconsistent array sizes: expected %1$d (from %2$s), but found %3$d here",
                            expectedCount,
                            expectedFolder,
                            record.count);
                    context.report(ISSUE, record.location, message);
                }
            }
        }
        mArrays.clear();
    }
}