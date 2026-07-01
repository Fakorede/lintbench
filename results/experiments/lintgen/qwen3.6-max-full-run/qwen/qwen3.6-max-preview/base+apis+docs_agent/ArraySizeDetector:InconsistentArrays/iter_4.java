package com.android.tools.lint.checks;

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
import com.android.tools.lint.detector.api.XmlScanner;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ArraySizeDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "InconsistentArrays",
            "Inconsistencies in array element counts",
            "When an array is translated in a different locale, it should normally have the same number of elements as the original array. When adding or removing elements to an array, it is easy to forget to update all the locales, and this lint warning finds inconsistencies like these.\n\n" +
            "Note however that there may be cases where you really want to declare a different number of array items in each configuration (for example where the array represents available options, and those options differ for different layout orientations and so on), so use your own judgment to decide if this is really an error.\n\n" +
            "You can suppress this error type if it finds false errors in your project.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(ArraySizeDetector.class, EnumSet.of(Scope.ALL_RESOURCE_FILES))
    );

    private final Map<String, List<ArrayRecord>> arrays = new HashMap<>();

    private static class ArrayRecord {
        final XmlContext context;
        final Location location;
        final int count;
        final boolean isDefault;

        ArrayRecord(XmlContext context, Location location, int count, boolean isDefault) {
            this.context = context;
            this.location = location;
            this.count = count;
            this.isDefault = isDefault;
        }
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("string-array", "integer-array", "array");
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.VALUES;
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

        String qualifiers = context.getFolderQualifiers();
        boolean isDefault = qualifiers == null || qualifiers.isEmpty();

        arrays.computeIfAbsent(name, k -> new ArrayList<>())
              .add(new ArrayRecord(context, context.getLocation(element), count, isDefault));
    }

    @Override
    public void afterCheckProject(Context context) {
        for (Map.Entry<String, List<ArrayRecord>> entry : arrays.entrySet()) {
            List<ArrayRecord> records = entry.getValue();
            if (records.size() < 2) {
                continue;
            }

            ArrayRecord base = null;
            for (ArrayRecord record : records) {
                if (record.isDefault) {
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
                            "This array has %d elements, but the default array has %d elements",
                            record.count, base.count);
                    record.context.report(ISSUE, record.location, message);
                }
            }
        }
        arrays.clear();
    }
}