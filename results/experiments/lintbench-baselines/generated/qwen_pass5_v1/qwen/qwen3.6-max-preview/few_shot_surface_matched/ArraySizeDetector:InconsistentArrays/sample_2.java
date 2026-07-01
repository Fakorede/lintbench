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

public class ArraySizeDetector extends ResourceXmlDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "InconsistentArrays",
                    "Inconsistent array element counts",
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
                    5,
                    Severity.WARNING,
                    new Implementation(ArraySizeDetector.class, Scope.ALL_RESOURCE_FILES));

    private Map<String, List<ArrayRecord>> arraySizes;

    private static class ArrayRecord {
        final String qualifier;
        final int size;
        final Location location;

        ArrayRecord(String qualifier, int size, Location location) {
            this.qualifier = qualifier;
            this.size = size;
            this.location = location;
        }
    }

    @Override
    public boolean appliesTo(@NonNull Context context, @NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.VALUES;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("array", "string-array", "integer-array");
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        arraySizes = new HashMap<>();
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

        String qualifier = "";
        if (context.getFolderConfiguration() != null) {
            String q = context.getFolderConfiguration().getQualifierString();
            if (q != null) {
                qualifier = q;
            }
        }

        Location location = context.getLocation(element);
        arraySizes.computeIfAbsent(name, k -> new ArrayList<>())
                .add(new ArrayRecord(qualifier, count, location));
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (arraySizes == null) {
            return;
        }

        for (Map.Entry<String, List<ArrayRecord>> entry : arraySizes.entrySet()) {
            List<ArrayRecord> records = entry.getValue();
            if (records.size() < 2) {
                continue;
            }

            int baseSize = -1;
            for (ArrayRecord record : records) {
                if (record.qualifier.isEmpty()) {
                    baseSize = record.size;
                    break;
                }
            }
            if (baseSize == -1) {
                baseSize = records.get(0).size;
            }

            for (ArrayRecord record : records) {
                if (record.size != baseSize) {
                    String configName = record.qualifier.isEmpty() ? "default" : record.qualifier;
                    String message = String.format(
                            "Array \"%s\" has %d elements in configuration \"%s\" but %d in the default configuration",
                            entry.getKey(), record.size, configName, baseSize);
                    context.report(ISSUE, record.location, message);
                }
            }
        }
    }
}