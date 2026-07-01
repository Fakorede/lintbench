package com.android.tools.lint.checks;

import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.DOT_XML;
import static com.android.SdkConstants.TAG_ARRAY;
import static com.android.SdkConstants.TAG_INTEGER_ARRAY;
import static com.android.SdkConstants.TAG_ITEM;
import static com.android.SdkConstants.TAG_RESOURCES;
import static com.android.SdkConstants.TAG_STRING_ARRAY;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlParser;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ArraySizeDetector extends Detector implements Detector.XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "InconsistentArrays",
            "Inconsistencies in array element counts",
            "When an array is translated in a different locale, it should normally have the same "
                    + "number of elements as the original array. When adding or removing elements "
                    + "to an array, it is easy to forget to update all the locales.\n"
                    + "\n"
                    + "Note however that there may be cases where you really want to declare a "
                    + "different number of array items in each configuration (for example where "
                    + "the array represents available options, and those options differ for "
                    + "different layout orientations and so on), so use your own judgment to "
                    + "decide if this is really an error.\n"
                    + "\n"
                    + "You can suppress this error type if it finds false errors in your project.",
            Category.CORRECTNESS,
            4,
            Severity.WARNING,
            new Implementation(ArraySizeDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    private static final String FOLDER_VALUES = "values";

    private final Map<String, List<ArrayInfo>> mArrays = new HashMap<>();
    private final Set<String> mVisitedPaths = new HashSet<>();

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.VALUES;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_ARRAY, TAG_STRING_ARRAY, TAG_INTEGER_ARRAY);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttribute(ATTR_NAME);
        if (name == null || name.isEmpty()) {
            return;
        }

        int size = countItems(element);
        String folder = context.file.getParentFile().getName();
        String key = element.getNodeName() + "/" + name;

        mVisitedPaths.add(context.file.getPath());

        List<ArrayInfo> list = mArrays.get(key);
        if (list == null) {
            list = new ArrayList<>();
            mArrays.put(key, list);
        }
        list.add(new ArrayInfo(name, size, folder, context.getLocation(element)));
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        Project project = context.getProject();
        XmlParser parser = context.getDriver().getXmlParser();
        if (parser != null && !mArrays.isEmpty()) {
            for (File file : project.getResourceFiles()) {
                if (mVisitedPaths.contains(file.getPath())) {
                    continue;
                }
                if (!file.getName().endsWith(DOT_XML)) {
                    continue;
                }
                File parent = file.getParentFile();
                if (parent == null) {
                    continue;
                }
                if (ResourceFolderType.getFolderType(parent.getName()) != ResourceFolderType.VALUES) {
                    continue;
                }

                Document document;
                try {
                    document = parser.parseXml(file, project);
                } catch (Exception e) {
                    continue;
                }
                if (document == null) {
                    continue;
                }

                Element root = document.getDocumentElement();
                if (root == null || !TAG_RESOURCES.equals(root.getNodeName())) {
                    continue;
                }

                String folder = parent.getName();
                NodeList children = root.getChildNodes();
                for (int i = 0, n = children.getLength(); i < n; i++) {
                    Node node = children.item(i);
                    if (node.getNodeType() != Node.ELEMENT_NODE) {
                        continue;
                    }
                    Element element = (Element) node;
                    String tag = element.getNodeName();
                    if (!TAG_ARRAY.equals(tag)
                            && !TAG_STRING_ARRAY.equals(tag)
                            && !TAG_INTEGER_ARRAY.equals(tag)) {
                        continue;
                    }
                    String arrayName = element.getAttribute(ATTR_NAME);
                    if (arrayName == null || arrayName.isEmpty()) {
                        continue;
                    }
                    String key = tag + "/" + arrayName;
                    List<ArrayInfo> list = mArrays.get(key);
                    if (list == null) {
                        continue;
                    }
                    int size = countItems(element);
                    Location location = parser.getNodeStartLocation(element);
                    list.add(new ArrayInfo(arrayName, size, folder, location));
                }
            }
        }

        for (List<ArrayInfo> list : mArrays.values()) {
            if (list.size() < 2) {
                continue;
            }

            ArrayInfo reference = null;
            for (ArrayInfo info : list) {
                if (FOLDER_VALUES.equals(info.folder)) {
                    reference = info;
                    break;
                }
            }
            if (reference == null) {
                reference = list.get(0);
            }

            for (ArrayInfo info : list) {
                if (info == reference) {
                    continue;
                }
                if (info.size != reference.size) {
                    String message = String.format(
                            "Array \"%1$s\" has %2$d items in %3$s but %4$d in %5$s",
                            info.name,
                            info.size,
                            info.folder,
                            reference.size,
                            reference.folder);
                    context.report(ISSUE, info.location, message);
                }
            }
        }

        mArrays.clear();
        mVisitedPaths.clear();
    }

    private static int countItems(@NonNull Element array) {
        int count = 0;
        NodeList children = array.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE
                    && TAG_ITEM.equals(child.getNodeName())) {
                count++;
            }
        }
        return count;
    }

    private static class ArrayInfo {
        final String name;
        final int size;
        final String folder;
        final Location location;

        ArrayInfo(String name, int size, String folder, Location location) {
            this.name = name;
            this.size = size;
            this.folder = folder;
            this.location = location;
        }
    }
}