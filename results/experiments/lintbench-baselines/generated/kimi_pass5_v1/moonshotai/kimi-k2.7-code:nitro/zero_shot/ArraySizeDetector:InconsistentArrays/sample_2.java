package com.android.tools.lint.checks;

import static com.android.SdkConstants.TAG_ARRAY;
import static com.android.SdkConstants.TAG_INTEGER_ARRAY;
import static com.android.SdkConstants.TAG_STRING_ARRAY;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.ArrayList;
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
                    + "elements to an array, it is easy to forget to update all the locales, and "
                    + "this lint warning finds inconsistencies like these.\n\n"
                    + "Note however that there may be cases where you really want to declare a "
                    + "different number of array items in each configuration (for example where "
                    + "the array represents available options, and those options differ for "
                    + "different layout orientations and so on), so use your own judgment to "
                    + "decide if this is really an error.\n\n"
                    + "You can suppress this error type if it finds false errors in your project.",
            Category.MESSAGES,
            5,
            Severity.WARNING,
            new Implementation(ArraySizeDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private final Map<Project, Map<String, List<ArrayDeclaration>>> mDeclarations =
            new HashMap<>();

    @Override
    public Collection<String> getApplicableElements() {
        return java.util.Arrays.asList(TAG_ARRAY, TAG_STRING_ARRAY, TAG_INTEGER_ARRAY);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttribute("name");
        if (name == null || name.isEmpty()) {
            return;
        }

        String type = element.getTagName();
        int count = countItems(element);
        String config = getConfigurationName(context);
        Location location = context.getElementLocation(element);

        Project project = context.getProject();
        Map<String, List<ArrayDeclaration>> projectMap = mDeclarations.get(project);
        if (projectMap == null) {
            projectMap = new HashMap<>();
            mDeclarations.put(project, projectMap);
        }

        String key = type + "/" + name;
        List<ArrayDeclaration> declarations = projectMap.get(key);
        if (declarations == null) {
            declarations = new ArrayList<>();
            projectMap.put(key, declarations);
        }

        declarations.add(new ArrayDeclaration(name, count, config, location));
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        Project project = context.getProject();
        Map<String, List<ArrayDeclaration>> projectMap = mDeclarations.get(project);
        if (projectMap == null) {
            return;
        }

        for (Map.Entry<String, List<ArrayDeclaration>> entry : projectMap.entrySet()) {
            List<ArrayDeclaration> declarations = entry.getValue();
            if (declarations.size() < 2) {
                continue;
            }

            ArrayDeclaration reference = null;
            for (ArrayDeclaration declaration : declarations) {
                if (declaration.config.isEmpty()) {
                    reference = declaration;
                    break;
                }
            }

            if (reference == null) {
                Map<Integer, Integer> countFrequencies = new HashMap<>();
                int maxFrequency = 0;
                for (ArrayDeclaration declaration : declarations) {
                    int frequency = countFrequencies.getOrDefault(declaration.count, 0) + 1;
                    countFrequencies.put(declaration.count, frequency);
                    if (frequency > maxFrequency) {
                        maxFrequency = frequency;
                        reference = declaration;
                    }
                }
            }

            if (reference == null) {
                continue;
            }

            int expectedCount = reference.count;
            String expectedConfig = displayConfig(reference.config);

            for (ArrayDeclaration declaration : declarations) {
                if (declaration.count != expectedCount) {
                    String actualConfig = displayConfig(declaration.config);
                    String message = String.format(
                            "Array \"%s\" has %d entries in %s but %d entries in %s",
                            declaration.name,
                            expectedCount,
                            expectedConfig,
                            declaration.count,
                            actualConfig);
                    context.report(ISSUE, declaration.location, message);
                }
            }
        }
    }

    private static int countItems(@NonNull Element array) {
        int count = 0;
        NodeList children = array.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                count++;
            }
        }
        return count;
    }

    @NonNull
    private static String getConfigurationName(@NonNull XmlContext context) {
        String folderName = context.file.getParentFile().getName();
        if ("values".equals(folderName)) {
            return "";
        } else if (folderName.startsWith("values-")) {
            return folderName.substring("values-".length());
        }
        return folderName;
    }

    @NonNull
    private static String displayConfig(@NonNull String config) {
        return config.isEmpty() ? "default" : config;
    }

    private static class ArrayDeclaration {
        final String name;
        final int count;
        final String config;
        final Location location;

        ArrayDeclaration(
                @NonNull String name,
                int count,
                @NonNull String config,
                @NonNull Location location) {
            this.name = name;
            this.count = count;
            this.config = config;
            this.location = location;
        }
    }
}