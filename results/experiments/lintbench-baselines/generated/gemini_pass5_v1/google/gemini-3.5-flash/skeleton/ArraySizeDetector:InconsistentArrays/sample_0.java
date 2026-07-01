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
                            + "the same number of elements as the original array. When adding or "
                            + "removing elements to an array, it is easy to forget to update all "
                            + "the locales, and this lint warning finds inconsistencies like these.\n"
                            + "\n"
                            + "Note however that there may be cases where you really want to declare "
                            + "a different number of array items in each configuration (for example "
                            + "where the array represents available options, and those options differ "
                            + "for different layout orientations and so on), so use your own judgment "
                            + "to decide if this is really an error.\n"
                            + "\n"
                            + "You can suppress this error type if it finds false errors in your project.",
                    Category.CORRECTNESS,
                    7,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private Map<String, List<ArrayInfo>> arrays;

    private static class ArrayInfo {
        final String name;
        final int count;
        final Location location;
        final String folderName;

        ArrayInfo(String name, int count, Location location, String folderName) {
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
        arrays = new HashMap<>();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (arrays == null) {
            arrays = new HashMap<>();
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

        Location location = context.getLocation(element);
        String folderName = "values";
        if (context.file.getParentFile() != null) {
            folderName = context.file.getParentFile().getName();
        }

        ArrayInfo info = new ArrayInfo(name, count, location, folderName);
        arrays.computeIfAbsent(name, k -> new ArrayList<>()).add(info);
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (arrays == null) {
            return;
        }

        for (Map.Entry<String, List<ArrayInfo>> entry : arrays.entrySet()) {
            String arrayName = entry.getKey();
            List<ArrayInfo> declarations = entry.getValue();
            if (declarations.size() <= 1) {
                continue;
            }

            int firstCount = declarations.get(0).count;
            boolean mismatch = false;
            for (int i = 1; i < declarations.size(); i++) {
                if (declarations.get(i).count != firstCount) {
                    mismatch = true;
                    break;
                }
            }

            if (mismatch) {
                ArrayInfo defaultDecl = null;
                for (ArrayInfo decl : declarations) {
                    if ("values".equals(decl.folderName)) {
                        defaultDecl = decl;
                        break;
                    }
                }

                if (defaultDecl != null) {
                    for (ArrayInfo decl : declarations) {
                        if (decl.count != defaultDecl.count) {
                            String message = String.format(
                                    "Array `%1$s` has an inconsistent number of items (%2$d in %3$s, but %4$d in %5$s)",
                                    arrayName, decl.count, decl.folderName, defaultDecl.count, defaultDecl.folderName);
                            Location location = decl.location;
                            location.setSecondary(defaultDecl.location);
                            context.report(ISSUE, location, message);
                        }
                    }
                } else {
                    ArrayInfo reference = declarations.get(0);
                    for (int i = 1; i < declarations.size(); i++) {
                        ArrayInfo decl = declarations.get(i);
                        if (decl.count != reference.count) {
                            String message = String.format(
                                    "Array `%1$s` has an inconsistent number of items (%2$d in %3$s, but %4$d in %5$s)",
                                    arrayName, decl.count, decl.folderName, reference.count, reference.folderName);
                            Location location = decl.location;
                            location.setSecondary(reference.location);
                            context.report(ISSUE, location, message);
                        }
                    }
                }
            }
        }
    }
}