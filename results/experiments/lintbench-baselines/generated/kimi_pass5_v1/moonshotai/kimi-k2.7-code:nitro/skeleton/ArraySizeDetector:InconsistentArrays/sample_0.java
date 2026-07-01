package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

public class ArraySizeDetector extends ResourceXmlDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(ArraySizeDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "InconsistentArrays",
                    "Inconsistencies in array element counts",
                    "When an array is translated in a different locale, it should normally have the same number of elements as the original array. When adding or removing elements to an array, it is easy to forget to update all the locales, and this lint warning finds inconsistencies like these.",
                    Category.CORRECTNESS,
                    7,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private java.util.Map<String, java.util.List<ArrayInfo>> mArrays;

    private static class ArrayInfo {
        final int count;
        final String folder;
        final Location location;

        ArrayInfo(int count, String folder, Location location) {
            this.count = count;
            this.folder = folder;
            this.location = location;
        }
    }

    @Override
    public boolean appliesTo(com.android.resources.ResourceFolderType folderType) {
        return folderType == com.android.resources.ResourceFolderType.VALUES;
    }

    @Override
    public java.util.Collection<String> getApplicableElements() {
        java.util.Collection<String> elements = new java.util.ArrayList<>();
        elements.add("array");
        elements.add("string-array");
        elements.add("integer-array");
        return elements;
    }

    @Override
    public void beforeCheckRootProject(Context context) {
        mArrays = new java.util.HashMap<>();
    }

    @Override
    public void visitElement(XmlContext context, org.w3c.dom.Element element) {
        String name = element.getAttribute("name");
        if (name == null || name.isEmpty()) {
            return;
        }

        int count = 0;
        org.w3c.dom.NodeList children = element.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            org.w3c.dom.Node child = children.item(i);
            if (child.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE
                    && "item".equals(child.getNodeName())) {
                count++;
            }
        }

        java.io.File parent = context.getFile().getParentFile();
        String folder = parent != null ? parent.getName() : context.getFile().getName();

        java.util.List<ArrayInfo> list = mArrays.get(name);
        if (list == null) {
            list = new java.util.ArrayList<>();
            mArrays.put(name, list);
        }
        list.add(new ArrayInfo(count, folder, context.getLocation(element)));
    }

    @Override
    public void afterCheckRootProject(Context context) {
        for (java.util.Map.Entry<String, java.util.List<ArrayInfo>> entry : mArrays.entrySet()) {
            java.util.List<ArrayInfo> list = entry.getValue();
            if (list.size() < 2) {
                continue;
            }

            int firstCount = list.get(0).count;
            boolean inconsistent = false;
            for (int i = 1; i < list.size(); i++) {
                if (list.get(i).count != firstCount) {
                    inconsistent = true;
                    break;
                }
            }
            if (!inconsistent) {
                continue;
            }

            StringBuilder sb = new StringBuilder();
            sb.append("Array '").append(entry.getKey()).append("' has inconsistent numbers of items (");
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                ArrayInfo info = list.get(i);
                sb.append(info.count).append(" in ").append(info.folder);
            }
            sb.append(")");
            String message = sb.toString();

            for (ArrayInfo info : list) {
                context.report(ISSUE, info.location, message);
            }
        }
        mArrays = null;
    }
}