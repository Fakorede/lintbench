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

    private final Map<String, List<ArrayDeclaration>> mDeclarations = new HashMap<>();

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.VALUES;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return java.util.Arrays.asList("string-array", "integer-array", "array");
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

        File file = context.file;
        String folderName = file.getParentFile().getName();
        Location location = context.getLocation(element);

        ArrayDeclaration decl = new ArrayDeclaration(name, count, file, location, folderName);
        synchronized (mDeclarations) {
            List<ArrayDeclaration> list = mDeclarations.get(name);
            if (list == null) {
                list = new ArrayList<>();
                mDeclarations.put(name, list);
            }
            list.add(decl);
        }
    }

    @Override
    public void afterCheckProject(Context context) {
        if (!context.getProject().equals(context.getMainProject())) {
            return;
        }

        synchronized (mDeclarations) {
            for (Map.Entry<String, List<ArrayDeclaration>> entry : mDeclarations.entrySet()) {
                String arrayName = entry.getKey();
                List<ArrayDeclaration> declarations = entry.getValue();
                if (declarations.size() <= 1) {
                    continue;
                }

                ArrayDeclaration baseline = null;
                for (ArrayDeclaration decl : declarations) {
                    if ("values".equals(decl.folderName)) {
                        baseline = decl;
                        break;
                    }
                }

                if (baseline == null) {
                    Map<Integer, Integer> counts = new HashMap<>();
                    for (ArrayDeclaration decl : declarations) {
                        counts.put(decl.count, counts.getOrDefault(decl.count, 0) + 1);
                    }
                    int maxCount = -1;
                    int mostCommonVal = -1;
                    for (Map.Entry<Integer, Integer> countEntry : counts.entrySet()) {
                        if (countEntry.getValue() > maxCount) {
                            maxCount = countEntry.getValue();
                            mostCommonVal = countEntry.getKey();
                        }
                    }
                    for (ArrayDeclaration decl : declarations) {
                        if (decl.count == mostCommonVal) {
                            baseline = decl;
                            break;
                        }
                    }
                }

                if (baseline == null) {
                    baseline = declarations.get(0);
                }

                for (ArrayDeclaration decl : declarations) {
                    if (decl == baseline) {
                        continue;
                    }
                    if (decl.count != baseline.count) {
                        String message = String.format(
                                "Array `%s` has an inconsistent number of items (%d in %s but %d in %s)",
                                arrayName, decl.count, decl.folderName, baseline.count, baseline.folderName
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
        final File file;
        final Location location;
        final String folderName;

        ArrayDeclaration(String name, int count, File file, Location location, String folderName) {
            this.name = name;
            this.count = count;
            this.file = file;
            this.location = location;
            this.folderName = folderName;
        }
    }
}