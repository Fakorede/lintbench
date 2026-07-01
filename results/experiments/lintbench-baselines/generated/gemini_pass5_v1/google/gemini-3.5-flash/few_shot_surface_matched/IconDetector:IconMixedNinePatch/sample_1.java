package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.*;
import com.intellij.psi.*;
import org.jetbrains.uast.*;

public class IconDetector extends Detector implements SourceCodeScanner, XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "IconMixedNinePatch",
            "Clashing PNG and 9-PNG files",
            "If you accidentally name two separate resources `file.png` and `file.9.png`, "
                    + "the image file and the nine patch file will both map to the same drawable resource, "
                    + "`@drawable/file`, which is probably not what was intended.",
            Category.ICONS,
            5,
            Severity.WARNING,
            new Implementation(IconDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    // 1. beforeCheckRootProject
    @Override
    public void beforeCheckRootProject(Context context) {
        super.beforeCheckRootProject(context);
    }

    // 2. afterCheckEachProject
    public void afterCheckEachProject(Context context) {
        checkIcons(context);
    }

    @Override
    public void afterCheckProject(Context context) {
        checkIcons(context);
    }

    // 3. filterIncident
    @Override
    public boolean filterIncident(Incident incident) {
        return true;
    }

    // 4. appliesTo
    @Override
    public boolean appliesTo(com.android.resources.ResourceFolderType folderType) {
        return folderType == com.android.resources.ResourceFolderType.DRAWABLE;
    }

    @Override
    public boolean appliesTo(Context context, java.io.File file) {
        return true;
    }

    // 5. getApplicableElements
    @Override
    public java.util.Collection<String> getApplicableElements() {
        return null;
    }

    // 6. visitElement
    @Override
    public void visitElement(XmlContext context, org.w3c.dom.Element element) {
    }

    // 7. createUastHandler
    @Override
    public com.android.tools.lint.client.api.UElementHandler createUastHandler(JavaContext context) {
        return null;
    }

    // 8. visitMethod
    @Override
    public void visitMethod(JavaContext context, UMethod method) {
    }

    // 9. visitCallExpression
    @Override
    public void visitCallExpression(JavaContext context, UCallExpression call) {
    }

    // 10. visitClass
    @Override
    public void visitClass(JavaContext context, UClass declaration) {
    }

    // 11. visitSimpleNameReferenceExpression
    @Override
    public void visitSimpleNameReferenceExpression(JavaContext context, USimpleNameReferenceExpression expression) {
    }

    // 12. getApplicableUastTypes
    @Override
    public java.util.List<java.lang.Class<? extends UElement>> getApplicableUastTypes() {
        return null;
    }

    private void checkIcons(Context context) {
        for (java.io.File resDir : context.getProject().getResourceFolders()) {
            java.io.File[] densityDirs = resDir.listFiles();
            if (densityDirs == null) {
                continue;
            }
            for (java.io.File densityDir : densityDirs) {
                if (!densityDir.getName().startsWith("drawable")) {
                    continue;
                }
                java.io.File[] files = densityDir.listFiles();
                if (files == null) {
                    continue;
                }
                java.util.Set<String> names = new java.util.HashSet<>();
                for (java.io.File file : files) {
                    String name = file.getName();
                    if (name.endsWith(".png")) {
                        names.add(name);
                    }
                }
                for (java.io.File file : files) {
                    String name = file.getName();
                    if (name.endsWith(".9.png")) {
                        String baseName = name.substring(0, name.length() - ".9.png".length());
                        String pngName = baseName + ".png";
                        if (names.contains(pngName)) {
                            Location location = Location.create(file);
                            context.report(
                                    ISSUE,
                                    location,
                                    "Clashing PNG and 9-PNG files: `" + pngName + "` and `" + name + "` both map to `@drawable/" + baseName + "`"
                            );
                        }
                    }
                }
            }
        }
    }
}