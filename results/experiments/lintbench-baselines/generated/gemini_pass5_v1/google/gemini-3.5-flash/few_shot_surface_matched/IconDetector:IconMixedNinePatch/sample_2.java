package com.android.tools.lint.checks;

import com.android.tools.lint.client.api.*;
import com.android.tools.lint.detector.api.*;
import com.intellij.psi.*;
import org.jetbrains.uast.*;

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
                    new Implementation(
                            IconDetector.class,
                            Scope.RESOURCE_FILE_SCOPE));

    public IconDetector() {}

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        super.beforeCheckRootProject(context);
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        super.afterCheckEachProject(context);
        if (!context.getProject().getReportIssues()) {
            return;
        }
        java.util.List<java.io.File> resourceFolders = context.getProject().getResourceFolders();
        for (java.io.File res : resourceFolders) {
            java.io.File[] folders = res.listFiles();
            if (folders == null) {
                continue;
            }
            for (java.io.File folder : folders) {
                String folderName = folder.getName();
                if (folderName.startsWith("drawable")) {
                    java.io.File[] files = folder.listFiles();
                    if (files == null) {
                        continue;
                    }
                    java.util.Map<String, java.io.File> pngFiles = new java.util.HashMap<>();
                    java.util.Map<String, java.io.File> ninePatchFiles = new java.util.HashMap<>();
                    for (java.io.File file : files) {
                        String name = file.getName();
                        if (name.endsWith(".9.png")) {
                            String base = name.substring(0, name.length() - 6);
                            ninePatchFiles.put(base, file);
                        } else if (name.endsWith(".png")) {
                            String base = name.substring(0, name.length() - 4);
                            pngFiles.put(base, file);
                        }
                    }
                    for (String base : ninePatchFiles.keySet()) {
                        if (pngFiles.containsKey(base)) {
                            java.io.File pngFile = pngFiles.get(base);
                            java.io.File ninePatchFile = ninePatchFiles.get(base);
                            String message = String.format(
                                    "Clashing PNG and 9-PNG files: `%s` and `%s` both map to `@drawable/%s`",
                                    pngFile.getName(), ninePatchFile.getName(), base);
                            context.report(
                                    ISSUE,
                                    Location.create(ninePatchFile),
                                    message);
                        }
                    }
                }
            }
        }
    }

    @Override
    public void filterIncident(@NonNull Incident incident) {
        super.filterIncident(incident);
    }

    @Override
    public boolean appliesTo(@NonNull Context context, @NonNull java.io.File file) {
        return true;
    }

    @Nullable
    @Override
    public java.util.Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull org.w3c.dom.Element element) {
    }

    @Nullable
    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return null;
    }

    public void visitMethod(@NonNull JavaContext context, @NonNull UMethod method) {
    }

    public void visitCallExpression(@NonNull JavaContext context, @NonNull UCallExpression node) {
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
    }

    public void visitSimpleNameReferenceExpression(@NonNull JavaContext context, @NonNull USimpleNameReferenceExpression node) {
    }

    @Nullable
    @Override
    public java.util.List<Class<? extends UElement>> getApplicableUastTypes() {
        return null;
    }
}