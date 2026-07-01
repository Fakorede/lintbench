package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
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
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Element;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class IconDetector extends Detector implements SourceCodeScanner, XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "IconMixedNinePatch",
            "Clashing PNG and 9-PNG files",
            "If you accidentally name two separate resources `file.png` and `file.9.png`, " +
            "the image file and the nine patch file will both map to the same drawable " +
            "resource, `@drawable/file`, which is probably not what was intended.",
            Category.ICONS,
            5,
            Severity.WARNING,
            new Implementation(IconDetector.class, Scope.ALL_SCOPE));

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        // Initialization hook before analyzing the root project
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        Project project = context.getProject();
        List<File> resourceFolders = project.getResourceFolders();
        for (File resDir : resourceFolders) {
            File[] drawables = resDir.listFiles(file ->
                    file.isDirectory() && file.getName().startsWith("drawable"));
            if (drawables == null) continue;

            for (File drawableDir : drawables) {
                Set<String> pngs = new HashSet<>();
                Set<String> ninePatches = new HashSet<>();
                File[] files = drawableDir.listFiles();
                if (files == null) continue;

                for (File f : files) {
                    String name = f.getName();
                    if (name.endsWith(".9.png")) {
                        ninePatches.add(name.substring(0, name.length() - 6));
                    } else if (name.endsWith(".png")) {
                        pngs.add(name.substring(0, name.length() - 4));
                    }
                }

                for (String base : pngs) {
                    if (ninePatches.contains(base)) {
                        File clashFile = new File(drawableDir, base + ".png");
                        Location location = Location.create(clashFile);
                        context.report(ISSUE, location,
                                "Clashing PNG and 9-PNG files: " + base + ".png and " + base + ".9.png");
                    }
                }
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

    @NonNull
    @Override
    public Collection<String> getApplicableElements() {
        return Collections.emptyList();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // No XML element processing required for this issue
    }

    @Nullable
    @Override
    public UElementHandler createUastHandler() {
        return new UElementHandler() {
            @Override
            public void visitClass(@NonNull UClass node) {
                IconDetector.this.visitClass(null, node);
            }

            @Override
            public void visitMethod(@NonNull UMethod node) {
                IconDetector.this.visitMethod(null, node);
            }

            @Override
            public void visitCallExpression(@NonNull UCallExpression node) {
                IconDetector.this.visitCallExpression(null, node);
            }

            @Override
            public void visitSimpleNameReferenceExpression(@NonNull USimpleNameReferenceExpression node) {
                IconDetector.this.visitSimpleNameReferenceExpression(null, node);
            }
        };
    }

    public void visitMethod(@Nullable JavaContext context, @NonNull UMethod node) {
        // Stub implementation for SourceCodeScanner contract
    }

    public void visitCallExpression(@Nullable JavaContext context, @NonNull UCallExpression node) {
        // Stub implementation for SourceCodeScanner contract
    }

    public void visitClass(@Nullable JavaContext context, @NonNull UClass node) {
        // Stub implementation for SourceCodeScanner contract
    }

    public void visitSimpleNameReferenceExpression(@Nullable JavaContext context, @NonNull USimpleNameReferenceExpression node) {
        // Stub implementation for SourceCodeScanner contract
    }

    @Nullable
    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        List<Class<? extends UElement>> types = new ArrayList<>();
        types.add(UClass.class);
        types.add(UMethod.class);
        types.add(UCallExpression.class);
        types.add(USimpleNameReferenceExpression.class);
        return types;
    }
}