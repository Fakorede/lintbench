package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
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
                            + "the same number of elements as the original array. When adding or "
                            + "removing elements to an array, it is easy to forget to update all "
                            + "the locales, and this lint warning finds inconsistencies like these.\n\n"
                            + "Note however that there may be cases where you really want to declare "
                            + "a different number of array items in each configuration (for example "
                            + "where the array represents available options, and those options "
                            + "differ for different layout orientations and so on), so use your "
                            + "own judgment to decide if this is really an error.\n\n"
                            + "You can suppress this error type if it finds false errors in your project.",
                    Category.CORRECTNESS,
                    7,
                    Severity.WARNING,
                    new Implementation(
                            ArraySizeDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private Map<String, List<ArrayDeclaration>> mArrays;

    private static class ArrayDeclaration {
        final String type;
        final String name;
        final int size;
        final String folderName;
        final Location location;

        ArrayDeclaration(String type, String name, int size, String folderName, Location location) {
            this.type = type;
            this.name = name;
            this.size = size;
            this.folderName = folderName;
            this.location = location;
        }
    }

    @Override
    public boolean appliesTo(@NonNull com.android.resources.ResourceFolderType folderType) {
        return folderType == com.android.resources.ResourceFolderType.VALUES;
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
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttribute("name");
        if (name.isEmpty()) {
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

        String folderName = "";
        if (context.getFolder() != null) {
            folderName = context.getFolder().getName();
        }

        Location location = context.getLocation(element);
        ArrayDeclaration declaration = new ArrayDeclaration(element.getTagName(), name, count, folderName, location);

        String key = element.getTagName() + ":" + name;
        List<ArrayDeclaration> list = mArrays.get(key);
        if (list == null) {
            list = new ArrayList<>();
            mArrays.put(key, list);
        }
        list.add(declaration);
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (mArrays == null) {
            return;
        }

        for (Map.Entry<String, List<ArrayDeclaration>> entry : mArrays.entrySet()) {
            List<ArrayDeclaration> declarations = entry.getValue();
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

            if (defaultDecl != null) {
                int defaultSize = defaultDecl.size;
                for (ArrayDeclaration decl : declarations) {
                    if (decl != defaultDecl && decl.size != defaultSize) {
                        String message = String.format(
                                "Array `%s` has an inconsistent number of items (%d) compared to the default (%d in %s)",
                                decl.name, decl.size, defaultSize, defaultDecl.folderName);
                        context.report(ISSUE, decl.location, message);
                    }
                }
            } else {
                ArrayDeclaration firstDecl = declarations.get(0);
                int firstSize = firstDecl.size;
                for (int i = 1; i < declarations.size(); i++) {
                    ArrayDeclaration decl = declarations.get(i);
                    if (decl.size != firstSize) {
                        String message = String.format(
                                "Array `%s` has an inconsistent number of items (%d) compared to %s (%d)",
                                decl.name, decl.size, firstDecl.folderName, firstSize);
                        context.report(ISSUE, decl.location, message);
                    }
                }
            }
        }
    }
}