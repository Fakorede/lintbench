package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
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

    public static final Issue ISSUE =
            Issue.create(
                    "InconsistentArrays",
                    "Inconsistencies in array element counts",
                    "When an array is translated in a different locale, it should normally have "
                            + "the same number of elements as the original array. When adding or "
                            + "removing elements to an array, it is easy to forget to update all "
                            + "the locales, and this lint warning finds inconsistencies like these."
                            + "\n\nNote however that there may be cases where you really want to "
                            + "declare a different number of array items in each configuration "
                            + "(for example where the array represents available options, and "
                            + "those options differ for different layout orientations and so on), "
                            + "so use your own judgment to decide if this is really an error.\n\n"
                            + "You can suppress this error type if it finds false errors in your "
                            + "project.",
                    Category.CORRECTNESS,
                    7,
                    Severity.WARNING,
                    new Implementation(ArraySizeDetector.class, Scope.ALL_RESOURCES_SCOPE));

    /** Map from array name to a list of pairs (context, count) */
    private Map<String, List<ArrayDeclaration>> mArrays;

    public ArraySizeDetector() {}

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
    public void beforeCheckRootProject(@NonNull com.android.tools.lint.detector.api.Project project) {
        mArrays = new HashMap<>();
    }

    @Override
    public void afterCheckRootProject(@NonNull com.android.tools.lint.detector.api.Project project) {
        if (mArrays == null) {
            return;
        }

        for (Map.Entry<String, List<ArrayDeclaration>> entry : mArrays.entrySet()) {
            List<ArrayDeclaration> declarations = entry.getValue();
            if (declarations.size() < 2) {
                continue;
            }

            // Find the default (non-locale-qualified) declaration if possible,
            // otherwise use the first declaration as reference.
            ArrayDeclaration reference = null;
            for (ArrayDeclaration decl : declarations) {
                String folderName = decl.context.file.getParentFile().getName();
                // Default values folder has no qualifiers beyond "values"
                if (folderName.equals("values")) {
                    reference = decl;
                    break;
                }
            }
            if (reference == null) {
                reference = declarations.get(0);
            }

            int referenceCount = reference.count;
            String referenceFolderName = reference.context.file.getParentFile().getName();

            for (ArrayDeclaration decl : declarations) {
                if (decl == reference) {
                    continue;
                }
                if (decl.count != referenceCount) {
                    String folderName = decl.context.file.getParentFile().getName();
                    String message =
                            String.format(
                                    "Array `%1$s` has an inconsistent number of items (%2$d in "
                                            + "`%3$s/res/%4$s`, %5$d in `%6$s/res/%7$s`)",
                                    entry.getKey(),
                                    decl.count,
                                    decl.context.getProject().getName(),
                                    folderName,
                                    referenceCount,
                                    reference.context.getProject().getName(),
                                    referenceFolderName);
                    decl.context.report(
                            ISSUE,
                            decl.element,
                            decl.context.getLocation(decl.element),
                            message);
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

        int count = countItems(element);

        List<ArrayDeclaration> declarations = mArrays.get(name);
        if (declarations == null) {
            declarations = new ArrayList<>();
            mArrays.put(name, declarations);
        }
        declarations.add(new ArrayDeclaration(context, element, count));
    }

    private static int countItems(@NonNull Element element) {
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

    private static class ArrayDeclaration {
        final XmlContext context;
        final Element element;
        final int count;

        ArrayDeclaration(@NonNull XmlContext context, @NonNull Element element, int count) {
            this.context = context;
            this.element = element;
            this.count = count;
        }
    }
}