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
                    5,
                    Severity.WARNING,
                    new Implementation(
                            ArraySizeDetector.class,
                            Scope.RESOURCE_FILE_SCOPE));

    private final java.util.Map<String, java.util.List<ArrayDeclaration>> mArrays = new java.util.HashMap<>();

    @Override
    public boolean appliesTo(@com.android.annotations.NonNull com.android.resources.ResourceFolderType folderType) {
        return folderType == com.android.resources.ResourceFolderType.VALUES;
    }

    @Override
    public java.util.Collection<String> getApplicableElements() {
        return java.util.Arrays.asList("string-array", "integer-array", "array");
    }

    @Override
    public void beforeCheckRootProject(@com.android.annotations.NonNull Context context) {
        mArrays.clear();
    }

    @Override
    public void visitElement(@com.android.annotations.NonNull XmlContext context, @com.android.annotations.NonNull org.w3c.dom.Element element) {
        String name = element.getAttribute("name");
        if (name == null || name.isEmpty()) {
            return;
        }

        int count = 0;
        org.w3c.dom.NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            org.w3c.dom.Node child = children.item(i);
            if (child.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE && "item".equals(child.getNodeName())) {
                count++;
            }
        }

        String folderName = "values";
        if (context.file != null && context.file.getParentFile() != null) {
            folderName = context.file.getParentFile().getName();
        }

        Location location = context.getLocation(element);
        ArrayDeclaration decl = new ArrayDeclaration(name, count, location, folderName);
        
        java.util.List<ArrayDeclaration> list = mArrays.get(name);
        if (list == null) {
            list = new java.util.ArrayList<>();
            mArrays.put(name, list);
        }
        list.add(decl);
    }

    @Override
    public void afterCheckRootProject(@com.android.annotations.NonNull Context context) {
        for (java.util.Map.Entry<String, java.util.List<ArrayDeclaration>> entry : mArrays.entrySet()) {
            java.util.List<ArrayDeclaration> declarations = entry.getValue();
            if (declarations.size() <= 1) {
                continue;
            }

            ArrayDeclaration defaultDecl = null;
            for (ArrayDeclaration decl : declarations) {
                if ("values".equals(decl.folderName)) {
                    defaultDecl = decl;
                    break;
                }
            }
            if (defaultDecl == null) {
                defaultDecl = declarations.get(0);
            }

            for (ArrayDeclaration decl : declarations) {
                if (decl != defaultDecl && decl.count != defaultDecl.count) {
                    String message = String.format(
                            "Array `%1$s` has an inconsistent number of items (%2$d in %3$s, but %4$d in %5$s)",
                            decl.name, decl.count, decl.folderName, defaultDecl.count, defaultDecl.folderName);
                    context.report(ISSUE, decl.location, message);
                }
            }
        }
    }

    private static class ArrayDeclaration {
        final String name;
        final int count;
        final Location location;
        final String folderName;

        ArrayDeclaration(String name, int count, Location location, String folderName) {
            this.name = name;
            this.count = count;
            this.location = location;
            this.folderName = folderName;
        }
    }
}