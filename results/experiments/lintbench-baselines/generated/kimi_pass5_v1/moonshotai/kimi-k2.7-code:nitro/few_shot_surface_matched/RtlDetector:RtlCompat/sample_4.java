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
import com.android.tools.lint.detector.api.UElementHandler;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.USimpleNameReferenceExpression;

public class RtlDetector extends LayoutDetector implements SourceCodeScanner, XmlScanner {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_TEXT_ALIGNMENT = "textAlignment";
    private static final String ATTR_GRAVITY = "gravity";
    private static final String ATTR_LAYOUT_GRAVITY = "layout_gravity";

    private static final Implementation IMPLEMENTATION =
            new Implementation(RtlDetector.class, Scope.RESOURCE_FILE_SCOPE, Scope.JAVA_FILE_SCOPE);

    public static final Issue RTL_COMPAT =
            Issue.create(
                    "RtlCompat",
                    "RTL text alignment compatibility",
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
        return Collections.singletonList(ATTR_TEXT_ALIGNMENT);
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        if (!ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }
        if (context.getProject().getMinSdk() >= 17) {
            return;
        }
        Element element = attribute.getOwnerElement();
        if (element == null) {
            return;
        }
        if (element.hasAttributeNS(ANDROID_URI, ATTR_GRAVITY)
                || element.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_GRAVITY)) {
            return;
        }
        context.report(
                RTL_COMPAT,
                attribute,
                context.getLocation(attribute),
                "When using `textAlignment` on devices running API levels below 17 you must "
                        + "also specify `android:gravity` or `android:layout_gravity`");
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(USimpleNameReferenceExpression.class);
    }

    @Override
    public UElementHandler createUastHandler(JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitSimpleNameReferenceExpression(USimpleNameReferenceExpression node) {
                if (context.getProject().getMinSdk() >= 17) {
                    return;
                }
                String name = node.getIdentifier();
                if ("setTextAlignment".equals(name) || "textAlignment".equals(name)) {
                    PsiElement resolved = node.resolve();
                    if (resolved instanceof PsiMethod) {
                        context.report(
                                RTL_COMPAT,
                                node,
                                context.getLocation(node),
                                "Programmatically setting text alignment requires a corresponding "
                                        + "`setGravity` call for compatibility with API levels below 17");
                    }
                }
            }
        };
    }

    @Override
    public boolean filterIncident(Context context, Incident incident, Object scope) {
        if (incident.getIssue() == RTL_COMPAT && context.getProject().getMinSdk() >= 17) {
            return false;
        }
        return true;
    }

    @Override
    public void afterCheckRootProject(Context context) {
    }
}