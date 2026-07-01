package com.android.tools.lint.checks;

import com.android.SdkConstants;
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
        new Implementation(ArraySizeDetector.class, Scope.ALL_RESOURCES_SCOPE)
    );

    private static class ArrayData {
        final String name;
        final String folderName;
        final int count;
        final Location location;

        ArrayData(String name, String folderName, int count, Location location) {
            this.name = name;
            this.folderName = folderName;
            this.count = count;
            this.location = location;
        }
    }

    private final Map<String, List<ArrayData>> arrays = new HashMap<>();

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.VALUES;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
            SdkConstants.TAG_STRING_ARRAY,
            SdkConstants.TAG_INTEGER_ARRAY,
            SdkConstants.TAG_ARRAY
        );
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String name = element.getAttribute(SdkConstants.ATTR_NAME);
        if (name == null || name.isEmpty()) {
            return;
        }

        int count = 0;
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE &&
                SdkConstants.TAG_ITEM.equals(child.getNodeName())) {
                count++;
            }
        }

        String folderName = context.getResourceFolderName();
        arrays.computeIfAbsent(name, k -> new ArrayList<>())
              .add(new ArrayData(name, folderName, count, context.getLocation(element)));
    }

    @Override
    public void beforeCheckProject(Context context) {
        arrays.clear();
    }

    @Override
    public void afterCheckProject(Context context) {
        for (List<ArrayData> list : arrays.values()) {
            if (list.size() < 2) {
                continue;
            }

            ArrayData defaultData = null;
            for (ArrayData data : list) {
                if (data.folderName.equals(SdkConstants.FD_RES_VALUES)) {
                    defaultData = data;
                    break;
                }
            }

            if (defaultData == null) {
                continue;
            }

            for (ArrayData data : list) {
                if (data == defaultData) {
                    continue;
                }
                if (data.count != defaultData.count) {
                    String message = String.format(
                        "Array \"%s\" has %d elements in %s but %d elements in %s",
                        data.name, data.count, data.folderName, defaultData.count, defaultData.folderName);
                    Location location = data.location;
                    location.setSecondary(defaultData.location);
                    context.report(ISSUE, location, message);
                }
            }
        }
    }
}