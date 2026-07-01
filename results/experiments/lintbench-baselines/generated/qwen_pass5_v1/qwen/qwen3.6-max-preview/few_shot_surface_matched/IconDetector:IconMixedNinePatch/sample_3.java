package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UElementHandler;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.USimpleNameReferenceExpression;

public class IconDetector extends Detector implements SourceCodeScanner, XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "IconMixedNinePatch",
                    "Clashing PNG and 9-PNG files",
                    "If you accidentally name two separate resources `file.png` and `file.9.png`, "
                            + "the image file and the nine patch file will both map to the same drawable "
                            + "resource, `@drawable/file`, which is probably not what was intended.",
                    Category.ICONS,
                    5,
                    Severity.WARNING,
                    new Implementation(IconDetector.class, Scope.ALL_SCOPE));

    @Override
    public void beforeCheckRootProject(Context context) {
    }

    @Override
    public void afterCheckEachProject(Context context) {
        java.io.File projectDir = context.getProject().getDir();
        java.io.File resDir = new java.io.File(projectDir, "res");
        if (!resDir.isDirectory()) {
            return;
        }

        java.util.Map<String, java.io.File> pngFiles = new java.util.HashMap<>();
        java.util.Map<String, java.io.File> ninePatchFiles = new java.util.HashMap<>();

        java.io.File[] drawableDirs = resDir.listFiles(new java.io.FileFilter() {
            @Override
            public boolean accept(java.io.File file) {
                return file.isDirectory() && file.getName().startsWith("drawable");
            }
        });

        if (drawableDirs != null) {
            for (java.io.File dir : drawableDirs) {
                java.io.File[] files = dir.listFiles();
                if (files == null) continue;
                for (java.io.File f : files) {
                    String name = f.getName();
                    if (name.endsWith(".9.png")) {
                        ninePatchFiles.put(name.substring(0, name.length() - 6), f);
                    } else if (name.endsWith(".png")) {
                        pngFiles.put(name.substring(0, name.length() - 4), f);
                    }
                }
            }
        }

        for (java.util.Map.Entry<String, java.io.File> entry : pngFiles.entrySet()) {
            String base = entry.getKey();
            if (ninePatchFiles.containsKey(base)) {
                java.io.File png = entry.getValue();
                java.io.File ninePatch = ninePatchFiles.get(base);
                context.report(ISSUE, context.getLocation(png),
                        "Clashing PNG and 9-PNG files: " + png.getName() + " and " + ninePatch.getName());
            }
        }
    }

    @Override
    public boolean filterIncident(Incident incident) {
        return true;
    }

    @Override
    public boolean appliesTo(Context context, java.io.File file) {
        return true;
    }

    @Override
    public java.util.Collection<String> getApplicableElements() {
        return java.util.Collections.emptyList();
    }

    @Override
    public void visitElement(XmlContext context, org.w3c.dom.Element element) {
    }

    @Override
    public UElementHandler createUastHandler(JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitMethod(UMethod node) {
            }

            @Override
            public void visitCallExpression(UCallExpression node) {
            }

            @Override
            public void visitClass(UClass node) {
            }

            @Override
            public void visitSimpleNameReferenceExpression(USimpleNameReferenceExpression node) {
            }
        };
    }

    @Override
    public java.util.List<Class<? extends UElement>> getApplicableUastTypes() {
        return java.util.Collections.emptyList();
    }
}