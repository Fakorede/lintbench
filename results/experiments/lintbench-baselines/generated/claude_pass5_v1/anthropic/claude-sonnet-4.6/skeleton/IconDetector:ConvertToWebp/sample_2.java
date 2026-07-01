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
import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Element;

public class IconDetector extends Detector implements Detector.SourceCodeScanner, Detector.XmlScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(IconDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

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
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION);

    /** Minimum API level that supports WebP with lossless and transparency */
    private static final int WEBP_MIN_API = 18;

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        // Nothing to initialize for this check
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        // Nothing to finalize for this check
    }

    @Override
    public boolean filterIncident(
            @NonNull Context context, @NonNull Incident incident, @NonNull LintMap map) {
        // Check if the project's min SDK supports WebP with lossless/transparency
        int minSdk = context.getMainProject().getMinSdk();
        return minSdk >= WEBP_MIN_API;
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
        // Check XML resource files for PNG/JPEG references that could be WebP
        File file = context.file;
        String name = file.getName();
        if (isPngOrJpeg(name)) {
            reportFile(context, file);
        }
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // Not used in this detector
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
                // Not used in this detector
            }

            @Override
            public void visitCallExpression(@NonNull UCallExpression node) {
                // Not used in this detector
            }

            @Override
            public void visitSimpleNameReferenceExpression(@NonNull USimpleNameReferenceExpression node) {
                // Not used in this detector
            }
        };
    }

    /**
     * Reports a PNG or JPEG file that could be converted to WebP.
     */
    private void reportFile(@NonNull XmlContext context, @NonNull File file) {
        String name = file.getName();
        String message;
        if (name.endsWith(".png")) {
            message =
                    "One or more images in this project can be converted to the WebP format "
                            + "which typically results in smaller file sizes, even for lossless "
                            + "conversion";
        } else {
            message =
                    "One or more images in this project can be converted to the WebP format "
                            + "which typically results in smaller file sizes";
        }

        Incident incident = new Incident(ISSUE, context.getLocation(context.document.getDocumentElement()), message);
        context.report(incident, new LintMap());
    }

    /**
     * Returns true if the given filename is a PNG or JPEG image.
     */
    private static boolean isPngOrJpeg(@NonNull String name) {
        String lower = name.toLowerCase();
        return lower.endsWith(".png")
                || lower.endsWith(".jpg")
                || lower.endsWith(".jpeg");
    }

    /**
     * Returns true if the given file is a PNG image (not a 9-patch).
     */
    private static boolean isPng(@NonNull File file) {
        String name = file.getName().toLowerCase();
        return name.endsWith(".png") && !name.endsWith(".9.png");
    }

    /**
     * Returns true if the given file is a JPEG image.
     */
    private static boolean isJpeg(@NonNull File file) {
        String name = file.getName().toLowerCase();
        return name.endsWith(".jpg") || name.endsWith(".jpeg");
    }
}