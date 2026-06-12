package com.android.tools.lint.checks;

import com.android.resources.ResourceType;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
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
    private static final List<String> ALL_EXTENSIONS = Arrays.asList(".xml", ".png", ".webp", ".jpg");

    private static final Issue ICON_ISSUE = Issue.create(
            "IconDensityCoverage",
            "Icon density coverage",
            "Icons should have complete coverage across major screen densities (mdpi, hdpi, xhdpi, xxhdpi, xxxhdpi).",
            com.android.tools.lint.detector.api.Issue.Severity.WARNING
    );

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
        if (isFramework) {
            return;
        }

        String typeName = type.name();
        if (!typeName.equals("mipmap") && !typeName.equals("drawable")) {
            return;
        }

        List<String> densitiesToCheck = new ArrayList<>(Arrays.asList("mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"));
        if ("true".equalsIgnoreCase(System.getenv(LDPI_ENV_VAR))) {
            densitiesToCheck.add(0, "ldpi");
        }

        Set<String> foundDensities = new HashSet<>();
        try {
            com.android.resources.Project project = context.getEvaluationContext().getProject();
            for (com.android.resources.ResourceFolder folder : project.getResourceFolders(type)) {
                File dir = folder.getDirectory();
                if (dir == null) {
                    continue;
                }
                String folderPath = dir.getPath();

                for (String density : densitiesToCheck) {
                    // Check if this folder is a density-specific folder for the current density (e.g., "-hdpi")
                    if (folderPath.contains("-" + density)) {
                        for (String ext : ALL_EXTENSIONS) {
                            if (new File(dir, name + ext).exists()) {
                                foundDensities.add(density);
                                break;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            // If we cannot access the project or folders, skip reporting to avoid false positives/errors
            return;
        }

        List<String> missingDensities = new ArrayList<>();
        for (String density : densitiesToCheck) {
            if (!foundDensities.contains(density)) {
                missingDensities.add(density);
            }
        }

        if (!missingDensities.isEmpty()) {
            context.report(
                    ICON_ISSUE,
                    node,
                    context.getLocation(node),
                    String.format(ERROR_MSG, name, String.join(", ", missingDensities))
            );
        }
    }
}