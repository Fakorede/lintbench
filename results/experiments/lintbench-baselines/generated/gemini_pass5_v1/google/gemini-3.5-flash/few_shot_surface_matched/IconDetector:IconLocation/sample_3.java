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
import com.android.tools.lint.detector.api.UElementHandler;
import com.intellij.psi.PsiMethod;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Element;

import java.io.File;
import java.util.Collection;
import java.util.List;

public class IconDetector extends Detector implements SourceCodeScanner, XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "IconLocation",
                    "Image defined in density-independent drawable folder",
                    "The res/drawable folder is intended for density-independent graphics such as "
                            + "shapes defined in XML. For bitmaps, move it to `drawable-mdpi` and consider "
                            + "providing higher and lower resolution versions in `drawable-ldpi`, `drawable-hdpi` "
                            + "and `drawable-xhdpi`. If the icon **really** is density independent (for example "
                            + "a solid color) you can place it in `drawable-nodpi`.",
                    Category.ICONS,
                    5,
                    Severity.WARNING,
                    new Implementation(
                            IconDetector.class,
                            Scope.RESOURCE_FILE_SCOPE));

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        super.beforeCheckRootProject(context);
        checkProjectForIcons(context);
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        super.afterCheckEachProject(context);
    }

    @Override
    public void filterIncident(@NonNull Incident incident) {
        super.filterIncident(incident);
    }

    @Override
    public boolean appliesTo(@NonNull Context context, @NonNull File file) {
        return super.appliesTo(context, file);
    }

    @Override
    public Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
    }

    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return null;
    }

    @Override
    public void visitMethod(@NonNull JavaContext context, @NonNull UCallExpression node, @NonNull PsiMethod method) {
    }

    public void visitCallExpression(@NonNull JavaContext context, @NonNull UCallExpression node) {
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
    }

    public void visitSimpleNameReferenceExpression(@NonNull JavaContext context, @NonNull USimpleNameReferenceExpression node) {
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return null;
    }

    private void checkProjectForIcons(@NonNull Context context) {
        for (File resFolder : context.getProject().getResourceFolders()) {
            File drawableFolder = new File(resFolder, "drawable");
            if (drawableFolder.exists() && drawableFolder.isDirectory()) {
                File[] files = drawableFolder.listFiles();
                if (files != null) {
                    for (File file : files) {
                        String name = file.getName().toLowerCase();
                        if (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".gif") || name.endsWith(".webp")) {
                            Location location = Location.create(file);
                            context.report(
                                    ISSUE,
                                    location,
                                    "The res/drawable folder is intended for density-independent graphics such as "
                                            + "shapes defined in XML. For bitmaps, move it to `drawable-mdpi` and consider "
                                            + "providing higher and lower resolution versions in `drawable-ldpi`, `drawable-hdpi` "
                                            + "and `drawable-xhdpi`. If the icon **really** is density independent (for example "
                                            + "a solid color) you can place it in `drawable-nodpi`."
                            );
                        }
                    }
                }
            }
        }
    }
}