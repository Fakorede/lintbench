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
import java.util.Collection;
import org.w3c.dom.Element;

public class ArraySizeDetector extends ResourceXmlDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(ArraySizeDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "InconsistentArrays",
                    "Inconsistencies in array element counts",
                    "When an array is translated in a different locale, it should normally have "
                            + "the same number of elements as the original array. When adding or removing "
                            + "elements to an array, it is easy to forget to update all the locales, and this "
                            + "lint warning finds inconsistencies like these.\n\n"
                            + "Note however that there may be cases where you really want to declare a "
                            + "different number of array items in each configuration (for example where "
                            + "the array represents available options, and those options differ for "
                            + "different layout orientations and so on), so use your own judgment to "
                            + "decide if this is really an error.\n\n"
                            + "You can suppress this error type if it finds false errors in your project.",
                    Category.CORRECTNESS,
                    7,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private final java.util.Map<String, java.util.List<ArrayDeclaration>> mArrays = new java.util.HashMap<>();

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

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.VALUES;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return java.util.Arrays.asList("string-array", "integer-array", "array");
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mArrays.clear();
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        for (java.util.Map.Entry<String, java.util.List<ArrayDeclaration>> entry : mArrays.entrySet()) {
            String name = entry.getKey();
            java.util.List<ArrayDeclaration> declarations = entry.getValue();
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
                java.util.Map<Integer, Integer> counts = new java.util.HashMap<>();
                for (ArrayDeclaration decl : declarations) {
                    counts.put(decl.count, counts.getOrDefault(decl.count, 0) + 1);
                }
                int maxCount = -1;
                int majorityValue = -1;
                for (java.util.Map.Entry<Integer, Integer> countEntry : counts.entrySet()) {
                    if (countEntry.getValue() > maxCount) {
                        maxCount = countEntry.getValue();
                        majorityValue = countEntry.getKey();
                    }
                }
                for (ArrayDeclaration decl : declarations) {
                    if (decl.count == majorityValue) {
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
                            "Array `%1$s` has an inconsistent number of items (%2$d in %3$s but %4$d in %5$s)",
                            name, decl.count, decl.folderName, baseline.count, baseline.folderName);
                    
                    Location location = decl.location;
                    Location secondary = baseline.location;
                    if (secondary != null) {
                        secondary.setMessage("Declaration with " + baseline.count + " items");
                        location.setSecondary(secondary);
                    }
                    context.report(ISSUE, location, message);
                }
            }
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttribute("name");
        if (name == null || name.isEmpty()) {
            return;
        }

        int count = 0;
        org.w3c.dom.NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            org.w3c.dom.Node child = children.item(i);
            if (child.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE && "item".equals(child.getNodeName())) {
                count++;
            }
        }

        Location location = context.getLocation(element);
        String folderName = context.file.getParentFile().getName();

        ArrayDeclaration declaration = new ArrayDeclaration(name, count, location, folderName);
        java.util.List<ArrayDeclaration> list = mArrays.get(name);
        if (list == null) {
            list = new java.util.ArrayList<>();
            mArrays.put(name, list);
        }
        list.add(declaration);
    }
}