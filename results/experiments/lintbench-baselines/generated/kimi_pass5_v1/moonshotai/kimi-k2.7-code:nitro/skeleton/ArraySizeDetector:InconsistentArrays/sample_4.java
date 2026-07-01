package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class ArraySizeDetector extends ResourceXmlDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(ArraySizeDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "InconsistentArrays",
                    "Inconsistencies in array element counts",
                    "When an array is translated in a different locale, it should normally have the same number of elements as the original array. When adding or removing elements to an array, it is easy to forget to update all the locales, and this lint warning finds inconsistencies like these.\n\n"
                            + "Note however that there may be cases where you really want to declare a different number of array items in each configuration (for example where the array represents available options, and those options differ for different layout orientations and so on), so use your own judgment to decide if this is really an error.\n\n"
                            + "You can suppress this error type if it finds false errors in your project.",
                    Category.CORRECTNESS,
                    7,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private Map<String, ArrayInfo> mArrays;

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.VALUES;
    }

    @Override
    public Collection<String> getApplicableElements() {
        Collection<String> elements = new ArrayList<>();
        elements.add("string-array");
        elements.add("integer-array");
        elements.add("array");
        return elements;
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mArrays = new HashMap<>();
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        for (ArrayInfo info : mArrays.values()) {
            if (info.baseCount == -1) {
                continue;
            }
            for (Variant variant : info.variants) {
                if (variant.count != info.baseCount) {
                    String message =
                            String.format(
                                    "Array \"%1$s\" has %2$d items in values but %3$d in %4$s",
                                    info.name, info.baseCount, variant.count, variant.folder);
                    Location location = variant.location;
                    location.setSecondary(info.baseLocation);
                    context.report(ISSUE, location, message);
                }
            }
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttribute("name");
        if (name == null || name.isEmpty()) {
            return;
        }

        String type = element.getTagName();
        String key = type + ":" + name;
        ArrayInfo info = mArrays.get(key);
        if (info == null) {
            info = new ArrayInfo();
            info.name = name;
            info.type = type;
            mArrays.put(key, info);
        }

        int count = countItems(element);
        String folder = context.file.getParentFile().getName();
        Location location = context.getLocation(element);
        Variant variant = new Variant();
        variant.count = count;
        variant.location = location;
        variant.folder = folder;

        if ("values".equals(folder)) {
            info.baseCount = count;
            info.baseLocation = location;
        } else {
            info.variants.add(variant);
        }
    }

    private static int countItems(@NonNull Element element) {
        int count = 0;
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE
                    && "item".equals(child.getNodeName())) {
                count++;
            }
        }
        return count;
    }

    private static class ArrayInfo {
        String name;
        String type;
        int baseCount = -1;
        Location baseLocation;
        List<Variant> variants = new ArrayList<>();
    }

    private static class Variant {
        int count;
        Location location;
        String folder;
    }
}