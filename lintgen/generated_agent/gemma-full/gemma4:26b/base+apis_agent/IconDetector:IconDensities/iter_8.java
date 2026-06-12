package com.android.tools.lint.checks;

import com.android.resources.ResourceType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiMethod;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UReferenceExpression;
import java.io.File;
import java.util.*;

public class IconDetector extends com.android.tools.lint.detector.api.Detector implements SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
            "IconDensityCoverage",
            "Icon density coverage",
            "Icons should have complete coverage across major screen densities (mdpi, hdpi, xhdpi, xxhdpi, xxxhdpi).",
            Category.LENGTH,
            1,
            Severity.WARNING,
            new Implementation(IconDetector.class, null)
    );

    private static final List<String> REQUIRED_DENSITIES = Arrays.asList("mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi");
    private static final List<String> EXTENSIONS = Arrays.asList(".png", ".webp", ".xml");
    private static final String LDPI_ENV_VAR = "ANDROID_LINT_INCLUDE_LDPI";

    @Override
    public List<String> getApplicableMethodNames() {
        return null;
    }

    @Override
    public List<String> getApplicableReferenceNames() {
        return null;
    }

    @Override
    public void visitResourceReference(JavaContext context, UElement node, ResourceType type, String name, boolean isFramework) {
        if (isFragmentOrFramework(isFramework)) {
            return;
        }

        String typeName = type.name();
        if (!typeName.equals("drawable") && !typeName.equals("mipmap")) {
            return;
        }

        List<String> densitiesToCheck = new ArrayList<>(REQUIRED_DENSITIES);
        if ("true".equalsIgnoreCase(System.getenv(LDPI_ENV_VAR))) {
            densitiesToCheck.add(0, "ldpi");
        }

        Set<String> foundDensities = new HashSet<>();
        
        // Iterate through all files in the project to find matches for this resource name in density folders
        for (File file : context.getFiles()) {
            String fileName = file.getName();
            if (fileName.startsWith(name + ".")) {
                boolean hasValidExt = false;
                for (String ext : EXTENSIONS) {
                    if (fileName.endsWith(ext)) {
                        hasValidExt = true;
                        break;
                    }
                }

                if (hasValidExt) {
                    String parentName = file.getParentFile().getName();
                    // Check if the folder is a density-specific folder for this type (e.g., drawable-hdpi)
                    if (parentName.startsWith(typeName + "-")) {
                        String density = parentName.substring(typeName.length() + 1);
                        foundDensities.add(density);
                    } else if (parentName.equals(typeName) || parentName.equals(typeName + "-mdpi")) {
                        // mdpi is often in the base folder without a suffix
                        foundDensities.add("mdpi");
                    }
                }
            }
        }

        List<String> missingDensities = new ArrayList<>();
        for (String density : densitiesToCheck) {
            if (!foundDensities.contains(density)) {
                missingDensities.add(density);
            }
        }

        if (!missingDensities.isEmpty()) {
            context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    String.format("Icon '%s' is missing density coverage for: %s", name, String.join(", ", missingDensities))
            );
        }
    }

    private boolean isFragmentOrFramework(boolean isFramework) {
        return isFramework;
    }
}