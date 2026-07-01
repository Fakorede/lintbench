package com.android.tools.lint.checks;

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
            6,
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

        String folderName = context.file.getParentFile().getName();
        Location location = context.getLocation(element);
        ArrayDeclaration declaration = new ArrayDeclaration(folderName, count, location, context.file);

        List<ArrayDeclaration> list = mArrays.get(name);
        if (list == null) {
            list = new ArrayList<>();
            mArrays.put(name, list);
        }
        list.add(declaration);
    }

    @Override
    public void beforeCheckProject(Context context) {
        mArrays.clear();
    }

    @Override
    public void afterCheckProject(Context context) {
        for (Map.Entry<String, List<ArrayDeclaration>> entry : mArrays.entrySet()) {
            String arrayName = entry.getKey();
            List<ArrayDeclaration> declarations = entry.getValue();
            if (declarations.size() <= 1) {
                continue;
            }

            // Find default declaration (in "values" folder)
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
                                "Array `%1$s` has an inconsistent number of items (%2$d) compared to the default declaration in values (%3$d)",
                                arrayName, decl.count, defaultDecl.count);
                        Location location = decl.location;
                        Location secondary = defaultDecl.location;
                        if (secondary != null) {
                            secondary.setMessage("Default declaration here");
                            location.setSecondary(secondary);
                        }
                        context.report(ISSUE, location, message);
                    }
                }
            } else {
                // No default. Find the majority count.
                Map<Integer, Integer> countToFrequency = new HashMap<>();
                for (ArrayDeclaration decl : declarations) {
                    countToFrequency.put(decl.count, countToFrequency.getOrDefault(decl.count, 0) + 1);
                }
                int majorityCount = -1;
                int maxFrequency = -1;
                for (Map.Entry<Integer, Integer> freqEntry : countToFrequency.entrySet()) {
                    if (freqEntry.getValue() > maxFrequency) {
                        maxFrequency = freqEntry.getValue();
                        majorityCount = freqEntry.getKey();
                    }
                }

                for (ArrayDeclaration decl : declarations) {
                    if (decl.count != majorityCount) {
                        String message = String.format(
                                "Array `%1$s` has an inconsistent number of items (%2$d) compared to other configurations (%3$d)",
                                arrayName, decl.count, majorityCount);
                        context.report(ISSUE, decl.location, message);
                    }
                }
            }
        }
        mArrays.clear();
    }

    private static class ArrayDeclaration {
        final String folder;
        final int count;
        final Location location;
        final File file;

        ArrayDeclaration(String folder, int count, Location location, File file) {
            this.folder = folder;
            this.count = count;
            this.location = location;
            this.file = file;
        }
    }
}