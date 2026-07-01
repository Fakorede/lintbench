package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
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
import org.jetbrains.uast.UElementHandler;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Element;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class IconDetector extends Detector implements XmlScanner, SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
            "IconMixedNinePatch",
            "Clashing PNG and 9-PNG files",
            "If you accidentally name two separate resources `file.png` and `file.9.png`, " +
            "the image file and the nine patch file will both map to the same drawable resource, " +
            "`@drawable/file`, which is probably not what was intended.",
            Category.ICONS,
            5,
            Severity.WARNING,
            new Implementation(IconDetector.class, Scope.RESOURCE_FILE_SCOPE, Scope.JAVA_FILE_SCOPE));

    @Override
    public void beforeCheckRootProject(Context context) {
        super.beforeCheckRootProject(context);
    }

    @Override
    public void afterCheckEachProject(Context context) {
        super.afterCheckEachProject(context);
        for (File resDir : context.getProject().getResourceFolders()) {
            if (!resDir.isDirectory()) continue;
            File[] drawableDirs = resDir.listFiles((dir, name) -> name.startsWith("drawable"));
            if (drawableDirs == null) continue;
            for (File drawableDir : drawableDirs) {
                checkDrawableFolder(context, drawableDir);
            }
        }
    }

    private void checkDrawableFolder(Context context, File folder) {
        File[] files = folder.listFiles();
        if (files == null) return;

        Map<String, File> pngFiles = new HashMap<>();
        Map<String, File> ninePatchFiles = new HashMap<>();

        for (File f : files) {
            String name = f.getName();
            if (name.endsWith(".9.png")) {
                ninePatchFiles.put(name.substring(0, name.length() - 6), f);
            } else if (name.endsWith(".png")) {
                pngFiles.put(name.substring(0, name.length() - 4), f);
            }
        }

        for (String base : pngFiles.keySet()) {
            if (ninePatchFiles.containsKey(base)) {
                File png = pngFiles.get(base);
                context.report(ISSUE, Location.create(png),
                        "Clashing PNG and 9-PNG files: `" + base + ".png` and `" + base + ".9.png` map to the same drawable resource");
            }
        }
    }

    @Override
    public boolean filterIncident(Context context, Incident incident) {
        return true;
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        // No-op for this issue
    }

    @Override
    public UElementHandler createUastHandler() {
        return null;
    }

    @Override
    public void visitMethod(JavaContext context, UCallExpression call, PsiMethod method) {
        // No-op for this issue
    }

    @Override
    public void visitCallExpression(JavaContext context, UCallExpression node) {
        // No-op for this issue
    }

    @Override
    public void visitClass(JavaContext context, UClass node) {
        // No-op for this issue
    }

    @Override
    public void visitSimpleNameReferenceExpression(JavaContext context, USimpleNameReferenceExpression node) {
        // No-op for this issue
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return null;
    }
}