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
import java.util.EnumSet;
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
     * Map from array name to a list of ArrayDeclaration objects.
     * We collect all array declarations across all resource folders, then
     * compare them at the end.
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
                count++;
            }
        }

        // Determine the folder name (e.g. "values", "values-de", "values-fr")
        File folder = context.file.getParentFile();
        String folderName = folder != null ? folder.getName() : "values";

        Location location = context.getLocation(element);

        List<ArrayDeclaration> declarations = mArrays.get(name);
        if (declarations == null) {
            declarations = new ArrayList<>();
            mArrays.put(name, declarations);
        }
        declarations.add(new ArrayDeclaration(count, location, folderName, context));
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        checkArrays(context);
    }

    private void checkArrays(@NonNull Context context) {
        // Now compare array sizes across configurations
        for (Map.Entry<String, List<ArrayDeclaration>> entry : mArrays.entrySet()) {
            String name = entry.getKey();
            List<ArrayDeclaration> declarations = entry.getValue();

            if (declarations.size() < 2) {
                // Only declared in one configuration, nothing to compare
                continue;
            }

            // Find the reference count (from the default "values" folder if present,
            // otherwise from the first declaration)
            ArrayDeclaration reference = null;
            for (ArrayDeclaration decl : declarations) {
                if (decl.folderName.equals("values")) {
                    reference = decl;
                    break;
                }
            }
            if (reference == null) {
                reference = declarations.get(0);
            }

            int referenceCount = reference.count;

            // Check all other declarations against the reference
            for (ArrayDeclaration decl : declarations) {
                if (decl == reference) {
                    continue;
                }
                if (decl.count != referenceCount) {
                    String message = String.format(
                            "Array `%1$s` has an inconsistent number of items (%2$d in `%3$s`, %4$d in `%5$s`)",
                            name,
                            decl.count,
                            decl.folderName,
                            referenceCount,
                            reference.folderName);
                    context.report(ISSUE, decl.location, message);
                }
            }
        }
        mArrays.clear();
    }

    /** Holds information about a single array declaration in a resource file. */
    private static class ArrayDeclaration {
        final int count;
        final Location location;
        final String folderName;
        final XmlContext xmlContext;

        ArrayDeclaration(int count, Location location, String folderName, XmlContext xmlContext) {
            this.count = count;
            this.location = location;
            this.folderName = folderName;
            this.xmlContext = xmlContext;
        }
    }
}