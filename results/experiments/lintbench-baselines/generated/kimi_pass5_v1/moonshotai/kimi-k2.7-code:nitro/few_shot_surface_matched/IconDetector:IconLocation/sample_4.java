package com.android.tools.lint.checks;

import com.android.tools.lint.client.api.LintDriver;
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
import com.android.tools.lint.detector.api.TextFormat;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.*;
import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.jetbrains.uast.*;

public class IconDetector extends Detector implements SourceCodeScanner, XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "IconLocation",
                    "Image defined in density-independent drawable folder",
                    "The res/drawable folder is intended for density-independent graphics such as "
                            + "shapes defined in XML. For bitmaps, move it to `drawable-mdpi` and "
                            + "consider providing higher and lower resolution versions in "
                            + "`drawable-ldpi`, `drawable-hdpi` and `drawable-xhdpi`. If the icon "
                            + "really is density independent (for example a solid color) you can "
                            + "place it in `drawable-nodpi`. See "
                            + "https://developer.android.com/guide/practices/screens_support.html",
                    Category.ICONS,
                    5,
                    Severity.WARNING,
                    new Implementation(
                            IconDetector.class,
                            Scope.ALL_RESOURCE_FILES,
                            Scope.JAVA_FILE_SCOPE));

    private static final String MESSAGE =
            "Bitmap image defined in the density-independent res/drawable folder";

    private final Map<Project, Set<String>> mBitmapDrawableNames = new HashMap<>();
    private final Map<Project, Set<File>> mBitmapDrawableFiles = new HashMap<>();
    private final Set<String> mReported = new HashSet<>();

    @Override
    public void beforeCheckRootProject(Context context) {
        mBitmapDrawableNames.clear();
        mBitmapDrawableFiles.clear();
        mReported.clear();

        LintDriver driver = context.getDriver();
        if (driver == null) {
            return;
        }
        List<Project> projects = driver.getProjects();
        if (projects == null) {
            return;
        }

        for (Project project : projects) {
            Set<String> names = new HashSet<>();
            Set<File> files = new HashSet<>();
            List<File> resourceFolders = project.getResourceFolders();
            if (resourceFolders != null) {
                for (File res : resourceFolders) {
                    File drawable = new File(res, "drawable");
                    if (!drawable.isDirectory()) {
                        continue;
                    }
                    File[] children = drawable.listFiles();
                    if (children == null) {
                        continue;
                    }
                    for (File child : children) {
                        if (isBitmapFile(child)) {
                            names.add(getResourceName(child));
                            files.add(child);
                        }
                    }
                }
            }
            mBitmapDrawableNames.put(project, names);
            mBitmapDrawableFiles.put(project, files);
        }
    }

    @Override
    public void afterCheckEachProject(Context context) {
        Project project = context.getProject();
        Set<File> files = mBitmapDrawableFiles.get(project);
        if (files == null) {
            return;
        }
        for (File file : files) {
            String name = getResourceName(file);
            if (!mReported.add(projectKey(project, name))) {
                continue;
            }
            context.report(ISSUE, Location.create(file), MESSAGE);
        }
    }

    @Override
    public boolean filterIncident(Context context, Incident incident, TextFormat textFormat) {
        return true;
    }

    @Override
    public boolean appliesTo(com.android.resources.ResourceFolderType folderType) {
        return folderType == com.android.resources.ResourceFolderType.DRAWABLE;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("bitmap");
    }

    @Override
    public void visitElement(XmlContext context, org.w3c.dom.Element element) {
        File parent = context.file.getParentFile();
        if (parent == null || !"drawable".equals(parent.getName())) {
            return;
        }
        if (!mReported.add(context.file.getAbsolutePath())) {
            return;
        }
        context.report(ISSUE, element, context.getLocation(element), MESSAGE);
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Arrays.asList(
                UCallExpression.class,
                UMethod.class,
                UClass.class,
                USimpleNameReferenceExpression.class);
    }

    @Override
    public UElementHandler createUastHandler(JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitSimpleNameReferenceExpression(
                    USimpleNameReferenceExpression expression) {
                checkReference(context, expression);
            }

            @Override
            public void visitCallExpression(UCallExpression expression) {
                for (UExpression arg : expression.getValueArguments()) {
                    if (arg instanceof UReferenceExpression) {
                        checkReference(context, (UReferenceExpression) arg);
                    }
                }
            }

            @Override
            public void visitMethod(UMethod method) {}

            @Override
            public void visitClass(UClass clazz) {}
        };
    }

    private void checkReference(JavaContext context, UReferenceExpression expression) {
        PsiElement resolved = expression.resolve();
        if (!(resolved instanceof PsiField)) {
            return;
        }
        PsiField field = (PsiField) resolved;
        PsiClass containingClass = field.getContainingClass();
        if (containingClass == null) {
            return;
        }
        String className = containingClass.getQualifiedName();
        if (className == null || !className.endsWith(".R.drawable")) {
            return;
        }
        String name = field.getName();
        Project project = context.getProject();
        Set<String> names = mBitmapDrawableNames.get(project);
        if (names == null || !names.contains(name)) {
            return;
        }
        if (!mReported.add(projectKey(project, name))) {
            return;
        }
        context.report(ISSUE, expression, context.getLocation(expression), MESSAGE);
    }

    private static String projectKey(Project project, String name) {
        return project.getName() + ":" + name;
    }

    private static boolean isBitmapFile(File file) {
        if (!file.isFile()) {
            return false;
        }
        String name = file.getName().toLowerCase(Locale.US);
        return name.endsWith(".png")
                || name.endsWith(".jpg")
                || name.endsWith(".jpeg")
                || name.endsWith(".gif")
                || name.endsWith(".webp")
                || name.endsWith(".bmp");
    }

    private static String getResourceName(File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}