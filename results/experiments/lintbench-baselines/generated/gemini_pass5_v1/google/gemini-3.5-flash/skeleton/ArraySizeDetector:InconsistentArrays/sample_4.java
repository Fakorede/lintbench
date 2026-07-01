package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
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
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

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

    private final Map<String, List<ArrayDeclaration>> mArrays = new HashMap<>();

    private static class ArrayDeclaration {
        final String name;
        final int count;
        @NonNull final Location location;
        @NonNull final String folderName;

        ArrayDeclaration(String name, int count, @NonNull Location location, @NonNull String folderName) {
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
        return Arrays.asList("string-array", "integer-array", "array");
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mArrays.clear();
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        for (Map.Entry<String, List<ArrayDeclaration>> entry : mArrays.entrySet()) {
            String name = entry.getKey();
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
                int bestSize = -1;
                for (Map.Entry<Integer, Integer> countEntry : counts.entrySet()) {
                    if (countEntry.getValue() > maxCount) {
                        maxCount = countEntry.getValue();
                        bestSize = countEntry.getKey();
                    }
                }
                for (ArrayDeclaration decl : declarations) {
                    if (decl.count == bestSize) {
                        baseline = decl;
                        break;
                    }
                }
            }

            if (baseline != null) {
                int baselineCount = baseline.count;
                for (ArrayDeclaration decl : declarations) {
                    if (decl.count != baselineCount) {
                        String message = String.format(
                                "Array `%s` has an inconsistent number of items (%d in `%s`, but %d in `%s`)",
                                name, decl.count, decl.folderName, baselineCount, baseline.folderName);
                        Location location = decl.location;
                        if (decl != baseline) {
                            Location secondary = baseline.location;
                            location.setSecondary(secondary);
                            secondary.setMessage("Declaration with size " + baselineCount);
                        }
                        context.report(ISSUE, location, message);
                    }
                }
            }
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttribute("name");
        if (name == null || name.trim().isEmpty()) {
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

        Location location = context.getLocation(element);
        String folderName = "values";
        File parentFile = context.file.getParentFile();
        if (parentFile != null) {
            folderName = parentFile.getName();
        }

        List<ArrayDeclaration> list = mArrays.computeIfAbsent(name, k -> new ArrayList<>());
        list.add(new ArrayDeclaration(name, count, location, folderName));
    }
}