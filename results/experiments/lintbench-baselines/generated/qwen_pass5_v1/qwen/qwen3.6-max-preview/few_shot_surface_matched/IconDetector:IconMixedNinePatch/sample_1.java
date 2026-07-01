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
import com.android.tools.lint.detector.api.ResourceFolder;
import com.android.tools.lint.detector.api.ResourceFolderType;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class IconDetector extends Detector implements SourceCodeScanner, XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "IconMixedNinePatch",
            "Clashing PNG and 9-PNG files",
            "If you accidentally name two separate resources file.png and file.9.png, " +
            "the image file and the nine patch file will both map to the same drawable resource, " +
            "@drawable/file, which is probably not what was intended.",
            Category.ICONS,
            5,
            Severity.WARNING,
            new Implementation(IconDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        // No-op
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        Set<String> pngNames = new HashSet<>();
        Set<String> ninePatchNames = new HashSet<>();
        Map<String, File> fileMap = new HashMap<>();

        for (ResourceFolder folder : context.getProject().getMainResources()) {
            if (folder.getType() == ResourceFolderType.DRAWABLE) {
                for (File file : folder.getFiles()) {
                    String name = file.getName();
                    if (name.endsWith(".9.png")) {
                        String base = name.substring(0, name.length() - 6);
                        ninePatchNames.add(base);
                        fileMap.put(name, file);
                    } else if (name.endsWith(".png")) {
                        String base = name.substring(0, name.length() - 4);
                        pngNames.add(base);
                        fileMap.put(name, file);
                    }
                }
            }
        }

        Set<String> clashes = new HashSet<>(pngNames);
        clashes.retainAll(ninePatchNames);

        for (String base : clashes) {
            File pngFile = fileMap.get(base + ".png");
            File ninePatchFile = fileMap.get(base + ".9.png");
            if (pngFile != null && ninePatchFile != null) {
                context.report(ISSUE, context.getLocation(pngFile),
                        "Found both " + pngFile.getName() + " and " + ninePatchFile.getName() +
                        "; they will map to the same drawable resource");
            }
        }
    }

    @Override
    public boolean filterIncident(@NonNull Incident incident) {
        return true;
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE;
    }

    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // No-op
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
        return null;
    }
}