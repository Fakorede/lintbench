package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UElementHandler;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class RtlDetector extends LayoutDetector implements SourceCodeScanner, XmlScanner {

    public static final Issue RTL_COMPAT =
            Issue.create(
                    "RtlCompat",
                    "Right-to-left text compatibility issue",
                    "API 17 adds a `textAlignment` attribute to specify text alignment. "
                            + "However, if you are supporting older versions than API 17, you "
                            + "must also specify a `gravity` or `layout_gravity` attribute, since "
                            + "older platforms will ignore the `textAlignment` attribute.",
                    Category.RTL,
                    6,
                    Severity.ERROR,
                    new Implementation(
                            RtlDetector.class, Scope.RESOURCE_FILE_SCOPE, Scope.JAVA_FILE_SCOPE));

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_TEXT_ALIGNMENT = "textAlignment";
    private static final String ATTR_GRAVITY = "gravity";
    private static final String ATTR_LAYOUT_GRAVITY = "layout_gravity";
    private static final String VIEW_CLASS = "android.view.View";
    private static final String TEXT_ALIGNMENT_PREFIX = "TEXT_ALIGNMENT_";

    public RtlDetector() {}

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_TEXT_ALIGNMENT);
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        if (!ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }
        if (!ATTR_TEXT_ALIGNMENT.equals(attribute.getLocalName())) {
            return;
        }
        if (context.getMainProject().getMinSdk() >= 17) {
            return;
        }

        Element element = attribute.getOwnerElement();
        if (element.hasAttributeNS(ANDROID_URI, ATTR_GRAVITY)
                || element.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_GRAVITY)) {
            return;
        }

        context.report(
                RTL_COMPAT,
                attribute,
                context.getLocation(attribute),
                "When supporting SDK versions older than API 17 you must also add a "
                        + "`gravity` or `layout_gravity` attribute alongside `textAlignment`; "
                        + "older platforms will ignore `textAlignment`.");
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(USimpleNameReferenceExpression.class);
    }

    @Override
    public UElementHandler createUastHandler(JavaContext context) {
        return new RtlHandler(context);
    }

    @Override
    public boolean filterIncident(Context context, Incident incident) {
        return false;
    }

    @Override
    public void afterCheckRootProject(Context context) {}

    private static class RtlHandler extends UElementHandler {
        private final JavaContext mContext;

        RtlHandler(JavaContext context) {
            this.mContext = context;
        }

        @Override
        public void visitSimpleNameReferenceExpression(USimpleNameReferenceExpression node) {
            if (mContext.getMainProject().getMinSdk() >= 17) {
                return;
            }

            PsiElement resolved = node.resolve();
            if (resolved instanceof PsiField) {
                PsiField field = (PsiField) resolved;
                String name = field.getName();
                if (name == null || !name.startsWith(TEXT_ALIGNMENT_PREFIX)) {
                    return;
                }

                PsiClass containingClass = field.getContainingClass();
                if (containingClass != null
                        && VIEW_CLASS.equals(containingClass.getQualifiedName())) {
                    mContext.report(
                            RTL_COMPAT,
                            node,
                            mContext.getLocation(node),
                            "Using `View." + name + "` is not supported on platforms older than "
                                    + "API 17; use a `gravity` value instead.");
                }
            }
        }
    }
}