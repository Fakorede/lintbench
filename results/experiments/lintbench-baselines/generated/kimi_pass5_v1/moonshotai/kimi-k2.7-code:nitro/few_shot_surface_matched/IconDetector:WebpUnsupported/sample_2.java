package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiField;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UElementHandler;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.ULiteralExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Element;

public class IconDetector extends Detector implements SourceCodeScanner, XmlScanner {

    public static final Issue WEBP_UNSUPPORTED =
            Issue.create(
                    "WebpUnsupported",
                    "WebP not supported",
                    "The WebP image format is supported on Android 4.0 (API 15) and later. "
                            + "Lossless WebP and WebP with transparency require Android 4.2.1 "
                            + "(API 18). Using WebP images or Bitmap.CompressFormat.WEBP on "
                            + "earlier platforms will not work.",
                    Category.ICONS,
                    6,
                    Severity.ERROR,
                    new Implementation(
                            IconDetector.class,
                            Scope.JAVA_FILE_SCOPE,
                            Scope.RESOURCE_FILE_SCOPE,
                            Scope.MANIFEST_SCOPE));

    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";
    private static final String BITMAP_CLASS = "android.graphics.Bitmap";
    private static final String COMPRESS_FORMAT_CLASS = "android.graphics.Bitmap.CompressFormat";
    private static final String WEBP = "webp";

    private int mMinSdk = -1;

    @Override
    public void beforeCheckRootProject(Context context) {
        mMinSdk = context.getMainProject().getMinSdk();
    }

    @Override
    public void afterCheckEachProject(Context context) {
        // No per-project cleanup required.
    }

    @Override
    public boolean filterIncident(Context context, Incident incident) {
        return true;
    }

    @Override
    public boolean appliesTo(com.android.resources.ResourceFolderType folderType) {
        return folderType == com.android.resources.ResourceFolderType.DRAWABLE
                || folderType == com.android.resources.ResourceFolderType.MIPMAP;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("application", "bitmap");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (mMinSdk >= 18) {
            return;
        }

        String tag = element.getTagName();
        String value = null;

        if ("application".equals(tag)) {
            value = element.getAttributeNS(ANDROID_NS, "icon");
        } else if ("bitmap".equals(tag)) {
            value = element.getAttributeNS(ANDROID_NS, "src");
        }

        if (value != null && !value.isEmpty() && value.toLowerCase().contains(WEBP)) {
            reportIfNeeded(context, element, context.getLocation(element));
        }
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Arrays.asList(
                UCallExpression.class,
                USimpleNameReferenceExpression.class,
                UMethod.class,
                UClass.class);
    }

    @Override
    public UElementHandler createUastHandler(JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitMethod(UMethod node) {
                // No method-declaration checks are required.
            }

            @Override
            public void visitCallExpression(UCallExpression node) {
                checkCallExpression(context, node);
            }

            @Override
            public void visitClass(UClass node) {
                // No class-level checks are required.
            }

            @Override
            public void visitSimpleNameReferenceExpression(USimpleNameReferenceExpression node) {
                checkSimpleNameReference(context, node);
            }
        };
    }

    private void checkCallExpression(JavaContext context, UCallExpression node) {
        if (mMinSdk >= 18) {
            return;
        }

        String methodName = node.getMethodName();
        if (methodName == null) {
            return;
        }

        // Bitmap.compress(CompressFormat.WEBP, ...) is handled by the reference check.
        if ("compress".equals(methodName)
                && node.getReceiverType() != null
                && BITMAP_CLASS.equals(node.getReceiverType().getCanonicalText())) {
            return;
        }

        for (UExpression arg : node.getValueArguments()) {
            if (arg instanceof ULiteralExpression) {
                Object argValue = ((ULiteralExpression) arg).getValue();
                if (argValue instanceof String) {
                    String path = (String) argValue;
                    if (path.toLowerCase().contains(WEBP)) {
                        reportIfNeeded(context, node, context.getLocation(node));
                        return;
                    }
                }
            }
        }
    }

    private void checkSimpleNameReference(
            JavaContext context, USimpleNameReferenceExpression node) {
        if (mMinSdk >= 18) {
            return;
        }

        if (!"WEBP".equals(node.getIdentifier())) {
            return;
        }

        Object resolved = node.resolve();
        if (resolved instanceof PsiField) {
            PsiField field = (PsiField) resolved;
            if (field.getContainingClass() != null
                    && COMPRESS_FORMAT_CLASS.equals(field.getContainingClass().getQualifiedName())) {
                reportIfNeeded(context, node, context.getLocation(node));
            }
        }
    }

    private void reportIfNeeded(
            JavaContext context, UElement node, com.android.tools.lint.detector.api.Location location) {
        if (mMinSdk < 15) {
            context.report(
                    WEBP_UNSUPPORTED,
                    node,
                    location,
                    "WebP is not supported on platforms older than API 15");
        } else if (mMinSdk < 18) {
            context.report(
                    WEBP_UNSUPPORTED,
                    node,
                    location,
                    "Lossless or transparent WebP is only supported on API 18 and later");
        }
    }

    private void reportIfNeeded(
            XmlContext context, Element node, com.android.tools.lint.detector.api.Location location) {
        if (mMinSdk < 15) {
            context.report(
                    WEBP_UNSUPPORTED,
                    node,
                    location,
                    "WebP is not supported on platforms older than API 15");
        } else if (mMinSdk < 18) {
            context.report(
                    WEBP_UNSUPPORTED,
                    node,
                    location,
                    "Lossless or transparent WebP is only supported on API 18 and later");
        }
    }
}