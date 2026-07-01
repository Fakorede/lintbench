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
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NonNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UElementHandler;
import org.jetbrains.uast.UReferenceExpression;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class RtlDetector extends LayoutDetector implements SourceCodeScanner, XmlScanner {

    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";

    public static final Issue ISSUE = Issue.create(
            "RtlCompat",
            "Right-to-left text compatibility issues",
            "API 17 adds a textAlignment attribute to specify text alignment. However, if you are supporting older versions than API 17, you must also specify a gravity or layout_gravity attribute, since older platforms will ignore the textAlignment attribute.",
            Category.I18N,
            5,
            Severity.ERROR,
            new Implementation(RtlDetector.class, Scope.RESOURCE_FILE_SCOPE, Scope.JAVA_FILE_SCOPE));

    @Override
    public boolean filterIncident(@NonNull Context context, @NonNull Incident incident) {
        return true;
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList("textAlignment");
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        if (context.getProject().getMinSdkVersion().getFeatureLevel() >= 17) {
            return;
        }
        Element element = attribute.getOwnerElement();
        boolean hasGravity = element.hasAttributeNS(ANDROID_NS, "gravity")
                || element.hasAttributeNS(ANDROID_NS, "layout_gravity");
        if (!hasGravity) {
            context.report(ISSUE, attribute, context.getLocation(attribute),
                    "To support older versions than API 17, you must also specify a gravity or layout_gravity attribute.");
        }
    }

    @Override
    public UElementHandler createUastHandler() {
        return new UElementHandler() {
            @Override
            public void visitSimpleNameReferenceExpression(@NonNull USimpleNameReferenceExpression node) {
                // Delegation handled by the detector method below
            }
        };
    }

    @Override
    public void visitSimpleNameReferenceExpression(@NonNull JavaContext context, @NonNull UReferenceExpression node, @Nullable PsiElement target) {
        if (context.getProject().getMinSdkVersion().getFeatureLevel() >= 17) {
            return;
        }
        if (target instanceof PsiMethod) {
            PsiMethod method = (PsiMethod) target;
            if ("setTextAlignment".equals(method.getName())) {
                context.report(ISSUE, node, context.getLocation(node),
                        "To support older versions than API 17, you must also call setGravity().");
            }
        }
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(USimpleNameReferenceExpression.class);
    }
}