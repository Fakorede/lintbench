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
import com.android.tools.lint.detector.api.LintMap;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiMethod;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Element;

public class IconDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final String KEY_MIN_SDK = "minSdk";
    private static final int WEBP_MIN_SDK = 15;
    private static final int WEBP_EXTENDED_MIN_SDK = 18;

    private static final String WEBP_FORMAT_NAME = "webp";
    private static final String BITMAP_COMPRESS_FORMAT_CLASS =
            "android.graphics.Bitmap.CompressFormat";
    private static final String BITMAP_FACTORY_CLASS = "android.graphics.BitmapFactory";
    private static final String BITMAP_CLASS = "android.graphics.Bitmap";
    private static final String WEBP_LOSSY_FORMAT = "WEBP";
    private static final String WEBP_LOSSLESS_FORMAT = "WEBP_LOSSLESS";
    private static final String WEBP_LOSSY_NEW_FORMAT = "WEBP_LOSSY";

    public static final Issue WEBP_UNSUPPORTED =
            Issue.create(
                    "WebpUnsupported",
                    "WebP Unsupported",
                    "The WebP format requires Android 4.0 (API 15). Certain features, such as "
                            + "lossless encoding and transparency, requires Android 4.2.1 "
                            + "(API 18; API 17 is 4.2.0.)",
                    Category.ICONS,
                    5,
                    Severity.ERROR,
                    new Implementation(
                            IconDetector.class,
                            EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE),
                            Scope.JAVA_FILE_SCOPE,
                            Scope.RESOURCE_FILE_SCOPE));

    private int mMinSdk = 1;

    public IconDetector() {}

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        Project project = context.getProject();
        mMinSdk = project.getMinSdk();
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        mMinSdk = 1;
    }

    @Override
    public boolean filterIncident(
            @NonNull Context context, @NonNull Incident incident, @NonNull LintMap map) {
        int minSdk = map.getInt(KEY_MIN_SDK, 1);
        int currentMinSdk = context.getProject().getMinSdk();
        if (currentMinSdk >= minSdk) {
            return false;
        }
        return true;
    }

    @Override
    public boolean appliesTo(@NonNull com.android.tools.lint.detector.api.ResourceFolderType folderType) {
        return folderType == com.android.tools.lint.detector.api.ResourceFolderType.DRAWABLE
                || folderType == com.android.tools.lint.detector.api.ResourceFolderType.MIPMAP;
    }

    // ---- XmlScanner ----

    @Override
    @Nullable
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("bitmap");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        org.w3c.dom.Attr srcAttr = element.getAttributeNode("android:src");
        if (srcAttr == null) {
            srcAttr = element.getAttributeNode("src");
        }
        if (srcAttr != null) {
            String value = srcAttr.getValue();
            if (value != null && value.toLowerCase(java.util.Locale.US).endsWith(".webp")) {
                reportWebpUsage(context, element, false);
            }
        }

        // Check if the element itself references a webp
        String tagName = element.getTagName();
        if ("bitmap".equals(tagName)) {
            org.w3c.dom.Attr androidSrc = element.getAttributeNode("android:src");
            if (androidSrc != null) {
                String val = androidSrc.getValue();
                if (val != null && val.toLowerCase(java.util.Locale.US).contains(WEBP_FORMAT_NAME)) {
                    reportWebpUsage(context, element, false);
                }
            }
        }
    }

    private void reportWebpUsage(
            @NonNull XmlContext context, @NonNull Element element, boolean requiresExtended) {
        int requiredApi = requiresExtended ? WEBP_EXTENDED_MIN_SDK : WEBP_MIN_SDK;
        int currentMinSdk = context.getProject().getMinSdk();
        if (currentMinSdk >= requiredApi) {
            return;
        }
        String message;
        if (requiresExtended) {
            message =
                    "WebP with lossless encoding or transparency requires API "
                            + WEBP_EXTENDED_MIN_SDK
                            + " (current min is "
                            + currentMinSdk
                            + ")";
        } else {
            message =
                    "WebP requires API "
                            + WEBP_MIN_SDK
                            + " (current min is "
                            + currentMinSdk
                            + ")";
        }
        LintMap map = new LintMap();
        map.put(KEY_MIN_SDK, requiredApi);
        Incident incident =
                new Incident(WEBP_UNSUPPORTED, element, context.getLocation(element), message, null);
        context.report(incident, map);
    }

    // ---- SourceCodeScanner ----

    @Override
    @Nullable
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Arrays.asList(
                UCallExpression.class,
                USimpleNameReferenceExpression.class);
    }

    @Override
    @Nullable
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitCallExpression(@NonNull UCallExpression expression) {
                IconDetector.this.visitCallExpression(context, expression);
            }

            @Override
            public void visitSimpleNameReferenceExpression(
                    @NonNull USimpleNameReferenceExpression expression) {
                IconDetector.this.visitSimpleNameReferenceExpression(context, expression);
            }
        };
    }

    @Override
    public void visitCallExpression(
            @NonNull JavaContext context, @NonNull UCallExpression expression) {
        PsiMethod method = expression.resolve();
        if (method == null) {
            return;
        }
        visitMethod(context, expression, method);
    }

    @Override
    public void visitMethod(
            @NonNull JavaContext context,
            @NonNull UCallExpression call,
            @NonNull PsiMethod method) {
        String methodName = method.getName();
        com.intellij.psi.PsiClass containingClass = method.getContainingClass();
        if (containingClass == null) {
            return;
        }
        String qualifiedName = containingClass.getQualifiedName();
        if (qualifiedName == null) {
            return;
        }

        if ((BITMAP_CLASS.equals(qualifiedName) || qualifiedName.startsWith("android.graphics.Bitmap"))
                && "compress".equals(methodName)) {
            List<org.jetbrains.uast.UExpression> args = call.getValueArguments();
            if (!args.isEmpty()) {
                org.jetbrains.uast.UExpression formatArg = args.get(0);
                String argText = formatArg.asSourceString();
                if (argText != null) {
                    boolean isWebp = argText.contains(WEBP_LOSSY_FORMAT)
                            || argText.contains(WEBP_FORMAT_NAME.toUpperCase(java.util.Locale.US));
                    boolean isExtended = argText.contains(WEBP_LOSSLESS_FORMAT);
                    if (isWebp || isExtended) {
                        reportWebpCodeUsage(context, call, isExtended);
                    }
                }
            }
        }
    }

    public void visitSimpleNameReferenceExpression(
            @NonNull JavaContext context,
            @NonNull USimpleNameReferenceExpression expression) {
        String name = expression.getIdentifier();
        if (name == null) {
            return;
        }
        com.intellij.psi.PsiElement resolved = expression.resolve();
        if (resolved instanceof com.intellij.psi.PsiField) {
            com.intellij.psi.PsiField field = (com.intellij.psi.PsiField) resolved;
            com.intellij.psi.PsiClass containingClass = field.getContainingClass();
            if (containingClass == null) {
                return;
            }
            String qualifiedName = containingClass.getQualifiedName();
            if (BITMAP_COMPRESS_FORMAT_CLASS.equals(qualifiedName)) {
                boolean isLossless = WEBP_LOSSLESS_FORMAT.equals(name);
                boolean isWebp = WEBP_LOSSY_FORMAT.equals(name)
                        || WEBP_LOSSY_NEW_FORMAT.equals(name)
                        || isLossless;
                if (isWebp) {
                    reportWebpCodeUsage(context, expression, isLossless);
                }
            }
        }
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // Not used for this detector but required by the interface
    }

    private void reportWebpCodeUsage(
            @NonNull JavaContext context,
            @NonNull UElement element,
            boolean requiresExtended) {
        int requiredApi = requiresExtended ? WEBP_EXTENDED_MIN_SDK : WEBP_MIN_SDK;
        int currentMinSdk = context.getProject().getMinSdk();
        if (currentMinSdk >= requiredApi) {
            return;
        }
        String message;
        if (requiresExtended) {
            message =
                    "WebP with lossless encoding or transparency requires API "
                            + WEBP_EXTENDED_MIN_SDK
                            + " (current min is "
                            + currentMinSdk
                            + ")";
        } else {
            message =
                    "WebP requires API "
                            + WEBP_MIN_SDK
                            + " (current min is "
                            + currentMinSdk
                            + ")";
        }
        LintMap map = new LintMap();
        map.put(KEY_MIN_SDK, requiredApi);
        Incident incident =
                new Incident(
                        WEBP_UNSUPPORTED,
                        element,
                        context.getLocation(element),
                        message,
                        null);
        context.report(incident, map);
    }
}