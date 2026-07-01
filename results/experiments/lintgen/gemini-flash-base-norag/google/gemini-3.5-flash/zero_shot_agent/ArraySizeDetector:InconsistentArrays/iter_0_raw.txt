package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
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
            5,
            Severity.WARNING,
            new Implementation(
                    ArraySizeDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    private final Map<String, List<ArrayDeclaration>> mArrays = new HashMap<>();

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                SdkConstants.TAG_STRING_ARRAY,
                SdkConstants.TAG_INTEGER_ARRAY,
                SdkConstants.TAG_ARRAY
        );
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mArrays.clear();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttribute(SdkConstants.ATTR_NAME);
        if (name == null || name.isEmpty()) {
            return;
        }

        int count = 0;
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE 
                    && SdkConstants.TAG_ITEM.equals(child.getNodeName())) {
                count++;
            }
        }

        String folderName = context.file.getParentFile().getName();
        Location location = context.getLocation(element);

        ArrayDeclaration decl = new ArrayDeclaration(name, count, folderName, location);
        List<ArrayDeclaration> list = mArrays.get(name);
        if (list == null) {
            list = new ArrayList<>();
            mArrays.put(name, list);
        }
        list.add(decl);
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        for (Map.Entry<String, List<ArrayDeclaration>> entry : mArrays.entrySet()) {
            String arrayName = entry.getKey();
            List<ArrayDeclaration> declarations = entry.getValue();
            if (declarations.size() <= 1) {
                continue;
            }

            ArrayDeclaration baseline = null;
            for (ArrayDeclaration decl : declarations) {
                if ("values".equals(decl.folder)) {
                    baseline = decl;
                    break;
                }
            }
            if (baseline == null) {
                baseline = declarations.get(0);
            }

            for (ArrayDeclaration decl : declarations) {
                if (decl == baseline) {
                    continue;
                }
                if (decl.size != baseline.size) {
                    String message = String.format(
                            "Array `%1$s` has an inconsistent number of items (%2$d in %3$s, but %4$d in %5$s)",
                            arrayName, decl.size, decl.folder, baseline.size, baseline.folder
                    );
                    Location location = decl.location;
                    location.setSecondary(baseline.location);
                    context.report(ISSUE, location, message);
                }
            }
        }
    }

    private static class ArrayDeclaration {
        final String name;
        final int size;
        final String folder;
        final Location location;

        ArrayDeclaration(String name, int size, String folder, Location location) {
            this.name = name;
            this.size = size;
            this.folder = folder;
            this.location = location;
        }
    }
}