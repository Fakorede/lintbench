package com.android.tools.lint.checks;

import com.android.resources.ResourceType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import org.jetbrains.uast.UElement;
import java.io.File;
import java.util.*;

public class IconDetector extends com.android.tools.lint.detector.api.Detector implements SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
            "IconDensityCoverage",
            "Icon density coverage",
            "Icons should have complete coverage across major screen densities (mdpi, hdpi, xhdpi, xxhdpi, xxxhdpi).",
            Category.CORRECTNESS,
            1,
            Severity.WARNING,
            new Implementation(IconDetector.class, null)
    );

    private static final List<String> REQUIRED_DENSITIES = Arrays.asList("mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi");
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

        Set<String> foundDensities = findFoundDensities(context, typeName, name);

        List<String> missing = new ArrayList<>();
        for (String density : densitiesToCheck) {
            if (!foundDensities.contains(density)) {
                missing.add(density);
            }
        }

        if (!missing.isEmpty()) {
            context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    String.format("Icon '%s' is missing density coverage for: %s", name, String.join(", ", missing))
            );
        }
    }

    private Set<String> findFoundDensities(JavaContext context, String typeName, String name) {
        Set<String> found = new HashSet<>();
        File root = context.getProject().getRootDir();
        // Check common resource directory locations in an Android project structure
        File[] potentialResDirs = {
                new File(root, "src/main/res"),
                new File(root, "res")
        };

        for (File resDir : potentialResDirs) {
            if (!resDir.exists() || !resDir.isDirectory()) {
                continue;
            }

            // 1. Check the base directory for mdpi coverage (e.g., res/drawable/icon.png)
            if (checkFileExists(new File(resDir, typeName + "/" + name))) {
                found.add("mdpi");
            }

            // 2. Check all density-qualified directories (e.g., res/drawable-hdpi/icon.png)
            File[] subDirs = resDir.listFiles();
            if (subDirs != null) {
                for (File dir : subDirs) {
                    String dirName = dir.getName();
                    // We are looking for folders like 'drawable-hdpi' or 'mipmap-xhdpi'
                    if (dirName.startsWith(typeName + "-")) {
                        String density = dirName.substring(typeName.length() + 1);
                        // Skip version-qualified directories like 'drawable-v24'
                        if (!density.isEmpty() && !density.startsWith("v")) {
                            if (checkFileExists(new File(dir, name))) {
                                found.add(density);
                            }
                        }
                    }
                }
            }
        }
        return found;
    }

    private boolean checkFileExists(File baseFile) {
        // An icon can be a PNG, WebP, or an XML vector drawable
        String[] extensions = {".png", ".webp", ".xml"};
        for (String ext : extensions) {
            File f = new File(baseFile.toString() + ext);
            if (f.exists() && !f.isDirectory()) {
                return true;
            }
        }
        return false;
    }
}