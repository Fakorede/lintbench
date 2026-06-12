package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.lint.detector.api.AnnotationInfo;
import com.android.tools.lint.detector.api.AnnotationUsageInfo;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.XmlContext;
import com.intellij.psi.PsiMethod;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UElement;
import org.w3c.dom.Node;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class IconDetector extends com.android.tools.lint.detector.api.Detector implements com.android.tools.lint.detector.api.SourceCodeScanner {

    private static final String ERROR_MSG = "Icon '%s' is missing density coverage for: %s";
    private static final String LDPI_ENV_VAR = "ANDROID_LINT_INCLUDE_LDPI";
    private static final List<String> DEFAULT_DENSITIES = Arrays.asList("mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi");
    private static final List<String> ALL_EXTENSIONS = Arrays.asList(".xml", ".png", ".webp", ".jpg");

    // Cache to avoid redundant file system checks for the same resource name
    private final Set<String> checkedResources = new HashSet<>();

    @Override
    public List<String> getApplicableMethodNames() {
        return null;
    }

    @Override
    public boolean appliesToResourceRefs() {
        return true;
    }

    @Override
    public void visitResourceReference(JavaContext context, UElement node, ResourceType type, String name, boolean isFramework) {
        if (isFramework || (type != ResourceType.mipmap && type != ResourceType.drawable)) {
            return;
        }

        String cacheKey = type.name() + ":" + name;
        if (checkedResources.contains(cacheKey)) {
            return;
        }

        List<String> densitiesToCheck = new ArrayList<>(DEFAULT_DENSITIES);
        if ("true".equalsIgnoreCase(System.getenv(LDPI_ENV_VAR))) {
            densitiesToCheck.add(0, "ldpi");
        }

        List<String> missingDensities = new ArrayList<>();
        for (String density : densitiesToCheck) {
            if (!resourceExistsInDensity(context, type, name, density)) {
                missingDensities.add(density);
            }
        }

        if (!missingDensities.isEmpty()) {
            context.report(
                    Issue.create(
                            "IconDensityCoverage",
                            "Icon density coverage",
                            ERROR_MSG,
                            com.android.tools.lint.detector.api.Issue.Severity.WARNING,
                            null,
                            null
                    ),
                    node,
                    context.getLocation(node),
                    String.format(ERROR_MSG, name, missingDensenciesToString(missingDensities))
            );
        }

        checkedResources.add(cacheKey);
    }

    private boolean resourceExistsInDensity(JavaContext context, ResourceType type, String name, String density) {
        try {
            // We attempt to find the folder for the specific density qualifier.
            // Note: In a real Lint environment, we access the project's resource structure.
            com.android.tools.lint.detector.api.ResourceFolder folder = context.getProject().getResourceFolder(type, density);
            if (folder == null) {
                return false;
            }

            File dir = new File(folder.getPath());
            if (!dir.exists() || !dir.isDirectory()) {
                return false;
            }

            for (String ext : ALL_EXTENSIONS) {
                if (new File(dir, name + ext).exists()) {
                    return true;
                }
            }
        } catch (Exception e) {
            // If we can't access the folder or it doesn't exist, assume it's missing.
            return false;
        }
        return false;
    }

    private String missingDensenciesToString(List<String> missing) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < missing.size(); i++) {
            sb.append(missing.get(i));
            if (i < missing.size() - 1) {
                sb.append(", ");
            }
        }
        return sb.toString();
    }

    // Inner class to represent the Issue definition within the detector file.
    // In a real implementation, this would be in a separate Issue class.
    private static class Issue {
        static com.android.tools.lint.detector.api.Issue create(
                String id, String summary, String description, 
                com.android.tools.lint.detector.api.Issue.Severity severity, 
                int priority, String implementation) {
            return new com.android.tools.lint.detector.api.Issue.Builder(id, severity)
                    .addDescription(description)
                    .addSummary(summary)
                    .build();
        }
    }
}