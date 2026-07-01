package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_DRAWABLE;
import static com.android.SdkConstants.ATTR_SRC;
import static com.android.SdkConstants.DOT_WEBP;
import static com.android.SdkConstants.TAG_BITMAP;
import static com.android.SdkConstants.TAG_ITEM;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
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
import com.intellij.psi.PsiMethod;
import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UElementHandler;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class IconDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(
                    IconDetector.class, Scope.RESOURCE_FILE_SCOPE, Scope.JAVA_FILE_SCOPE);

    public static final Issue WEBP_UNSUPPORTED =
            Issue.create(
                    "WebpUnsupported",
                    "WebP Unsupported",
                    "The WebP format requires Android 4.0 (API 15). Certain features, such as "
                            + "lossless encoding and transparency, require Android 4.2.1 "
                            + "(API 18; API 17 is 4.2.0).",
                    Category.ICONS,
                    5,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private int mMinSdk = -1;

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mMinSdk = context.getMainProject().getMinSdk();
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        mMinSdk = -1;
    }

    @Override
    public boolean filterIncident(
            @NonNull Context context, @NonNull Incident incident, boolean remove) {
        if (incident.getIssue() == WEBP_UNSUPPORTED && mMinSdk >= 15) {
            return false;
        }
        return true;
    }

    @Override
    public boolean appliesTo(@NonNull Context context, @NonNull File file) {
        String name = file.getName();
        return name.endsWith(".xml") || name.endsWith(".java") || name.endsWith(".kt");
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_BITMAP, TAG_ITEM);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        checkWebpAttribute(context, element, ATTR_SRC);
        checkWebpAttribute(context, element, ATTR_DRAWABLE);
    }

    private void checkWebpAttribute(
            @NonNull XmlContext context, @NonNull Element element, @NonNull String attrName) {
        if (!element.hasAttributeNS(ANDROID_URI, attrName)) {
            return;
        }
        String value = element.getAttributeNS(ANDROID_URI, attrName);
        if (value.endsWith(DOT_WEBP) && mMinSdk < 15) {
            Attr attr = element.getAttributeNodeNS(ANDROID_URI, attrName);
            context.report(
                    WEBP_UNSUPPORTED,
                    attr,
                    context.getLocation(attr),
                    "WebP images are not supported before API 15 "
                            + "(current minSdk is "
                            + mMinSdk
                            + ")");
        }
    }

    @Override
    @Nullable
    public UElementHandler createUastHandler(@NonNull final JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitCallExpression(@NonNull UCallExpression node) {
                for (UExpression arg : node.getValueArguments()) {
                    Object value = arg.evaluate();
                    if (value instanceof String) {
                        String s = (String) value;
                        if (s.endsWith(DOT_WEBP)) {
                            reportWebp(context, node, s);
                        }
                    }
                }
            }

            @Override
            public void visitSimpleNameReferenceExpression(
                    @NonNull USimpleNameReferenceExpression node) {
                String name = node.getIdentifier();
                if (name != null && name.endsWith(DOT_WEBP)) {
                    reportWebp(context, node, name);
                }
            }

            @Override
            public void visitMethod(@NonNull UMethod node) {
                // No method-level WebP checks required.
            }

            @Override
            public void visitClass(@NonNull UClass node) {
                // No class-level WebP checks required.
            }
        };
    }

    private void reportWebp(
            @NonNull JavaContext context, @NonNull UElement node, @NonNull String name) {
        if (mMinSdk >= 15) {
            return;
        }
        context.report(
                WEBP_UNSUPPORTED,
                node,
                context.getLocation(node),
                "WebP image `" + name + "` is not supported before API 15 "
                        + "(current minSdk is " + mMinSdk + ")");
    }

    @Override
    @Nullable
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Arrays.asList(
                UCallExpression.class,
                USimpleNameReferenceExpression.class,
                UMethod.class,
                UClass.class);
    }
}