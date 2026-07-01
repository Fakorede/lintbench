package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.client.api.UElementHandler;
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
import com.intellij.psi.PsiMethod;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Element;

public class IconDetector extends Detector implements SourceCodeScanner, XmlScanner {

    public static final Issue WEBP_ISSUE =
            Issue.create(
                    "ConvertToWebp",
                    "Convert to WebP",
                    "The WebP format is typically more compact than PNG and JPEG. As of "
                            + "Android 4.2.1 it supports transparency and lossless conversion as well. "
                            + "Note that there is a quickfix in the IDE which lets you perform "
                            + "conversion.\n\n"
                            + "Previously, launcher icons were required to be in the PNG format but "
                            + "that restriction is no longer there, so lint now flags these.",
                    Category.ICONS,
                    5,
                    Severity.WARNING,
                    new Implementation(IconDetector.class, Scope.JAVA_AND_RESOURCE_FILES));

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        // No-op
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        File projectDir = context.getProject().getDir();
        File resDir = new File(projectDir, "src/main/res");
        if (resDir.exists() && resDir.isDirectory()) {
            File[] dirs = resDir.listFiles();
            if (dirs != null) {
                for (File dir : dirs) {
                    if (dir.isDirectory() && (dir.getName().startsWith("drawable") || dir.getName().startsWith("mipmap"))) {
                        File[] files = dir.listFiles();
                        if (files != null) {
                            for (File file : files) {
                                String name = file.getName().toLowerCase(Locale.US);
                                if ((name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg")) && !name.endsWith(".9.png")) {
                                    Location location = Location.create(file);
                                    context.report(
                                            WEBP_ISSUE,
                                            location,
                                            "The image format " + file.getName() + " can be converted to WebP to reduce app size"
                                    );
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public boolean filterIncident(@NonNull Context context, @NonNull Incident incident) {
        return true;
    }

    @Override
    public boolean appliesTo(@NonNull Context context, @NonNull File file) {
        return true;
    }

    @Override
    @Nullable
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("imageView");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String src = element.getAttributeNS("http://schemas.android.com/apk/res/android", "src");
        if (src != null && (src.endsWith(".png") || src.endsWith(".jpg") || src.endsWith(".jpeg"))) {
            context.report(
                    WEBP_ISSUE,
                    element,
                    context.getLocation(element),
                    "Convert this image to WebP to reduce app size"
            );
        }
    }

    @Override
    @Nullable
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitCallExpression(@NonNull UCallExpression node) {
                IconDetector.this.visitCallExpression(context, node);
            }

            @Override
            public void visitSimpleNameReferenceExpression(@NonNull USimpleNameReferenceExpression node) {
                IconDetector.this.visitSimpleNameReferenceExpression(context, node);
            }

            @Override
            public void visitMethod(@NonNull UMethod node) {
                IconDetector.this.visitMethod(context, node);
            }

            @Override
            public void visitClass(@NonNull UClass node) {
                IconDetector.this.visitClass(context, node);
            }
        };
    }

    public void visitMethod(@NonNull JavaContext context, @NonNull UMethod node) {
        // No-op
    }

    public void visitCallExpression(@NonNull JavaContext context, @NonNull UCallExpression node) {
        // No-op
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // No-op
    }

    public void visitClass(@NonNull JavaContext context, @NonNull UClass node, boolean isUastHandlerTriggered) {
        // No-op
    }

    public void visitSimpleNameReferenceExpression(@NonNull JavaContext context, @NonNull USimpleNameReferenceExpression node) {
        // No-op
    }

    @Override
    @Nullable
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        List<Class<? extends UElement>> types = new ArrayList<>();
        types.add(UCallExpression.class);
        types.add(USimpleNameReferenceExpression.class);
        types.add(UMethod.class);
        types.add(UClass.class);
        return types;
    }
}