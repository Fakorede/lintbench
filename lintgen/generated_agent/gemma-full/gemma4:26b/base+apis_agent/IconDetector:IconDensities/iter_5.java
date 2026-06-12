package com.android.tools.lint.checks;

import com.android.resources.ResourceType;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import org.jetbrains.uast.UElement;
import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class IconDetector extends com.android.tools.lint.detector.api.Detector implements SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
            "IconDensityCoverage",
            "Icon density coverage",
            "Icons should have complete coverage across major screen densities (mdpi, hdpi, xhdpi, xxhdpi, xxxhdpi).",
            Issue.Severity.WARNING
    );

    private static final String LDPI_ENV_VAR = "ANDROID_LINT_INCLUDE_LDPI";
    private static final List<String> EXTENSIONS = Arrays.asList(".png", ".webp", ".xml");
    private static final List<String> BASE_DENSITIES = Arrays.asList("mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi");

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

        List<String> densitiesToCheck = new ArrayList<>(BASE_DENSITIES);
        if ("true".equalsIgnoreCase(System.getenv(LDPI_ENV_VAR))) {
            densitiesToCheck.add(0, "ldpi");
        }

        Set<String> foundDensities = new HashSet<>();
        try {
            Iterable<?> folders = context.getProject().getResourceFolders();
            for (Object folder : folders) {
                if (folder == null) continue;
                
                // Use reflection to access getDirectory() as ResourceFolder might not be in the compile classpath
                Method getDirMethod = folder.getClass().getMethod("getDirectory");
                File dir = (File) getDirMethod.invoke(folder);
                
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
            // If we cannot access resource folders via reflection, we cannot perform the check.
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
                    ISSUE,
                    node,
                    context.getLocation(node),
                    String.format("Icon '%s' is missing density coverage for: %s", name, String.join(", ", missingDensities))
            );
        }
    }

    @Override
    public List<String> getApplicableReferenceNames() {
        return null;
    }
}