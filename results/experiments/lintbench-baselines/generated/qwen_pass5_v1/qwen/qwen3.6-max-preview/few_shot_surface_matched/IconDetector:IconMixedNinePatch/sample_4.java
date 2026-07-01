package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
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

public class IconDetector extends Detector implements SourceCodeScanner, XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "IconMixedNinePatch",
            "Clashing PNG and 9-PNG files",
            "If you accidentally name two separate resources `file.png` and `file.9.png`, "
                    + "the image file and the nine patch file will both map to the same drawable "
                    + "resource, `@drawable/file`, which is probably not what was intended.",
            Category.ICONS,
            5,
            Severity.WARNING,
            new Implementation(IconDetector.class, Scope.RESOURCE_FILE_SCOPE, Scope.JAVA_FILE_SCOPE));

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        super.beforeCheckRootProject(context);
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        super.afterCheckEachProject(context);
        checkForMixedNinePatches(context);
    }

    @Override
    public boolean filterIncident(@NonNull Context context, @NonNull Incident incident) {
        return true;
    }

    @Override
    public boolean appliesTo(@NonNull Context context, @NonNull File file) {
        return true;
    }

    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // No XML element processing required for this issue
    }

    @Nullable
    @Override
    public UElementHandler createUastHandler() {
        return null;
    }

    @Override
    public void visitMethod(@NonNull JavaContext context, @NonNull UCallExpression call, @NonNull PsiMethod method) {
        // No-op
    }

    @Override
    public void visitCallExpression(@NonNull JavaContext context, @NonNull UCallExpression call) {
        // No-op
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass node) {
        // No-op
    }

    @Override
    public void visitSimpleNameReferenceExpression(@NonNull JavaContext context, @NonNull USimpleNameReferenceExpression node) {
        // No-op
    }

    @Nullable
    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.emptyList();
    }

    private void checkForMixedNinePatches(@NonNull Context context) {
        List<File> resourceFolders = context.getProject().getResourceFolders();
        for (File resDir : resourceFolders) {
            File[] drawableDirs = resDir.listFiles(file -> file.isDirectory() && file.getName().startsWith("drawable"));
            if (drawableDirs == null) continue;

            for (File drawableDir : drawableDirs) {
                Map<String, File> pngFiles = new HashMap<>();
                Map<String, File> ninePatchFiles = new HashMap<>();

                File[] files = drawableDir.listFiles();
                if (files == null) continue;

                for (File f : files) {
                    String name = f.getName();
                    if (name.endsWith(".9.png")) {
                        String base = name.substring(0, name.length() - 6);
                        ninePatchFiles.put(base, f);
                    } else if (name.endsWith(".png")) {
                        String base = name.substring(0, name.length() - 4);
                        pngFiles.put(base, f);
                    }
                }

                for (String base : pngFiles.keySet()) {
                    if (ninePatchFiles.containsKey(base)) {
                        File pngFile = pngFiles.get(base);
                        File ninePatchFile = ninePatchFiles.get(base);
                        String message = String.format(
                                "Both `%s` and `%s` exist. They will map to the same drawable resource `@drawable/%s`.",
                                pngFile.getName(), ninePatchFile.getName(), base);
                        context.report(ISSUE, Location.create(pngFile), message);
                    }
                }
            }
        }
    }
}