package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
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

public class RtlDetector extends LayoutDetector implements SourceCodeScanner, XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "RtlCompat",
                    "Right-to-left text compatibility issues",
                    "API 17 adds a `textAlignment` attribute to specify text alignment. However, if"
                            + " you are supporting older versions than API 17, you must also"
                            + " specify a `gravity` or `layout_gravity` attribute, since older"
                            + " platforms will ignore the `textAlignment` attribute.",
                    Category.RTL,
                    5,
                    Severity.ERROR,
                    new Implementation(
                            RtlDetector.class, Scope.RESOURCE_FILE_SCOPE, Scope.JAVA_FILE_SCOPE));

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_TEXT_ALIGNMENT = "textAlignment";
    private static final String ATTR_GRAVITY = "gravity";
    private static final String ATTR_LAYOUT_GRAVITY = "layout_gravity";
    private static final String VIEW_CLASS = "android.view.View";

    @Override
    public boolean filterIncident(Context context, Incident incident) {
        if (incident.getIssue() == ISSUE) {
            return context.getMainProject().getMinSdkVersion().getApiLevel() < 17;
        }
        return true;
    }

    @Override
    public void afterCheckRootProject(Context context) {
    }

    @Override
    public Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_TEXT_ALIGNMENT);
    }

    @Override
    public void visitAttribute(XmlContext context, org.w3c.dom.Attr attribute) {
        if (!ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }

        org.w3c.dom.Element owner = attribute.getOwnerElement();
        if (owner.hasAttributeNS(ANDROID_URI, ATTR_GRAVITY)
                || owner.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_GRAVITY)) {
            return;
        }

        context.report(
                ISSUE,
                attribute,
                context.getLocation(attribute),
                "When using `textAlignment` for RTL compatibility you should also specify "
                        + "`gravity` or `layout_gravity` for older platforms");
    }

    @Override
    public UElementHandler createUastHandler(final JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitSimpleNameReferenceExpression(
                    USimpleNameReferenceExpression node) {
                String name = node.getIdentifier();
                if (name != null && name.startsWith("TEXT_ALIGNMENT_")) {
                    PsiElement resolved = node.resolve();
                    if (resolved instanceof PsiField) {
                        PsiClass containingClass = ((PsiField) resolved).getContainingClass();
                        if (containingClass != null
                                && VIEW_CLASS.equals(containingClass.getQualifiedName())) {
                            context.report(
                                    ISSUE,
                                    node,
                                    context.getLocation(node),
                                    "When using `textAlignment` for RTL compatibility you should"
                                            + " also specify `gravity` or `layout_gravity` for"
                                            + " older platforms");
                        }
                    }
                }
            }
        };
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.<Class<? extends UElement>>singletonList(
                USimpleNameReferenceExpression.class);
    }
}