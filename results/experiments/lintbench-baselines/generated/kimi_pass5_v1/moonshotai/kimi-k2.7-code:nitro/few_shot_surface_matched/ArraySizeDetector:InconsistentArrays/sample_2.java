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
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class ArraySizeDetector extends ResourceXmlDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "InconsistentArrays",
                    "Inconsistencies in array element counts",
                    "When an array is translated in a different locale, it should normally have "
                            + "the same number of elements as the original array. When adding or "
                            + "removing elements to an array, it is easy to forget to update all "
                            + "the locales, and this lint warning finds inconsistencies like "
                            + "these.\n\n"
                            + "Note however that there may be cases where you really want to declare "
                            + "a different number of array items in each configuration (for example "
                            + "where the array represents available options, and those options differ "
                            + "for different layout orientations and so on), so use your own judgment "
                            + "to decide if this is really an error.\n\n"
                            + "You can suppress this error type if it finds false errors in your project.",
                    Category.CORRECTNESS,
                    5,
                    Severity.WARNING,
                    new Implementation(ArraySizeDetector.class, Scope.ALL_RESOURCES));

    private final Map<String, List<ArrayInfo>> mArrays = new HashMap<>();

    private static class ArrayInfo {
        final String mType;
        final String mName;
        final int mCount;
        final Location mLocation;
        final String mFolder;

        ArrayInfo(String type, String name, int count, Location location, String folder) {
            mType = type;
            mName = name;
            mCount = count;
            mLocation = location;
            mFolder = folder;
        }
    }

    @Override
    public boolean appliesTo(com.android.resources.ResourceFolderType folderType) {
        return folderType == com.android.resources.ResourceFolderType.VALUES;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("string-array", "integer-array", "array");
    }

    @Override
    public void beforeCheckRootProject(Context context) {
        mArrays.clear();
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String tagName = element.getTagName();
        String name = element.getAttribute("name");
        if (name == null || name.isEmpty()) {
            return;
        }

        int count = 0;
        NodeList children = element.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && "item".equals(child.getNodeName())) {
                count++;
            }
        }

        String folderName = context.file.getParentFile().getName();
        String key = tagName + "/" + name;
        List<ArrayInfo> list = mArrays.get(key);
        if (list == null) {
            list = new ArrayList<>();
            mArrays.put(key, list);
        }
        list.add(new ArrayInfo(tagName, name, count, context.getLocation(element), folderName));
    }

    @Override
    public void afterCheckRootProject(Context context) {
        for (List<ArrayInfo> list : mArrays.values()) {
            if (list.size() < 2) {
                continue;
            }

            int defaultCount = -1;
            for (ArrayInfo info : list) {
                if ("values".equals(info.mFolder)) {
                    defaultCount = info.mCount;
                    break;
                }
            }

            if (defaultCount == -1) {
                continue;
            }

            for (ArrayInfo info : list) {
                if (!"values".equals(info.mFolder) && info.mCount != defaultCount) {
                    context.report(
                            ISSUE,
                            info.mLocation,
                            info.mType + " \"" + info.mName + "\" has " + info.mCount
                                    + " items in " + info.mFolder + " but " + defaultCount
                                    + " items in values");
                }
            }
        }
    }
}