package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_BACKGROUND;
import static com.android.SdkConstants.ATTR_SRC;
import static com.android.SdkConstants.IMAGE_VIEW;
import static com.android.SdkConstants.TAG_BITMAP;

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
import com.android.tools.lint.detector.api.LintMap;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.UElementHandler;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiMethod;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.ULiteralExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class IconDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final int WEBP_MIN_API = 15;
    private static final int WEBP_LOSSLESS_TRANSPARENCY_MIN_API = 18;

    private static final String BITMAP_FACTORY = "android.graphics.BitmapFactory";
    private static final String BITMAP = "android.graphics.Bitmap";
    private static final String WEBP = "WEBP";

    private static final Implementation IMPLEMENTATION = new Implementation(
            IconDetector.class,
            EnumSet.of(Scope.RESOURCE_FILE_SCOPE, Scope.JAVA_FILE_SCOPE));

    public static final Issue WEBP_UNSUPPORTED = Issue.create(
            "WebpUnsupported",
            "WebP not supported",
            "The WebP format is supported on Android 4.0+ (API level 15). Lossless and "
                    + "transparent WebP images are only supported on Android 4.2.1+ (API level 18). "
                    + "Using WebP on older devices can lead to crashes or missing images.",
            Category.ICONS,
            6,
            Severity.ERROR,
            IMPLEMENTATION);

    public IconDetector() {
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
    }

    @Override
    public boolean filterIncident(@NonNull Context context, @NonNull Incident incident,
            @NonNull LintMap map) {
        int minSdk = context.getMainProject().getMinSdk();
        return minSdk < WEBP_MIN_API;
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT
                || folderType == ResourceFolderType.DRAWABLE
                || folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(IMAGE_VIEW, TAG_BITMAP);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (IMAGE_VIEW.equals(element.getTagName())) {
            checkWebpReference(context, element, ATTR_SRC);
            checkWebpReference(context, element, ATTR_BACKGROUND);
        } else {
            checkWebpReference(context, element, ATTR_SRC);
        }
    }

    private static void checkWebpReference(@NonNull XmlContext context, @NonNull Element element,
            @NonNull String attrName) {
        Attr attr = element.getAttributeNodeNS(ANDROID_URI, attrName);
        if (attr == null) {
            return;
        }
        String value = attr.getValue();
        if (value == null) {
            return;
        }
        if (isWebpReference(value)) {
            context.report(WEBP_UNSUPPORTED, attr, context.getLocation(attr),
                    "WebP images require API level " + WEBP_MIN_API + " or higher");
        }
    }

    private static boolean isWebpReference(@NonNull String value) {
        int slash = value.lastIndexOf('/');
        String name = slash >= 0 ? value.substring(slash + 1) : value;
        return name.toLowerCase().contains("webp");
    }

    @Override
    @Nullable
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Arrays.asList(
                UCallExpression.class,
                USimpleNameReferenceExpression.class,
                UClass.class,
                UMethod.class);
    }

    @Override
    @Nullable
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new WebpHandler(context);
    }

    private class WebpHandler extends UElementHandler {
        private final JavaContext mContext;

        WebpHandler(@NonNull JavaContext context) {
            mContext = context;
        }

        @Override
        public void visitMethod(@NonNull UMethod node) {
        }

        @Override
        public void visitClass(@NonNull UClass node) {
        }

        @Override
        public void visitCallExpression(@NonNull UCallExpression node) {
            PsiMethod method = node.resolve();
            if (method == null) {
                return;
            }
            String className = method.getContainingClass() != null
                    ? method.getContainingClass().getQualifiedName()
                    : null;
            String methodName = node.getMethodName();
            if (methodName == null) {
                return;
            }

            if (BITMAP_FACTORY.equals(className) && methodName.startsWith("decode")) {
                for (UExpression arg : node.getValueArguments()) {
                    if (arg instanceof ULiteralExpression) {
                        Object argValue = ((ULiteralExpression) arg).getValue();
                        if (argValue instanceof String) {
                            String path = (String) argValue;
                            if (path.toLowerCase().endsWith(".webp")) {
                                report(mContext, node, WEBP_MIN_API);
                                break;
                            }
                        }
                    }
                }
            }
        }

        @Override
        public void visitSimpleNameReferenceExpression(
                @NonNull USimpleNameReferenceExpression node) {
            if (WEBP.equals(node.getIdentifier())) {
                report(mContext, node, WEBP_MIN_API);
            }
        }

        private void report(@NonNull JavaContext context, @NonNull UElement node, int api) {
            context.report(WEBP_UNSUPPORTED, node, context.getLocation(node),
                    "WebP is not supported on Android versions older than API " + api);
        }
    }
}