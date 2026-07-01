package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

public class ArraySizeDetector extends ResourceXmlDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "InconsistentArrays",
                    "Inconsistent array sizes",
                    "When an array is translated in a different locale, it should normally have"
                            + " the same number of elements as the original array. When adding or"
                            + " removing elements to an array, it is easy to forget to update all"
                            + " the locales, and this warning finds inconsistencies like these.\n\n"
                            + "Note however that there may be cases where you really want to declare"
                            + " a different number of array items in each configuration (for example"
                            + " where the array represents available options, and those options differ"
                            + " for different layout orientations and so on), so use your own judgment"
                            + " to decide if this is really an error.\n\n"
                            + "You can suppress this error type if it finds false errors in your"
                            + " project.",
                    Category.CORRECTNESS,
                    3,
                    Severity.WARNING,
                    new Implementation(ArraySizeDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private final java.util.Map<String, java.util.List<ArrayInfo>> mArrays =
            new java.util.HashMap<>();

    @Override
    public boolean appliesTo(com.android.resources.ResourceFolderType folderType) {
        return folderType == com.android.resources.ResourceFolderType.VALUES;
    }

    @Override
    public java.util.Collection<String> getApplicableElements() {
        return java.util.Arrays.asList("array", "string-array", "integer-array");
    }

    @Override
    public void beforeCheckRootProject(Context context) {
        mArrays.clear();
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

        String folder = context.file.getParentFile().getName();
        java.util.List<ArrayInfo> list = mArrays.get(name);
        if (list == null) {
            list = new java.util.ArrayList<>();
            mArrays.put(name, list);
        }
        list.add(new ArrayInfo(context, element, name, folder, count));
    }

    @Override
    public void afterCheckRootProject(Context context) {
        for (java.util.Map.Entry<String, java.util.List<ArrayInfo>> entry : mArrays.entrySet()) {
            java.util.List<ArrayInfo> list = entry.getValue();
            if (allSame(list)) {
                continue;
            }

            StringBuilder counts = new StringBuilder();
            for (ArrayInfo info : list) {
                if (counts.length() > 0) {
                    counts.append(", ");
                }
                counts.append(info.folder).append(" (").append(info.count).append(")");
            }

            for (ArrayInfo info : list) {
                info.context.report(
                        ISSUE,
                        info.element,
                        info.context.getLocation(info.element),
                        "Inconsistent array size for \""
                                + info.name
                                + "\". Array item counts: "
                                + counts.toString());
            }
        }
        mArrays.clear();
    }

    private static boolean allSame(java.util.List<ArrayInfo> list) {
        if (list.size() <= 1) {
            return true;
        }
        int count = list.get(0).count;
        for (int i = 1; i < list.size(); i++) {
            if (list.get(i).count != count) {
                return false;
            }
        }
        return true;
    }

    private static class ArrayInfo {
        final XmlContext context;
        final org.w3c.dom.Element element;
        final String name;
        final String folder;
        final int count;

        ArrayInfo(
                XmlContext context,
                org.w3c.dom.Element element,
                String name,
                String folder,
                int count) {
            this.context = context;
            this.element = element;
            this.name = name;
            this.folder = folder;
            this.count = count;
        }
    }
}