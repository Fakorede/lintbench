package com.android.tools.lint.checks;

import com.android.resources.ResourceFolder;
import com.android.resources.ResourceType;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiElement;
import org.jetbrains.uast.UElement;
import org.w3c.dom.Node;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class IconDetector extends com.android.tools.lint.detector.api.Detector implements SourceCodeScanner {

    private static final String ERROR_MSG = "Icon '%s' is missing density coverage for: %s";
    private static final String LDPI_ENV_VAR = "ANDROID_LINT_INCLUDE_LDPI";
    private static final List<String> EXTENSIONS = Arrays.asList(".xml", ".png", ".webp");

    private static final Issue ICON_ISSUE = Issue.create(
            "IconDensityCoverage",
            "Icon density coverage",
            "Icons should have complete coverage across major screen densities (mdpi, hdpi, xhdpi, xxhdpi, xxxhdpi).",
            Issue.Severity.WARNING
    );

    @Override
    public List<com.android.tools.lint.detector.api.Implementation> getImplementation() {
        return Arrays.asList(new com.android.tools.lint.detector.api.Implementation(
                IconDetector.class,
                com.android.tools.lint.detector.api.Scope.JAVA_SOURCE_FILES));
    }

    @Override
    public List<String> getApplicableMethodNames() {
        return null;
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
            Iterable<ResourceFolder> folders = context.getProject().getResourceFolders();
            for (ResourceFolder folder : folders) {
                File dir = folder.getDirectory();
                if (dir == null) {
                    continue;
                }

                String folderName = dir.getName();
                for (String density : densitiesToCheck) {
                    if (folderName.endsWith("-" + density)) {
                        boolean exists = false;
                        for (String ext : EXTENSIONS) {
                            if (new File(dir, name + ext).exists()) {
                                exists = true;
                                break;
                            }
                        }
                        if (exists) {
                            foundDensities.add(density);
                        }
                    }
                }
            }
        } catch (Exception e) {
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

    @Override
    public List<String> getApplicableReferenceNames() {
        return null;
    }

    @Override
    public void visitReference(org.jetbrains.uast.UReferenceExpression reference, PsiElement referenced) {
    }
}