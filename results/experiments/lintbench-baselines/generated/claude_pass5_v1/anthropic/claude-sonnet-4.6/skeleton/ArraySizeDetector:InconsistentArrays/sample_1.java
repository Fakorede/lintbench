package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
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
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
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
                    "When an array is translated in a different locale, it should normally have "
                            + "the same number of elements as the original array. When adding or removing "
                            + "elements to an array, it is easy to forget to update all the locales, and this "
                            + "lint warning finds inconsistencies like these.\n"
                            + "\n"
                            + "Note however that there may be cases where you really want to declare a "
                            + "different number of array items in each configuration (for example where "
                            + "the array represents available options, and those options differ for "
                            + "different layout orientations and so on), so use your own judgment to "
                            + "decide if this is really an error.\n"
                            + "\n"
                            + "You can suppress this error type if it finds false errors in your project.",
                    Category.CORRECTNESS,
                    7,
                    Severity.WARNING,
                    IMPLEMENTATION);

    /**
     * Map from array name to a list of (file, count) pairs representing each declaration
     * of that array across different resource folders.
     */
    private Map<String, List<ArrayDeclaration>> mArrayCount;

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.VALUES;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("string-array", "integer-array", "array");
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mArrayCount = new HashMap<>();
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (mArrayCount == null) {
            return;
        }

        for (Map.Entry<String, List<ArrayDeclaration>> entry : mArrayCount.entrySet()) {
            String name = entry.getKey();
            List<ArrayDeclaration> declarations = entry.getValue();

            if (declarations.size() < 2) {
                continue;
            }

            // Find the default (non-locale-qualified) declaration
            ArrayDeclaration defaultDeclaration = null;
            for (ArrayDeclaration decl : declarations) {
                if (isDefaultFolder(decl.file)) {
                    defaultDeclaration = decl;
                    break;
                }
            }

            if (defaultDeclaration == null) {
                // No default declaration; use the first one as reference
                defaultDeclaration = declarations.get(0);
            }

            int defaultCount = defaultDeclaration.count;

            for (ArrayDeclaration decl : declarations) {
                if (decl == defaultDeclaration) {
                    continue;
                }

                if (decl.count != defaultCount) {
                    Location location = decl.location;
                    String message =
                            String.format(
                                    "Array `%1$s` has an inconsistent number of items (%2$d in `%3$s`, %4$d in `%5$s`)",
                                    name,
                                    decl.count,
                                    getParentFolderName(decl.file),
                                    defaultCount,
                                    getParentFolderName(defaultDeclaration.file));
                    context.report(ISSUE, location, message);
                }
            }
        }

        mArrayCount = null;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttribute("name");
        if (name == null || name.isEmpty()) {
            return;
        }

        int count = countItems(element);
        File file = context.file;
        Location location = context.getLocation(element);

        List<ArrayDeclaration> declarations = mArrayCount.get(name);
        if (declarations == null) {
            declarations = new ArrayList<>();
            mArrayCount.put(name, declarations);
        }
        declarations.add(new ArrayDeclaration(file, count, location));
    }

    private static int countItems(Element element) {
        int count = 0;
        NodeList children = element.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                count++;
            }
        }
        return count;
    }

    private static boolean isDefaultFolder(File file) {
        String parentName = getParentFolderName(file);
        // Default values folder has no qualifiers (just "values")
        return parentName.equals("values");
    }

    private static String getParentFolderName(File file) {
        File parent = file.getParentFile();
        if (parent != null) {
            return parent.getName();
        }
        return "";
    }

    private static class ArrayDeclaration {
        final File file;
        final int count;
        final Location location;

        ArrayDeclaration(File file, int count, Location location) {
            this.file = file;
            this.count = count;
            this.location = location;
        }
    }
}