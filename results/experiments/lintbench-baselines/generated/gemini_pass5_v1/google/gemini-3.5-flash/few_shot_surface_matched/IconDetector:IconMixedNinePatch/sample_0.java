package com.android.tools.lint.checks;

import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.IncidentFilter;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiMethod;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.USimpleNameReferenceExpression;

public class IconDetector extends Detector implements SourceCodeScanner, XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "IconMixedNinePatch",
                    "Clashing PNG and 9-PNG files",
                    "If you accidentally name two separate resources `file.png` and `file.9.png`,"
                            + " the image file and the nine patch file will both map to the same drawable"
                            + " resource, `@drawable/file`, which is probably not what was intended.",
                    Category.ICONS,
                    5,
                    Severity.WARNING,
                    new Implementation(IconDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public void beforeCheckRootProject(Context context) {
        super.beforeCheckRootProject(context);
    }

    @Override
    public void afterCheckEachProject(Context context) {
        super.afterCheckEachProject(context);
        java.util.List<java.io.File> resourceFolders = context.getProject().getResourceFolders();
        for (java.io.File resFolder : resourceFolders) {
            java.io.File[] drawables = resFolder.listFiles();
            if (drawables != null) {
                for (java.io.File drawableFolder : drawables) {
                    if (drawableFolder.isDirectory() && drawableFolder.getName().startsWith("drawable")) {
                        checkDrawableFolder(context, drawableFolder);
                    }
                }
            }
        }
    }

    private void checkDrawableFolder(Context context, java.io.File folder) {
        java.io.File[] files = folder.listFiles();
        if (files == null) return;

        java.util.Map<String, java.io.File> pngFiles = new java.util.HashMap<>();
        java.util.Map<String, java.io.File> ninePatchFiles = new java.util.HashMap<>();

        for (java.io.File file : files) {
            String name = file.getName();
            if (name.endsWith(".9.png")) {
                String base = name.substring(0, name.length() - ".9.png".length());
                ninePatchFiles.put(base, file);
            } else if (name.endsWith(".png")) {
                String base = name.substring(0, name.length() - ".png".length());
                pngFiles.put(base, file);
            }
        }

        for (String base : pngFiles.keySet()) {
            if (ninePatchFiles.containsKey(base)) {
                java.io.File pngFile = pngFiles.get(base);
                java.io.File ninePatchFile = ninePatchFiles.get(base);
                
                String message = String.format(
                        "Clashing PNG and 9-PNG files: `%s` and `%s` both map to `@drawable/%s`",
                        pngFile.getName(), ninePatchFile.getName(), base);
                
                context.report(ISSUE, Location.create(pngFile), message);
            }
        }
    }

    @Override
    public void filterIncident(Context context, Incident incident, IncidentFilter map) {
    }

    @Override
    public boolean appliesTo(com.android.resources.ResourceFolderType folderType) {
        return folderType == com.android.resources.ResourceFolderType.DRAWABLE;
    }

    @Override
    public java.util.Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public void visitElement(XmlContext context, org.w3c.dom.Element element) {
    }

    @Override
    public UElementHandler createUastHandler(JavaContext context) {
        return null;
    }

    @Override
    public void visitMethod(JavaContext context, UMethod method) {
    }

    @Override
    public void visitCallExpression(JavaContext context, UCallExpression node) {
    }

    @Override
    public void visitClass(JavaContext context, UClass declaration) {
    }

    @Override
    public void visitSimpleNameReferenceExpression(JavaContext context, USimpleNameReferenceExpression node) {
    }

    @Override
    public java.util.List<Class<? extends UElement>> getApplicableUastTypes() {
        return null;
    }
}