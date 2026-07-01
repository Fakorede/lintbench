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
import org.jetbrains.annotations.NotNull;
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

    private static final String KEY = "ArraySizeDetector.Map";

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

    @SuppressWarnings("unchecked")
    private Map<String, List<ArrayData>> getMap(Context context) {
        Map<String, List<ArrayData>> map = (Map<String, List<ArrayData>>) context.getProject().getClientProperty(KEY);
        if (map == null) {
            map = new HashMap<>();
            context.getProject().putClientProperty(KEY, map);
        }
        return map;
    }

    @Override
    public boolean appliesTo(@NotNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.VALUES;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                SdkConstants.TAG_ARRAY,
                SdkConstants.TAG_STRING_ARRAY,
                SdkConstants.TAG_INTEGER_ARRAY
        );
    }

    @Override
    public void visitElement(@NotNull XmlContext context, @NotNull Element element) {
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

        Map<String, List<ArrayData>> map = getMap(context);
        String filePath = context.file.getPath();

        for (List<ArrayData> list : map.values()) {
            list.removeIf(data -> data.filePath.equals(filePath));
        }

        File parent = context.file.getParentFile();
        String folderName = parent != null ? parent.getName() : "";
        Location location = context.getLocation(element);

        map.computeIfAbsent(name, k -> new ArrayList<>())
                .add(new ArrayData(name, count, location, filePath, folderName));
    }

    @Override
    public void afterCheckProject(@NotNull Context context) {
        @SuppressWarnings("unchecked")
        Map<String, List<ArrayData>> map = (Map<String, List<ArrayData>>) context.getProject().getClientProperty(KEY);
        if (map == null) {
            return;
        }

        for (List<ArrayData> list : map.values()) {
            if (list.size() <= 1) {
                continue;
            }

            ArrayData base = null;
            for (ArrayData data : list) {
                if (SdkConstants.FD_RES_VALUES.equals(data.folderName)) {
                    base = data;
                    break;
                }
            }

            if (base == null) {
                base = list.get(0);
            }

            for (ArrayData data : list) {
                if (data == base) {
                    continue;
                }
                if (data.count != base.count) {
                    String message = String.format(
                            "Array `%s` has %d elements in %s but %d elements in %s",
                            data.name, data.count, data.folderName, base.count, base.folderName);

                    context.report(ISSUE, data.location, message);
                }
            }
        }

        context.getProject().putClientProperty(KEY, null);
    }

    private static class ArrayData {
        final String name;
        final int count;
        final Location location;
        final String filePath;
        final String folderName;

        ArrayData(String name, int count, Location location, String filePath, String folderName) {
            this.name = name;
            this.count = count;
            this.location = location;
            this.filePath = filePath;
            this.folderName = folderName;
        }
    }
}