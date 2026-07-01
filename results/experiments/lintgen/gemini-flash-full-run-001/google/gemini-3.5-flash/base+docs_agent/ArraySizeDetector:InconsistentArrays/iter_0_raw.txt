package com.android.tools.lint.checks;

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

public class ArraySizeDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "InconsistentArrays",
            "Inconsistencies in array element counts",
            "When an array is translated in a different locale, it should normally have "
                    + "the same number of elements as the original array. When adding or "
                    + "removing elements to an array, it is easy to forget to update all the "
                    + "locales, and this lint warning finds inconsistencies like these.\n\n"
                    + "Note however that there may be cases where you really want to declare a "
                    + "different number of array items in each configuration (for example where "
                    + "the array represents available options, and those options differ for "
                    + "different layout orientations and so on), so use your own judgment to "
                    + "decide if this is really an error.\n\n"
                    + "You can suppress this error type if it finds false errors in your project.",
            Category.MESSAGES,
            7,
            Severity.WARNING,
            new Implementation(ArraySizeDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    private final Map<String, List<ArrayDeclaration>> arrays = new HashMap<>();

    @Override
    public void beforeCheckEachProject(Context context) {
        arrays.clear();
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("string-array", "integer-array", "array");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (context.getResourceFolderType() != ResourceFolderType.VALUES) {
            return;
        }

        String name = element.getAttribute("name");
        if (name.isEmpty()) {
            return;
        }

        int childCount = 0;
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && "item".equals(child.getNodeName())) {
                childCount++;
            }
        }

        File parentFile = context.file.getParentFile();
        String folderName = parentFile != null ? parentFile.getName() : "values";
        Location location = context.getLocation(element);

        List<ArrayDeclaration> list = arrays.computeIfAbsent(name, k -> new ArrayList<>());
        list.add(new ArrayDeclaration(folderName, childCount, location));
    }

    @Override
    public void afterCheckEachProject(Context context) {
        for (Map.Entry<String, List<ArrayDeclaration>> entry : arrays.entrySet()) {
            String arrayName = entry.getKey();
            List<ArrayDeclaration> decls = entry.getValue();
            if (decls.size() <= 1) {
                continue;
            }

            int firstSize = decls.get(0).size;
            boolean consistent = true;
            for (ArrayDeclaration decl : decls) {
                if (decl.size != firstSize) {
                    consistent = false;
                    break;
                }
            }

            if (!consistent) {
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

                for (ArrayDeclaration decl : decls) {
                    if (decl == defaultDecl) {
                        continue;
                    }
                    if (decl.size != defaultDecl.size) {
                        String message = String.format(
                                "Array `%1$s` has an inconsistent number of items (%2$d in `%3$s`, but %4$d in `%5$s`)",
                                arrayName,
                                decl.size,
                                decl.folderName,
                                defaultDecl.size,
                                defaultDecl.folderName
                        );
                        Location location = decl.location;
                        if (defaultDecl.location != null) {
                            Location secondary = defaultDecl.location;
                            secondary.setMessage(String.format("Declaration in `%1$s` with %2$d items", defaultDecl.folderName, defaultDecl.size));
                            location.setSecondary(secondary);
                        }
                        context.report(ISSUE, location, message);
                    }
                }
            }
        }
    }

    private static class ArrayDeclaration {
        final String folderName;
        final int size;
        final Location location;

        ArrayDeclaration(String folderName, int size, Location location) {
            this.folderName = folderName;
            this.size = size;
            this.location = location;
        }
    }
}