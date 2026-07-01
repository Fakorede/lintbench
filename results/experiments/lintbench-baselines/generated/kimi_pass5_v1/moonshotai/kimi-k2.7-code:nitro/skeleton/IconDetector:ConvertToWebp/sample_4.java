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
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Element;

public class IconDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(IconDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    private static final String EXPLANATION =
            "The WebP format is typically more compact than PNG and JPEG. As of Android 4.2.1 "
                    + "it supports transparency and lossless conversion as well. Note that there is a "
                    + "quickfix in the IDE which lets you perform conversion.\n\n"
                    + "Previously, launcher icons were required to be in the PNG format but that "
                    + "restriction is no longer there, so lint now flags these.";

    public static final Issue ISSUE =
            Issue.create(
                    "ConvertToWebp",
                    "Convert to WebP",
                    EXPLANATION,
                    Category.ICONS,
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private int mMinSdk;

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mMinSdk = context.getMainProject().getMinSdk();
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        if (mMinSdk < 18) {
            return;
        }

        List<File> resourceDirs = context.getProject().getResourceDirectories();
        if (resourceDirs == null) {
            return;
        }

        for (File resDir : resourceDirs) {
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
                if (!typeDir.isDirectory()) {
                    continue;
                }
                File[] files = typeDir.listFiles();
                if (files == null) {
                    continue;
                }
                for (File file : files) {
                    if (!file.isFile()) {
                        continue;
                    }
                    if (isWebpCandidate(file.getName())) {
                        Location location = Location.create(file);
                        context.report(
                                ISSUE,
                                location,
                                "Could convert this image to WebP format for a smaller APK");
                    }
                }
            }
        }
    }

    private static boolean isWebpCandidate(String name) {
        String lower = name.toLowerCase(Locale.US);
        if (lower.endsWith(".9.png")) {
            return false;
        }
        return lower.endsWith(".png")
                || lower.endsWith(".jpg")
                || lower.endsWith(".jpeg");
    }

    @Override
    public boolean filterIncident(
            @NonNull Context context, @NonNull Incident incident, @NonNull LintMap map) {
        return true;
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE
                || folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.emptyList();
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
        return Collections.emptyList();
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