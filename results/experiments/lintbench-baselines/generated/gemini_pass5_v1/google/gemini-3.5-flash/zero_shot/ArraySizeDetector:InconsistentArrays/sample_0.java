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
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
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
            "lint warning finds inconsistencies like these.\n\n" +
            "Note however that there may be cases where you really want to declare a " +
            "different number of array items in each configuration (for example where " +
            "the array represents available options, and those options differ for " +
            "different layout orientations and so on), so use your own judgment to " +
            "decide if this is really an error.\n\n" +
            "You can suppress this error type if it finds false errors in your project.",
            Category.CORRECTNESS,
            7,
            Severity.WARNING,
            new Implementation(
                    ArraySizeDetector.class,
                    Scope.ALL_RESOURCES_SCOPE
            )
    );

    private final Map<String, Map<String, ArrayDeclaration>> mArrays = new HashMap<>();

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.VALUES;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("string-array", "integer-array", "array");
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

        File parentFile = context.file.getParentFile();
        String folderName = parentFile != null ? parentFile.getName() : "values";

        Map<String, ArrayDeclaration> declarations = mArrays.get(name);
        if (declarations == null) {
            declarations = new HashMap<>();
            mArrays.put(name, declarations);
        }
        Location location = context.getLocation(element);
        declarations.put(folderName, new ArrayDeclaration(count, location, folderName));
    }

    @Override
    public void afterCheckRootProject(Context context) {
        checkArrays(context);
    }

    private void checkArrays(Context context) {
        for (Map.Entry<String, Map<String, ArrayDeclaration>> entry : mArrays.entrySet()) {
            String arrayName = entry.getKey();
            Map<String, ArrayDeclaration> declarations = entry.getValue();

            if (declarations.size() <= 1) {
                continue;
            }

            ArrayDeclaration defaultDecl = declarations.get("values");
            if (defaultDecl == null) {
                for (ArrayDeclaration decl : declarations.values()) {
                    if (decl.folder.startsWith("values")) {
                        defaultDecl = decl;
                        break;
                    }
                }
                if (defaultDecl == null) {
                    defaultDecl = declarations.values().iterator().next();
                }
            }

            int expectedSize = defaultDecl.size;

            for (ArrayDeclaration decl : declarations.values()) {
                if (decl == defaultDecl) {
                    continue;
                }

                if (decl.size != expectedSize) {
                    String message = String.format(
                            "Array `%1$s` has an inconsistent number of items (%2$d in `%3$s`, but %4$d in `%5$s`)",
                            arrayName,
                            decl.size,
                            decl.folder,
                            expectedSize,
                            defaultDecl.folder
                    );

                    Location location = decl.location;
                    Location defaultLoc = defaultDecl.location;
                    if (defaultLoc != null) {
                        defaultLoc.setMessage("Default size (" + expectedSize + ") defined here");
                        location.setSecondary(defaultLoc);
                    }
                    context.report(ISSUE, location, message);
                }
            }
        }
    }

    private static class ArrayDeclaration {
        final int size;
        final Location location;
        final String folder;

        ArrayDeclaration(int size, Location location, String folder) {
            this.size = size;
            this.location = location;
            this.folder = folder;
        }
    }
}