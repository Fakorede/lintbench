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

import java.io.File;
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
            5,
            Severity.WARNING,
            new Implementation(ArraySizeDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    private final Map<String, List<ArrayRecord>> arrayRecords = new HashMap<>();

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
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.VALUES;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("string-array", "integer-array", "array");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
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

        File parent = context.file.getParentFile();
        String folderName = parent != null ? parent.getName() : "values";

        Location location = context.getLocation(element);
        arrayRecords.computeIfAbsent(name, k -> new ArrayList<>())
                    .add(new ArrayRecord(name, count, location, folderName));
    }

    @Override
    public void afterCheckProject(Context context) {
        for (List<ArrayRecord> records : arrayRecords.values()) {
            if (records.size() < 2) {
                continue;
            }

            ArrayRecord base = null;
            for (ArrayRecord record : records) {
                if ("values".equals(record.folderName)) {
                    base = record;
                    break;
                }
            }

            if (base == null) {
                base = records.get(0);
            }

            for (ArrayRecord record : records) {
                if (record == base) {
                    continue;
                }
                if (record.count != base.count) {
                    String message = String.format(
                            "Array \"%s\" has %d elements in %s but %d elements in %s",
                            record.name, record.count, record.folderName, base.count, base.folderName);
                    context.report(ISSUE, record.location, message);
                }
            }
        }
    }
}