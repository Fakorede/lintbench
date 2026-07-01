package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.client.api.IncidentFilter;
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
import com.android.tools.lint.detector.api.UElementHandler;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class IconDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final String DRAWABLE_PREFIX = "@drawable/";
    private static final String MIPMAP_PREFIX = "@mipmap/";

    private final Set<String> mReferencedIcons = new HashSet<>();
    private final Set<File> mReported = new HashSet<>();

    public static final Issue ISSUE =
            Issue.create(
                    "ConvertToWebp",
                    "Convert to WebP",
                    "The WebP format is typically more compact than PNG and JPEG. As of Android 4.2.1 "
                            + "it supports transparency and lossless conversion as well. Note that there is a "
                            + "quickfix in the IDE which lets you perform conversion.\n\n"
                            + "Previously, launcher icons were required to be in the PNG format but that "
                            + "restriction is no longer there, so lint now flags these.",
                    Category.ICONS,
                    5,
                    Severity.WARNING,
                    new Implementation(
                            IconDetector.class,
                            EnumSet.of(Scope.RESOURCE_FILE, Scope.JAVA_FILE),
                            EnumSet.of(Scope.RESOURCE_FILE)));

    public IconDetector() {}

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mReferencedIcons.clear();
        mReported.clear();
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        Project project = context.getProject();
        List<File> resourceFolders = project.getResourceFolders();
        if (resourceFolders == null) {
            return;
        }

        for (File resDir : resourceFolders) {
            if (!resDir.isDirectory()) {
                continue;
            }
            File[] typeDirs = resDir.listFiles();
            if (typeDirs == null) {
                continue;
            }
            for (File typeDir : typeDirs) {
                ResourceFolderType folderType = ResourceFolderType.getFolderType(typeDir.getName());
                if (folderType != ResourceFolderType.DRAWABLE
                        && folderType != ResourceFolderType.MIPMAP) {
                    continue;
                }
                File[] files = typeDir.listFiles();
                if (files == null) {
                    continue;
                }
                for (File file : files) {
                    if (isConvertibleImage(file)) {
                        reportImage(context, file);
                    }
                }
            }
        }
    }

    @Override
    public boolean filterIncident(
            @NonNull Context context,
            @NonNull Incident incident,
            @NonNull Severity severity,
            @NonNull TextFormat format,
            @Nullable IncidentFilter filter) {
        if (incident.getIssue() == ISSUE) {
            File file = incident.getLocation().getFile();
            if (file != null && file.getName().toLowerCase(Locale.US).endsWith(".webp")) {
                return true;
            }
        }
        return filter != null && !filter.accept(context, incident, severity, format);
    }

    @Override
    public boolean appliesTo(
            @NonNull ResourceFolderType folderType, @NonNull String fileName) {
        return fileName.endsWith(".xml")
                && (folderType == ResourceFolderType.DRAWABLE
                        || folderType == ResourceFolderType.MIPMAP
                        || folderType == ResourceFolderType.LAYOUT
                        || folderType == ResourceFolderType.MENU);
    }

    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0, n = attributes.getLength(); i < n; i++) {
            Attr attr = (Attr) attributes.item(i);
            String value = attr.getValue();
            if (value.startsWith(DRAWABLE_PREFIX)) {
                mReferencedIcons.add(value.substring(DRAWABLE_PREFIX.length()));
            } else if (value.startsWith(MIPMAP_PREFIX)) {
                mReferencedIcons.add(value.substring(MIPMAP_PREFIX.length()));
            }
        }
    }

    @Nullable
    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitMethod(@NonNull UMethod node) {}

            @Override
            public void visitCallExpression(@NonNull UCallExpression node) {}

            @Override
            public void visitClass(@NonNull UClass node) {}

            @Override
            public void visitSimpleNameReferenceExpression(
                    @NonNull USimpleNameReferenceExpression node) {
                recordResourceReference(node.resolve());
            }
        };
    }

    @Nullable
    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Arrays.asList(
                UClass.class, UMethod.class, UCallExpression.class, USimpleNameReferenceExpression.class);
    }

    private void recordResourceReference(@Nullable PsiElement element) {
        if (!(element instanceof PsiField)) {
            return;
        }
        PsiField field = (PsiField) element;
        PsiClass containingClass = field.getContainingClass();
        if (containingClass == null) {
            return;
        }
        String typeName = containingClass.getName();
        PsiClass outerClass = containingClass.getContainingClass();
        if (outerClass != null
                && "R".equals(outerClass.getName())
                && ("drawable".equals(typeName) || "mipmap".equals(typeName))) {
            mReferencedIcons.add(field.getName());
        }
    }

    private static boolean isConvertibleImage(@NonNull File file) {
        String name = file.getName().toLowerCase(Locale.US);
        if (name.endsWith(".9.png")) {
            return false;
        }
        return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg");
    }

    private void reportImage(@NonNull Context context, @NonNull File file) {
        if (!mReported.add(file)) {
            return;
        }
        Location location = Location.create(file);
        context.report(
                ISSUE,
                location,
                "This image can be converted to WebP to reduce APK size.");
    }
}