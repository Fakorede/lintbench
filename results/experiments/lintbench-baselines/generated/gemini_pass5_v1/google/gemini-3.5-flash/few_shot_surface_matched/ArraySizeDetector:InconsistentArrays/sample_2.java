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

public class ArraySizeDetector extends ResourceXmlDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "InconsistentArrays",
                    "Inconsistencies in array element counts",
                    "When an array is translated in a different locale, it should normally have "
                            + "the same number of elements as the original array. When adding or "
                            + "removing elements to an array, it is easy to forget to update all "
                            + "the locales, and this lint warning finds inconsistencies like these.\n\n"
                            + "Note however that there may be cases where you really want to declare "
                            + "a different number of array items in each configuration (for example "
                            + "where the array represents available options, and those options differ "
                            + "for different layout orientations and so on), so use your own judgment "
                            + "to decide if this is really an error.\n\n"
                            + "You can suppress this error type if it finds false errors in your project.",
                    Category.CORRECTNESS,
                    6,
                    Severity.WARNING,
                    new Implementation(
                            ArraySizeDetector.class,
                            Scope.RESOURCE_FILE_SCOPE));

    private final java.util.Map<String, java.util.List<ArrayDeclaration>> mArrays = new java.util.HashMap<>();

    @Override
    public boolean appliesTo(com.android.resources.ResourceFolderType folderType) {
        return folderType == com.android.resources.ResourceFolderType.VALUES;
    }

    @Override
    public java.util.Collection<String> getApplicableElements() {
        return java.util.Arrays.asList("string-array", "integer-array", "array");
    }

    @Override
    public void beforeCheckRootProject(Context context) {
        mArrays.clear();
    }

    @Override
    public void visitElement(XmlContext context, org.w3c.dom.Element element) {
        String name = element.getAttribute("name");
        if (name.isEmpty()) {
            return;
        }

        int count = 0;
        org.w3c.dom.NodeList childNodes = element.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            org.w3c.dom.Node child = childNodes.item(i);
            if (child.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE && "item".equals(child.getNodeName())) {
                count++;
            }
        }

        String folderName = context.file.getParentFile().getName();
        Location location = context.getLocation(element);

        java.util.List<ArrayDeclaration> list = mArrays.get(name);
        if (list == null) {
            list = new java.util.ArrayList<>();
            mArrays.put(name, list);
        }
        list.add(new ArrayDeclaration(count, name, folderName, location));
    }

    @Override
    public void afterCheckRootProject(Context context) {
        for (java.util.Map.Entry<String, java.util.List<ArrayDeclaration>> entry : mArrays.entrySet()) {
            java.util.List<ArrayDeclaration> declarations = entry.getValue();
            if (declarations.size() <= 1) {
                continue;
            }

            // Find baseline (default values folder)
            ArrayDeclaration baseline = null;
            for (ArrayDeclaration decl : declarations) {
                if ("values".equals(decl.folder)) {
                    baseline = decl;
                    break;
                }
            }

            if (baseline == null) {
                baseline = declarations.get(0);
            }

            for (ArrayDeclaration decl : declarations) {
                if (decl == baseline) {
                    continue;
                }
                if (decl.count != baseline.count) {
                    String message = String.format(
                            "Array `%1$s` has an inconsistent number of items (%2$d in %3$s but %4$d in %5$s)",
                            decl.name, decl.count, decl.folder, baseline.count, baseline.folder);
                    context.report(ISSUE, decl.location, message);
                }
            }
        }
    }

    private static class ArrayDeclaration {
        final int count;
        final String name;
        final String folder;
        final Location location;

        ArrayDeclaration(int count, String name, String folder, Location location) {
            this.count = count;
            this.name = name;
            this.folder = folder;
            this.location = location;
        }
    }
}