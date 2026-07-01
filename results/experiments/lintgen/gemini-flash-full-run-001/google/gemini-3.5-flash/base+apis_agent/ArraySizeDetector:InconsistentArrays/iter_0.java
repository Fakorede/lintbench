package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
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
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class ArraySizeDetector extends ResourceXmlDetector {

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
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    private final Map<String, List<ArrayDeclaration>> mArrays = new HashMap<>();

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.VALUES;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return java.util.Arrays.asList("string-array", "integer-array", "array");
    }

    @Override
    public void beforeCheckProject(Context context) {
        mArrays.clear();
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
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

        ArrayDeclaration decl = new ArrayDeclaration(count, location, folderName, context.file);
        List<ArrayDeclaration> list = mArrays.get(name);
        if (list == null) {
            list = new ArrayList<>();
            mArrays.put(name, list);
        }
        list.add(decl);
    }

    @Override
    public void afterCheckProject(Context context) {
        for (Map.Entry<String, List<ArrayDeclaration>> entry : mArrays.entrySet()) {
            String arrayName = entry.getKey();
            List<ArrayDeclaration> declarations = entry.getValue();
            if (declarations.size() <= 1) {
                continue;
            }

            ArrayDeclaration defaultDecl = null;
            for (ArrayDeclaration decl : declarations) {
                if ("values".equals(decl.folder)) {
                    defaultDecl = decl;
                    break;
                }
            }

            if (defaultDecl != null) {
                for (ArrayDeclaration decl : declarations) {
                    if (decl != defaultDecl && decl.count != defaultDecl.count) {
                        String message = String.format(
                            "Array `%s` has an inconsistent number of items (%d in %s, but %d in %s)",
                            arrayName, decl.count, decl.folder, defaultDecl.count, defaultDecl.folder
                        );
                        context.report(ISSUE, decl.location, message);
                    }
                }
            } else {
                ArrayDeclaration firstDecl = declarations.get(0);
                for (int i = 1; i < declarations.size(); i++) {
                    ArrayDeclaration decl = declarations.get(i);
                    if (decl.count != firstDecl.count) {
                        String message = String.format(
                            "Array `%s` has an inconsistent number of items (%d in %s, but %d in %s)",
                            arrayName, decl.count, decl.folder, firstDecl.count, firstDecl.folder
                        );
                        context.report(ISSUE, decl.location, message);
                    }
                }
            }
        }
    }

    private static class ArrayDeclaration {
        final int count;
        final Location location;
        final String folder;
        final File file;

        ArrayDeclaration(int count, Location location, String folder, File file) {
            this.count = count;
            this.location = location;
            this.folder = folder;
            this.file = file;
        }
    }
}