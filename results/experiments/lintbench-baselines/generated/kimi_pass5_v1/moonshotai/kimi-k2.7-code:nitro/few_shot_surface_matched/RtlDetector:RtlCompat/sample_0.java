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
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UElementHandler;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class RtlDetector extends LayoutDetector implements SourceCodeScanner, XmlScanner {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    private static final Implementation IMPLEMENTATION =
            new Implementation(RtlDetector.class, Scope.RESOURCE_FILE_SCOPE, Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "RtlCompat",
                    "Right-to-left text compatibility issues",
                    "API 17 adds a `textAlignment` attribute to specify text alignment. However, "
                            + "if you are supporting older versions than API 17, you must also "
                            + "specify a `gravity` or `layout_gravity` attribute, since older "
                            + "platforms will ignore the `textAlignment` attribute.",
                    Category.RTL,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList("textAlignment");
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        if (context.getProject().getMinSdk() >= 17) {
            return;
        }

        Element element = attribute.getOwnerElement();
        if (element.getAttributeNodeNS(ANDROID_URI, "gravity") != null
                || element.getAttributeNodeNS(ANDROID_URI, "layout_gravity") != null) {
            return;
        }

        context.report(
                ISSUE,
                attribute,
                context.getLocation(attribute),
                "When using `textAlignment` for RTL support on platforms older than API 17, "
                        + "you must also specify `gravity` or `layout_gravity`");
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(USimpleNameReferenceExpression.class);
    }

    @Override
    public UElementHandler createUastHandler(JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitSimpleNameReferenceExpression(
                    USimpleNameReferenceExpression expression) {
                String name = expression.getIdentifier();
                if (!"LEFT".equals(name) && !"RIGHT".equals(name)) {
                    return;
                }

                PsiElement resolved = expression.resolve();
                if (!(resolved instanceof PsiField)) {
                    return;
                }

                PsiClass containingClass = ((PsiField) resolved).getContainingClass();
                if (containingClass == null) {
                    return;
                }

                String qualifiedName = containingClass.getQualifiedName();
                if ("android.view.Gravity".equals(qualifiedName)) {
                    context.report(
                            ISSUE,
                            expression,
                            context.getLocation(expression),
                            "Consider using `Gravity.START` or `Gravity.END` instead of `Gravity."
                                    + name
                                    + "` for RTL compatibility");
                }
            }
        };
    }

    @Override
    public boolean filterIncident(Context context, Incident incident) {
        return incident.getIssue() == ISSUE && context.getProject().getMinSdk() < 17;
    }

    @Override
    public void afterCheckRootProject(Context context) {
    }
}