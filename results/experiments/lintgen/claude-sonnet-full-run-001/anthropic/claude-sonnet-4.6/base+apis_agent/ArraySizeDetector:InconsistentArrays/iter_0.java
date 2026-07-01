package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
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
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Attr;
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

/**
 * Checks for inconsistencies in array sizes across different resource configurations/locales.
 */
public class ArraySizeDetector extends Detector implements XmlScanner {

    /** The main issue discovered by this detector */
    public static final Issue ISSUE = Issue.create(
            "InconsistentArrays",
            "Inconsistencies in array element counts",
            "When an array is translated in a different locale, it should normally have " +
            "the same number of elements as the original array. When adding or removing " +
            "elements to an array, it is easy to forget to update all the locales, and this " +
            "lint warning finds inconsistencies like these.\n" +
            "\n" +
            "Note however that there may be cases where you really want to declare a " +
            "different number of array items in each configuration (for example where " +
            "the array represents available options, and those options differ for " +
            "different layout orientations and so on), so use your own judgment to " +
            "decide if this is really an error.\n" +
            "\n" +
            "You can suppress this error type if it finds false errors in your project.",
            Category.CORRECTNESS,
            7,
            Severity.WARNING,
            new Implementation(
                    ArraySizeDetector.class,
                    Scope.ALL_RESOURCES_SCOPE));

    /**
     * Map from array name to a list of (file, count) pairs recording how many
     * items each declaration of that array has.
     */
    private final Map<String, List<ArrayDeclaration>> mArrays = new HashMap<>();

    /** Constructs a new {@link ArraySizeDetector} */
    public ArraySizeDetector() {
    }

    // ---- Implements XmlScanner ----

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.VALUES;
    }

    @Override
    @Nullable
    public Collection<String> getApplicableElements() {
        return Arrays.asList("string-array", "integer-array", "array");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        Attr nameAttr = element.getAttributeNode("name");
        if (nameAttr == null) {
            return;
        }
        String name = nameAttr.getValue();
        if (name.isEmpty()) {
            return;
        }

        // Count the number of <item> children
        int count = 0;
        NodeList children = element.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                String tagName = ((Element) child).getTagName();
                if ("item".equals(tagName)) {
                    count++;
                }
            }
        }

        List<ArrayDeclaration> declarations = mArrays.get(name);
        if (declarations == null) {
            declarations = new ArrayList<>();
            mArrays.put(name, declarations);
        }

        Location location = context.getLocation(element);
        declarations.add(new ArrayDeclaration(context.file, count, location));
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        // Now check all arrays for inconsistencies
        for (Map.Entry<String, List<ArrayDeclaration>> entry : mArrays.entrySet()) {
            String name = entry.getKey();
            List<ArrayDeclaration> declarations = entry.getValue();

            if (declarations.size() < 2) {
                // Only one declaration, nothing to compare
                continue;
            }

            // Find the most common count (use the first/default as reference)
            // We'll use the declaration with the smallest folder depth as the "default"
            // Actually, let's find the default (values/ without qualifiers) declaration
            ArrayDeclaration defaultDecl = findDefaultDeclaration(declarations);
            if (defaultDecl == null) {
                defaultDecl = declarations.get(0);
            }

            int defaultCount = defaultDecl.count;

            // Check all other declarations against the default
            for (ArrayDeclaration decl : declarations) {
                if (decl == defaultDecl) {
                    continue;
                }
                if (decl.count != defaultCount) {
                    // Report the inconsistency on the non-default file
                    String message = String.format(
                            "Array `%1$s` has an inconsistent number of items (%2$d in `%3$s`, %4$d in `%5$s`)",
                            name,
                            decl.count,
                            getRelativePath(decl.file),
                            defaultCount,
                            getRelativePath(defaultDecl.file));

                    context.report(ISSUE, decl.location, message);
                }
            }
        }
    }

    /**
     * Finds the "default" declaration — the one in the plain {@code values/} folder
     * (i.e. the parent folder name is exactly "values").
     */
    @Nullable
    private static ArrayDeclaration findDefaultDeclaration(@NonNull List<ArrayDeclaration> declarations) {
        for (ArrayDeclaration decl : declarations) {
            File parentFolder = decl.file.getParentFile();
            if (parentFolder != null && parentFolder.getName().equals("values")) {
                return decl;
            }
        }
        return null;
    }

    /**
     * Returns a short relative-style path for display purposes.
     * Shows "foldername/filename".
     */
    @NonNull
    private static String getRelativePath(@NonNull File file) {
        File parent = file.getParentFile();
        if (parent != null) {
            return parent.getName() + "/" + file.getName();
        }
        return file.getName();
    }

    /** Holds information about a single array declaration. */
    private static class ArrayDeclaration {
        /** The file containing this declaration */
        @NonNull
        final File file;

        /** The number of {@code <item>} elements in this array */
        final int count;

        /** The location of the array element */
        @NonNull
        final Location location;

        ArrayDeclaration(@NonNull File file, int count, @NonNull Location location) {
            this.file = file;
            this.count = count;
            this.location = location;
        }
    }
}