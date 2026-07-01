package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ProjectContext;
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

    public static final Issue ISSUE_INCONSISTENT_ARRAYS = Issue.create(
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
            new Implementation(ArraySizeDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private static final String ATTR_NAME = "name";
    private static final String TAG_ITEM = "item";

    private static class ArrayData {
        final String folderName;
        final int count;
        final Location location;
        final XmlContext context;

        ArrayData(String folderName, int count, Location location, XmlContext context) {
            this.folderName = folderName;
            this.count = count;
            this.location = location;
            this.context = context;
        }
    }

    private final Map<String, List<ArrayData>> arrays = new HashMap<>();

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("array", "string-array", "integer-array");
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

        File folder = context.getResourceFolder();
        String folderName = folder != null ? folder.getName() : "unknown";
        Location location = context.getLocation(element);

        arrays.computeIfAbsent(name, k -> new ArrayList<>())
                .add(new ArrayData(folderName, count, location, context));
    }

    @Override
    public void beforeCheckProject(ProjectContext context) {
        arrays.clear();
    }

    @Override
    public void afterCheckProject(ProjectContext context) {
        for (Map.Entry<String, List<ArrayData>> entry : arrays.entrySet()) {
            List<ArrayData> list = entry.getValue();
            if (list.size() < 2) {
                continue;
            }

            int baseSize = -1;
            ArrayData baseData = null;

            for (ArrayData data : list) {
                if (data.folderName.equals("values")) {
                    baseSize = data.count;
                    baseData = data;
                    break;
                }
            }

            if (baseSize == -1) {
                baseData = list.get(0);
                baseSize = baseData.count;
            }

            for (ArrayData data : list) {
                if (data.count != baseSize) {
                    String message = String.format(
                            "Array \"%s\" has %d elements in %s but %d elements in %s",
                            entry.getKey(), data.count, data.folderName, baseSize, baseData.folderName);
                    data.context.report(ISSUE_INCONSISTENT_ARRAYS, data.location, message);
                }
            }
        }
        arrays.clear();
    }
}