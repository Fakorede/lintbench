package com.android.tools.lint.checks;

import com.android.SdkConstants;
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
            "When an array is translated in a different locale, it should normally have "
                + "the same number of elements as the original array. When adding or removing "
                + "elements to an array, it is easy to forget to update all the locales, and this "
                + "lint warning finds inconsistencies like these.\n"
                + "\n"
                + "Note however that there may be cases where you really want to declare a "
                + "different number of array items in each configuration (for example where "
                + "the array represents available options, and those options differ for "
                + "different layout orientations and so on), so use your own judgment to "
                + "decide if this is really an error.\n"
                + "\n"
                + "You can suppress this error type if it finds false errors in your project.",
            Category.MESSAGES,
            7,
            Severity.WARNING,
            new Implementation(
                    ArraySizeDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    private final Map<String, List<ArrayDeclaration>> mArrays = new HashMap<>();

    private static class ArrayDeclaration {
        final String name;
        final String folder;
        final int count;
        final Location location;

        ArrayDeclaration(String name, String folder, int count, Location location) {
            this.name = name;
            this.folder = folder;
            this.count = count;
            this.location = location;
        }
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

        String name = element.getAttribute(SdkConstants.ATTR_NAME);
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
        String folder = parentFile != null ? parentFile.getName() : "values";
        Location location = context.getLocation(element);

        synchronized (mArrays) {
            List<ArrayDeclaration> list = mArrays.get(name);
            if (list == null) {
                list = new ArrayList<>();
                mArrays.put(name, list);
            }
            list.add(new ArrayDeclaration(name, folder, count, location));
        }
    }

    @Override
    public void afterCheckProject(Context context) {
        synchronized (mArrays) {
            for (Map.Entry<String, List<ArrayDeclaration>> entry : mArrays.entrySet()) {
                List<ArrayDeclaration> declarations = entry.getValue();
                if (declarations.size() <= 1) {
                    continue;
                }

                int firstCount = declarations.get(0).count;
                boolean inconsistent = false;
                for (int i = 1; i < declarations.size(); i++) {
                    if (declarations.get(i).count != firstCount) {
                        inconsistent = true;
                        break;
                    }
                }

                if (inconsistent) {
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
                        if (decl.count != baseline.count) {
                            String message = String.format(
                                    "Array `%1$s` has an inconsistent number of items (%2$d in `%3$s` but %4$d in `%5$s`)",
                                    decl.name, decl.count, decl.folder, baseline.count, baseline.folder
                            );
                            Location location = decl.location;
                            Location baselineLocation = baseline.location;
                            if (baselineLocation != null && location != null) {
                                location.setSecondary(baselineLocation);
                                baselineLocation.setMessage("Baseline is here");
                            }
                            context.report(ISSUE, location, message);
                        }
                    }
                }
            }
            mArrays.clear();
        }
    }
}