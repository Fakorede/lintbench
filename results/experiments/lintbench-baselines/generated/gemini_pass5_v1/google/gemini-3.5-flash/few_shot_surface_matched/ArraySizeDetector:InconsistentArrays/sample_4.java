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

public class ArraySizeDetector extends ResourceXmlDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "InconsistentArrays",
                    "Inconsistencies in array element counts",
                    "When an array is translated in a different locale, it should normally have "
                            + "the same number of elements as the original array. When adding or removing "
                            + "elements to an array, it is easy to forget to update all the locales, and this "
                            + "lint warning finds inconsistencies like these.\n\n"
                            + "Note however that there may be cases where you really want to declare a "
                            + "different number of array items in each configuration (for example where "
                            + "the array represents available options, and those options differ for "
                            + "different layout orientations and so on), so use your own judgment to "
                            + "decide if this is really an error.\n\n"
                            + "You can suppress this error type if it finds false errors in your project.",
                    Category.CORRECTNESS,
                    7,
                    Severity.WARNING,
                    new Implementation(
                            ArraySizeDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private final Map<String, List<ArrayDeclaration>> mArrays = new HashMap<>();

    @Override
    public boolean appliesTo(@com.android.annotations.NonNull com.android.resources.ResourceFolderType folderType) {
        return folderType == com.android.resources.ResourceFolderType.VALUES;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("string-array", "integer-array", "array");
    }

    @Override
    public void beforeCheckRootProject(@com.android.annotations.NonNull Context context) {
        mArrays.clear();
    }

    @Override
    public void visitElement(@com.android.annotations.NonNull XmlContext context, @com.android.annotations.NonNull Element element) {
        String name = element.getAttribute("name");
        if (name == null || name.isEmpty()) {
            return;
        }

        int count = 0;
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && "item".equals(child.getNodeName())) {
                count++;
            }
        }

        List<ArrayDeclaration> list = mArrays.get(name);
        if (list == null) {
            list = new ArrayList<>();
            mArrays.put(name, list);
        }
        list.add(new ArrayDeclaration(name, count, context.file, context.getLocation(element)));
    }

    @Override
    public void afterCheckRootProject(@com.android.annotations.NonNull Context context) {
        for (Map.Entry<String, List<ArrayDeclaration>> entry : mArrays.entrySet()) {
            List<ArrayDeclaration> declarations = entry.getValue();
            if (declarations.size() <= 1) {
                continue;
            }

            ArrayDeclaration reference = null;
            for (ArrayDeclaration decl : declarations) {
                if (isDefault(decl)) {
                    reference = decl;
                    break;
                }
            }
            if (reference == null) {
                reference = declarations.get(0);
            }

            for (ArrayDeclaration decl : declarations) {
                if (decl == reference) {
                    continue;
                }
                if (decl.size != reference.size) {
                    String message = String.format(
                            "Array %1$s has an inconsistent number of items (%2$d in %3$s, %4$d in %5$s)",
                            decl.name,
                            decl.size,
                            getFolderAndFileName(decl.file),
                            reference.size,
                            getFolderAndFileName(reference.file));

                    Location location = decl.location;
                    Location secondary = reference.location;
                    if (secondary != null) {
                        location.setSecondary(secondary);
                        secondary.setMessage(String.format("Declaration with %d items", reference.size));
                    }
                    context.report(ISSUE, location, message);
                }
            }
        }
    }

    private boolean isDefault(ArrayDeclaration decl) {
        if (decl.file != null) {
            File parent = decl.file.getParentFile();
            if (parent != null) {
                return "values".equals(parent.getName());
            }
        }
        return false;
    }

    private String getFolderAndFileName(File file) {
        if (file == null) {
            return "unknown";
        }
        File parent = file.getParentFile();
        if (parent != null) {
            return parent.getName() + File.separator + file.getName();
        }
        return file.getName();
    }

    private static class ArrayDeclaration {
        final String name;
        final int size;
        final File file;
        final Location location;

        ArrayDeclaration(String name, int size, File file, Location location) {
            this.name = name;
            this.size = size;
            this.file = file;
            this.location = location;
        }
    }
}