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
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ArraySizeDetector extends ResourceXmlDetector {

    public static final Issue ISSUE = Issue.create(
            "InconsistentArrays",
            "Inconsistencies in array element counts",
            "When an array is translated in a different locale, it should normally have " +
            "the same number of elements as the original array. When adding or removing " +
            "elements to an array, it is easy to forget to update all the locales, and this " +
            "lint warning finds inconsistencies like these.\n\n" +
            "Note however that there may be cases where you really want to declare a " +
            "different number of array items in each configuration (for example where " +
            "the array represents available options, and those options differ for " +
            "different layout orientations and so on), so use your own judgment to " +
            "decide if this is really an error.\n\n" +
            "You can suppress this error type if it finds false errors in your project.",
            Category.MESSAGES,
            7,
            Severity.WARNING,
            new Implementation(
                    ArraySizeDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    private final Map<String, List<ArrayDeclaration>> mArrays = new HashMap<>();

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("string-array", "integer-array", "array");
    }

    @Override
    public void beforeCheckProject(@NonNull Context context) {
        mArrays.clear();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (context.getResourceFolderType() != ResourceFolderType.VALUES) {
            return;
        }

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

        String folderName = context.file.getParentFile().getName();
        Location location = context.getLocation(element);
        List<ArrayDeclaration> list = mArrays.get(name);
        if (list == null) {
            list = new ArrayList<>();
            mArrays.put(name, list);
        }
        list.add(new ArrayDeclaration(name, count, location, folderName));
    }

    @Override
    public void afterCheckProject(@NonNull Context context) {
        for (Map.Entry<String, List<ArrayDeclaration>> entry : mArrays.entrySet()) {
            List<ArrayDeclaration> decls = entry.getValue();
            if (decls.size() <= 1) {
                continue;
            }

            int firstCount = decls.get(0).count;
            boolean inconsistent = false;
            for (int i = 1; i < decls.size(); i++) {
                if (decls.get(i).count != firstCount) {
                    inconsistent = true;
                    break;
                }
            }

            if (inconsistent) {
                ArrayDeclaration defaultDecl = null;
                for (ArrayDeclaration decl : decls) {
                    if ("values".equals(decl.folderName)) {
                        defaultDecl = decl;
                        break;
                    }
                }
                if (defaultDecl == null) {
                    defaultDecl = decls.get(0);
                }

                int expectedCount = defaultDecl.count;
                String expectedFolder = defaultDecl.folderName;

                for (ArrayDeclaration decl : decls) {
                    if (decl.count != expectedCount) {
                        String message = String.format(
                                "Array `%s` has an inconsistent number of items (%d in %s, but %d in %s)",
                                decl.name, decl.count, decl.folderName, expectedCount, expectedFolder
                        );
                        context.report(ISSUE, decl.location, message);
                    }
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