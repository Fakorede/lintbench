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
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UElementHandler;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;

public class IconDetector extends Detector implements SourceCodeScanner, XmlScanner {

    public static final Issue ICON_LOCATION =
            Issue.create(
                    "IconLocation",
                    "Image defined in density-independent drawable folder",
                    "The res/drawable folder is intended for density-independent graphics such as "
                            + "shapes defined in XML. For bitmaps, move it to `drawable-mdpi` and "
                            + "consider providing higher and lower resolution versions in "
                            + "`drawable-ldpi`, `drawable-hdpi` and `drawable-xhdpi`. If the icon "
                            + "really is density independent (for example a solid color) you can "
                            + "place it in `drawable-nodpi`.",
                    Category.ICONS,
                    5,
                    Severity.WARNING,
                    new Implementation(
                            IconDetector.class,
                            Scope.RESOURCE_FILE_SCOPE,
                            Scope.JAVA_FILE_SCOPE));

    private static final String DRAWABLE_FOLDER = "drawable";
    private static final String NODPI_FOLDER = "drawable-nodpi";
    private static final String DRAWABLE_PREFIX = "@drawable/";

    private final Map<String, File> mBitmapDrawableFiles = new HashMap<>();
    private final Set<String> mReferencedBadIcons = new HashSet<>();
    private Project mRootProject;

    public IconDetector() {}

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mRootProject = context.getProject();
        mBitmapDrawableFiles.clear();
        mReferencedBadIcons.clear();

        List<File> resourceFolders = context.getProject().getResourceFolders();
        if (resourceFolders == null) {
            return;
        }

        for (File res : resourceFolders) {
            File drawable = new File(res, DRAWABLE_FOLDER);
            if (!drawable.isDirectory()) {
                continue;
            }
            File[] files = drawable.listFiles();
            if (files == null) {
                continue;
            }
            for (File file : files) {
                if (file.isFile() && isImageFile(file)) {
                    mBitmapDrawableFiles.put(getBaseName(file.getName()), file);
                }
            }
        }
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        if (context.getProject() != mRootProject) {
            return;
        }

        for (Map.Entry<String, File> entry : mBitmapDrawableFiles.entrySet()) {
            if (!mReferencedBadIcons.contains(entry.getKey())) {
                context.report(
                        ICON_LOCATION,
                        Location.create(entry.getValue()),
                        "Found bitmap drawable in density-independent drawable folder");
            }
        }

        mBitmapDrawableFiles.clear();
        mReferencedBadIcons.clear();
        mRootProject = null;
    }

    @Override
    public boolean filterIncident(@NonNull Context context, @NonNull Incident incident) {
        if (incident.getIssue() != ICON_LOCATION) {
            return true;
        }

        Location location = incident.getLocation();
        if (location == null) {
            return true;
        }

        File file = location.getFile();
        if (file == null) {
            return true;
        }

        String path = file.getPath();
        if (path.contains(File.separator + NODPI_FOLDER + File.separator)
                || path.endsWith(File.separator + NODPI_FOLDER)) {
            return false;
        }

        String name = file.getName().toLowerCase(Locale.US);
        if (isImageFileName(name)) {
            return path.contains(File.separator + DRAWABLE_FOLDER + File.separator)
                    || path.endsWith(File.separator + DRAWABLE_FOLDER);
        }

        return true;
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return true;
    }

    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        NamedNodeMap attributes = element.getAttributes();
        if (attributes == null) {
            return;
        }

        for (int i = 0; i < attributes.getLength(); i++) {
            Attr attr = (Attr) attributes.item(i);
            String value = attr.getValue();
            if (value != null && value.startsWith(DRAWABLE_PREFIX)) {
                String name = value.substring(DRAWABLE_PREFIX.length());
                if (mBitmapDrawableFiles.containsKey(name)
                        && mReferencedBadIcons.add(name)) {
                    context.report(
                            ICON_LOCATION,
                            attr,
                            context.getLocation(attr),
                            "The drawable `" + name + "` is a bitmap in the "
                                    + "density-independent drawable folder");
                }
            }
        }
    }

    @Nullable
    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(USimpleNameReferenceExpression.class);
    }

    @Nullable
    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitSimpleNameReferenceExpression(
                    @NonNull USimpleNameReferenceExpression node) {
                IconDetector.this.visitSimpleNameReferenceExpression(context, node);
            }

            @Override
            public void visitCallExpression(@NonNull UCallExpression node) {
                IconDetector.this.visitCallExpression(context, node);
            }

            @Override
            public void visitClass(@NonNull UClass node) {
                IconDetector.this.visitClass(context, node);
            }

            @Override
            public void visitMethod(@NonNull UMethod node) {
                IconDetector.this.visitMethod(context, node);
            }
        };
    }

    public void visitSimpleNameReferenceExpression(
            @NonNull JavaContext context,
            @NonNull USimpleNameReferenceExpression node) {
        PsiElement resolved = node.resolve();
        if (!(resolved instanceof PsiField)) {
            return;
        }

        PsiField field = (PsiField) resolved;
        PsiClass drawableClass = field.getContainingClass();
        if (drawableClass == null || !"drawable".equals(drawableClass.getName())) {
            return;
        }

        PsiClass rClass = drawableClass.getContainingClass();
        if (rClass == null) {
            return;
        }

        String rQualifiedName = rClass.getQualifiedName();
        if (rQualifiedName != null && rQualifiedName.startsWith("android.")) {
            return;
        }

        String name = field.getName();
        if (mBitmapDrawableFiles.containsKey(name) && mReferencedBadIcons.add(name)) {
            context.report(
                    ICON_LOCATION,
                    node,
                    context.getLocation(node),
                    "The drawable `" + name + "` is a bitmap in the "
                            + "density-independent drawable folder");
        }
    }

    public void visitMethod(@NonNull JavaContext context, @NonNull UMethod node) {
        // Not needed for IconLocation.
    }

    public void visitCallExpression(
            @NonNull JavaContext context, @NonNull UCallExpression node) {
        // Not needed for IconLocation.
    }

    public void visitClass(@NonNull JavaContext context, @NonNull UClass node) {
        // Not needed for IconLocation.
    }

    private static boolean isImageFile(@NonNull File file) {
        return isImageFileName(file.getName().toLowerCase(Locale.US));
    }

    private static boolean isImageFileName(@NonNull String name) {
        return name.endsWith(".png")
                || name.endsWith(".jpg")
                || name.endsWith(".jpeg")
                || name.endsWith(".gif")
                || name.endsWith(".webp")
                || name.endsWith(".bmp");
    }

    private static String getBaseName(@NonNull String fileName) {
        if (fileName.endsWith(".9.png")) {
            return fileName.substring(0, fileName.length() - ".9.png".length());
        }
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }
}