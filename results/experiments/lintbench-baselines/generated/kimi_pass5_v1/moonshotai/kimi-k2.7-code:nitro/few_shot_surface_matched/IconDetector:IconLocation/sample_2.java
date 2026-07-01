package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFileType;
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
import com.android.tools.lint.detector.api.UElementHandler;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UReferenceExpression;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Element;

public class IconDetector extends Detector implements SourceCodeScanner, XmlScanner {

    public static final Issue ICON_LOCATION =
            Issue.create(
                    "IconLocation",
                    "Image defined in density-independent drawable folder",
                    "The res/drawable folder is intended for density-independent graphics such as "
                            + "shapes defined in XML. For bitmaps, move it to `drawable-mdpi` and "
                            + "consider providing higher and lower resolution versions in "
                            + "`drawable-ldpi`, `drawable-hdpi` and `drawable-xhdpi`. If the icon "
                            + "**really** is density independent (for example a solid color) you can "
                            + "place it in `drawable-nodpi`.",
                    Category.ICONS,
                    5,
                    Severity.WARNING,
                    new Implementation(
                            IconDetector.class,
                            EnumSet.of(Scope.RESOURCE_FILE_SCOPE, Scope.JAVA_FILE_SCOPE)));

    private static final String DRAWABLE_FOLDER = "drawable";
    private static final Collection<String> BITMAP_EXTENSIONS =
            Arrays.asList("png", "jpg", "jpeg", "gif", "webp", "bmp");

    private Map<String, Location> mBitmapDrawables;

    public IconDetector() {}

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mBitmapDrawables = new HashMap<>();
        Project project = context.getProject();
        List<File> resourceDirs = project.getResourceDirectories();
        for (File resDir : resourceDirs) {
            File drawableDir = new File(resDir, DRAWABLE_FOLDER);
            if (drawableDir.isDirectory()) {
                File[] files = drawableDir.listFiles();
                if (files != null) {
                    for (File file : files) {
                        String fileName = file.getName();
                        String extension = getExtension(fileName);
                        if (extension != null && BITMAP_EXTENSIONS.contains(extension)) {
                            String resourceName =
                                    fileName.substring(0, fileName.length() - extension.length() - 1);
                            mBitmapDrawables.put(resourceName, Location.create(file));
                        }
                    }
                }
            }
        }
    }

    @Nullable
    private static String getExtension(@NonNull String fileName) {
        int index = fileName.lastIndexOf('.');
        if (index != -1 && index < fileName.length() - 1) {
            return fileName.substring(index + 1).toLowerCase();
        }
        return null;
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        if (mBitmapDrawables == null || mBitmapDrawables.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Location> entry : mBitmapDrawables.entrySet()) {
            Location location = entry.getValue();
            context.report(
                    ICON_LOCATION,
                    location,
                    "Bitmap in density-independent drawable folder");
        }
        mBitmapDrawables = null;
    }

    @Nullable
    @Override
    public Severity filterIncident(
            @NonNull Context context, @NonNull Incident incident, @NonNull Severity severity) {
        return severity;
    }

    @Override
    public boolean appliesTo(
            @NonNull ResourceFolderType folderType, @NonNull ResourceFileType fileType) {
        return folderType == ResourceFolderType.DRAWABLE && fileType == ResourceFileType.XML;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("bitmap", "nine-patch");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();
        if ("bitmap".equals(tag) || "nine-patch".equals(tag)) {
            context.report(
                    ICON_LOCATION,
                    element,
                    context.getLocation(element),
                    "Bitmap defined in density-independent drawable folder");
        }
    }

    @Nullable
    @Override
    public UElementHandler createUastHandler(@NonNull final JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitMethod(@NonNull UMethod node) {
                // No-op; required for completeness.
            }

            @Override
            public void visitCallExpression(@NonNull UCallExpression node) {
                PsiMethod method = node.resolve();
                if (method == null) {
                    return;
                }
                String name = method.getName();
                if (!"setImageResource".equals(name)
                        && !"setImageDrawable".equals(name)
                        && !"getDrawable".equals(name)
                        && !"getDrawableForDensity".equals(name)) {
                    return;
                }
                for (UExpression argument : node.getValueArguments()) {
                    checkDrawableReference(context, argument);
                }
            }

            @Override
            public void visitSimpleNameReferenceExpression(
                    @NonNull USimpleNameReferenceExpression node) {
                checkDrawableReference(context, node);
            }

            @Override
            public void visitClass(@NonNull UClass node) {
                // No-op; required for completeness.
            }
        };
    }

    private void checkDrawableReference(@NonNull JavaContext context, @NonNull UElement node) {
        if (!(node instanceof UReferenceExpression)) {
            return;
        }
        UReferenceExpression reference = (UReferenceExpression) node;
        PsiElement resolved = reference.resolve();
        if (!(resolved instanceof PsiField)) {
            return;
        }
        PsiField field = (PsiField) resolved;
        PsiClass cls = field.getContainingClass();
        if (cls == null || !"drawable".equals(cls.getName())) {
            return;
        }
        PsiClass outer = cls.getContainingClass();
        if (outer == null || !"R".equals(outer.getName())) {
            return;
        }
        String name = field.getName();
        if (mBitmapDrawables != null && mBitmapDrawables.containsKey(name)) {
            mBitmapDrawables.remove(name);
            context.report(
                    ICON_LOCATION,
                    node,
                    context.getLocation(node),
                    "References a bitmap in the density-independent drawable folder");
        }
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // No-op; required for completeness.
    }

    @Nullable
    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Arrays.<Class<? extends UElement>>asList(
                UCallExpression.class,
                UMethod.class,
                USimpleNameReferenceExpression.class,
                UClass.class);
    }
}