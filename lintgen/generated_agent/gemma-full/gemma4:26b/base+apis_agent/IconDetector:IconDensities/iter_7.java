package com.android.tools.lint.checks;

import com.android.resources.ResourceType;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.JavaContext;
import org.jetbrains.uast.UElement;
import java.io.File;
import java.util.*;

public class IconDetector extends com.android.tools.lint.detector.api.Detector implements SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
            "IconDensityCoverage",
            "Icon density coverage",
            "Icons should have complete coverage across major screen densities (mdpi, hdpi, xhdpi, xxhdpi, xxxhdpi).",
            Severity.WARNING
    );

    private static final String LDPI_ENV_VAR = "ANDROID_LINT_INCLUDE_LDPI";
    private static final List<String> REQUIRED_DENSITIES = Arrays.asList("mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi");
    private static final List<String> EXTENSIONS = Arrays.asList(".png", ".webp", ".xml");

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
        if (!typeName.equals("drawable") && !typeName.equals("mipmap")) {
            return;
        }

        List<String> densitiesToCheck = new ArrayList<>(REQUIRED_DENSITIES);
        if ("true".equalsIgnoreCase(System.getenv(LDPI_ENV_VAR))) {
            densitiesToCheck.add(0, "ldpi");
        }

        Set<String> foundDensities = new HashSet<>();
        File resRoot = context.getProject().getResourceRoot();
        if (resRoot == null) {
            return;
        }

        for (com.android.resources.ResourceFolder folder : context.getProject().getResourceFolders()) {
            String folderName = folder.getName();
            
            // Only check folders that belong to the current resource type (drawable or mipmap)
            if (!folderName.startsWith(typeName)) {
                continue;
            }

            // Extract density from folder name (e.g., "drawable-hdpi" -> "hdpi")
            String density = "mdpi"; 
            int dashIndex = folderName.lastIndexOf('-');
            if (dashIndex != -1) {
                density = folderName.substring(dashIndex + 1);
            }

            File folderDir = new File(resRoot, folderName);
            if (folderDir.exists() && folderDir.isDirectory()) {
                for (String ext : EXTENSIONS) {
                    if (new File(folderDir, name + ext).exists()) {
                        foundDensities.add(density);
                        break;
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

    @Override
    public List<String> getApplicableReferenceNames() {
        return null;
    }
}