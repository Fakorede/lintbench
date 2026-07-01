package com.android.tools.lint.checks;

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

    private final Map<String, List<ArrayRecord>> arrays = new HashMap<>();

    private static class ArrayRecord {
        final String name;
        final int count;
        final Location location;
        final boolean isBase;

        ArrayRecord(String name, int count, Location location, boolean isBase) {
            this.name = name;
            this.count = count;
            this.location = location;
            this.isBase = isBase;
        }
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("array", "string-array", "integer-array");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String name = element.getAttribute("name");
        if (name == null || name.isEmpty()) {
            return;
        }

        int count = 0;
        for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() == Node.ELEMENT_NODE && "item".equals(child.getNodeName())) {
                count++;
            }
        }

        File parent = context.file.getParentFile();
        String folderName = parent != null ? parent.getName() : "";
        boolean isBase = "values".equals(folderName);

        Location location = context.getLocation(element);
        arrays.computeIfAbsent(name, k -> new ArrayList<>())
                .add(new ArrayRecord(name, count, location, isBase));
    }

    @Override
    public void afterCheckProject(Context context) {
        for (Map.Entry<String, List<ArrayRecord>> entry : arrays.entrySet()) {
            List<ArrayRecord> records = entry.getValue();
            ArrayRecord base = null;
            for (ArrayRecord r : records) {
                if (r.isBase) {
                    base = r;
                    break;
                }
            }

            if (base == null) {
                continue;
            }

            for (ArrayRecord r : records) {
                if (r.isBase) {
                    continue;
                }
                if (r.count != base.count) {
                    String message = String.format(
                            "Array \"%s\" has %d elements, but the default array has %d elements",
                            r.name, r.count, base.count);
                    context.report(ISSUE, r.location, message);
                }
            }
        }
    }
}