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
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
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
                    "When an array resource is translated or otherwise provided in an alternative configuration, "
                            + "it should normally contain the same number of elements as the original. "
                            + "If you add or remove items in one configuration but forget to update the others, "
                            + "the application may behave unexpectedly when running under a different configuration. "
                            + "This check flags arrays whose size varies across resource folders.",
                    Category.CORRECTNESS,
                    7,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private static final Collection<String> APPLICABLE_ELEMENTS =
            Arrays.asList("array", "string-array", "integer-array");

    private Map<String, List<ArrayInfo>> mArrays;

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.VALUES;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return APPLICABLE_ELEMENTS;
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mArrays = new HashMap<>();
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        for (Map.Entry<String, List<ArrayInfo>> entry : mArrays.entrySet()) {
            List<ArrayInfo> list = entry.getValue();
            if (list.size() < 2) {
                continue;
            }

            int firstCount = list.get(0).count;
            boolean consistent = true;
            for (ArrayInfo info : list) {
                if (info.count != firstCount) {
                    consistent = false;
                    break;
                }
            }
            if (consistent) {
                continue;
            }

            for (ArrayInfo info : list) {
                StringBuilder sb = new StringBuilder();
                for (ArrayInfo other : list) {
                    if (other != info && other.count != info.count) {
                        if (sb.length() > 0) {
                            sb.append(", ");
                        }
                        sb.append(other.folderName).append(" has ").append(other.count);
                    }
                }
                String message =
                        String.format(
                                Locale.US,
                                "Array \"%1$s\" has %2$d items in %3$s but other versions have different counts (%4$s)",
                                info.name,
                                info.count,
                                info.folderName,
                                sb.toString());
                context.report(ISSUE, info.location, message);
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
        String key = type + "/" + name;

        int count = countItems(element);
        Location location = context.getLocation(element);
        String folderName = context.file.getParentFile().getName();

        ArrayInfo info = new ArrayInfo(name, count, location, folderName);

        synchronized (mArrays) {
            List<ArrayInfo> list = mArrays.get(key);
            if (list == null) {
                list = new ArrayList<>();
                mArrays.put(key, list);
            }
            list.add(info);
        }
    }

    private static int countItems(Element array) {
        int count = 0;
        NodeList children = array.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && "item".equals(child.getNodeName())) {
                count++;
            }
        }
        return count;
    }

    private static class ArrayInfo {
        final String name;
        final int count;
        final Location location;
        final String folderName;

        ArrayInfo(String name, int count, Location location, String folderName) {
            this.name = name;
            this.count = count;
            this.location = location;
            this.folderName = folderName;
        }
    }
}