package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LintMap;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Element;

public class IconDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(IconDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "ConvertToWebp",
                    "Convert to WebP",
                    "The WebP format is typically more compact than PNG and JPEG. As of Android 4.2.1 it supports transparency and lossless conversion as well. Note that there is a quickfix in the IDE which lets you perform conversion. Previously, launcher icons were required to be in the PNG format but that restriction is no longer there, so lint now flags these.",
                    Category.ICONS,
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private static final int WEBP_MIN_SDK = 18;
    private static final String MIN_SDK_KEY = "minSdk";

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        // No cross-project state is required for this check.
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        for (java.io.File resDir : context.getProject().getResourceDirectories()) {
            checkResourceDirectory(context, resDir);
        }
    }

    private void checkResourceDirectory(@NonNull Context context, @NonNull java.io.File resDir) {
        if (!resDir.isDirectory()) {
            return;
        }
        java.io.File[] typeDirs = resDir.listFiles();
        if (typeDirs == null) {
            return;
        }
        for (java.io.File typeDir : typeDirs) {
            String typeName = typeDir.getName();
            if (!typeName.startsWith("drawable") && !typeName.startsWith("mipmap")) {
                continue;
            }
            checkImageFolder(context, typeDir);
        }
    }

    private void checkImageFolder(@NonNull Context context, @NonNull java.io.File folder) {
        java.io.File[] files = folder.listFiles();
        if (files == null) {
            return;
        }
        for (java.io.File file : files) {
            if (!file.isFile()) {
                continue;
            }
            String name = file.getName().toLowerCase();
            if (name.endsWith(".9.png")) {
                continue;
            }
            if (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg")) {
                LintMap metadata = new LintMap();
                metadata.putInt(MIN_SDK_KEY, WEBP_MIN_SDK);
                Incident incident =
                        new Incident(
                                ISSUE,
                                context.getLocation(file),
                                "This image can be converted to WebP format for a smaller APK size",
                                metadata);
                context.report(incident);
            }
        }
    }

    @Override
    public boolean filterIncident(
            @NonNull Context context, @NonNull Incident incident, @NonNull LintMap map) {
        int threshold = map.getInt(MIN_SDK_KEY, WEBP_MIN_SDK);
        return context.getMainProject().getMinSdkVersion().getApiLevel() >= threshold;
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE || folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return java.util.Collections.emptyList();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Not used for the ConvertToWebp check.
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // Not used for the ConvertToWebp check.
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return java.util.Collections.emptyList();
    }

    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitMethod(@NonNull UMethod node) {
                // Not used for the ConvertToWebp check.
            }

            @Override
            public void visitCallExpression(@NonNull UCallExpression node) {
                // Not used for the ConvertToWebp check.
            }

            @Override
            public void visitSimpleNameReferenceExpression(
                    @NonNull USimpleNameReferenceExpression node) {
                // Not used for the ConvertToWebp check.
            }
        };
    }
}