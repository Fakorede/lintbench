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
                    IMPLEMENTATION);

    /**
     * Map from array name to a list of (file, count) pairs tracking how many
     * items each array declaration has across different resource folders.
     */
    private Map<String, List<ArrayDeclaration>> mArrays;

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
        mArrays = new HashMap<>();
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (mArrays == null || mArrays.isEmpty()) {
            return;
        }

        for (Map.Entry<String, List<ArrayDeclaration>> entry : mArrays.entrySet()) {
            String arrayName = entry.getKey();
            List<ArrayDeclaration> declarations = entry.getValue();

            if (declarations.size() < 2) {
                continue;
            }

            // Find the default (non-locale-specific) declaration if any
            ArrayDeclaration defaultDeclaration = null;
            for (ArrayDeclaration decl : declarations) {
                if (isDefaultFolder(decl.file)) {
                    defaultDeclaration = decl;
                    break;
                }
            }

            if (defaultDeclaration == null) {
                // No default folder declaration; pick the first one as reference
                defaultDeclaration = declarations.get(0);
            }

            int defaultCount = defaultDeclaration.count;

            for (ArrayDeclaration decl : declarations) {
                if (decl == defaultDeclaration) {
                    continue;
                }

                if (decl.count != defaultCount) {
                    String message =
                            String.format(
                                    "Array `%1$s` has an inconsistent number of items (%2$d in `%3$s`, %4$d in `%5$s`)",
                                    arrayName,
                                    decl.count,
                                    getFolderName(decl.file),
                                    defaultCount,
                                    getFolderName(defaultDeclaration.file));
                    context.report(ISSUE, decl.location, message);
                }
            }
        }

        mArrays = null;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttribute("name");
        if (name == null || name.isEmpty()) {
            return;
        }

        // Count child item elements
        int count = 0;
        NodeList children = element.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                count++;
            }
        }

        File file = context.file;
        Location location = context.getLocation(element);

        List<ArrayDeclaration> declarations = mArrays.get(name);
        if (declarations == null) {
            declarations = new ArrayList<>();
            mArrays.put(name, declarations);
        }
        declarations.add(new ArrayDeclaration(file, count, location));
    }

    private static boolean isDefaultFolder(File file) {
        File folder = file.getParentFile();
        if (folder == null) {
            return false;
        }
        String folderName = folder.getName();
        // The default values folder is exactly "values" with no qualifiers
        return "values".equals(folderName);
    }

    private static String getFolderName(File file) {
        File folder = file.getParentFile();
        if (folder != null) {
            return folder.getName();
        }
        return file.getName();
    }

    /** Simple data holder for an array declaration. */
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