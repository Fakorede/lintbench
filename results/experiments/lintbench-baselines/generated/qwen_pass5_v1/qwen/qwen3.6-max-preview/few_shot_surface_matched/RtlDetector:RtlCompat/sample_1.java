package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UElementHandler;
import org.jetbrains.uast.UReferenceExpression;
import org.jetbrains.uast.USimpleReferenceExpression;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class RtlDetector extends LayoutDetector implements SourceCodeScanner, XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "RtlCompat",
            "Right-to-left text compatibility issues",
            "API 17 adds a `textAlignment` attribute to specify text alignment. However, " +
            "if you are supporting older versions than API 17, you must also specify a " +
            "gravity or layout_gravity attribute, since older platforms will ignore the " +
            "`textAlignment` attribute.",
            Category.RTL,
            5,
            Severity.ERROR,
            new Implementation(RtlDetector.class, Scope.RESOURCE_FILE_SCOPE, Scope.JAVA_FILE_SCOPE));

    @Override
    public boolean filterIncident(Context context, Incident incident) {
        return true;
    }

    @Override
    public void afterCheckRootProject(Context context) {
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList("textAlignment");
    }

    @Override
    public void visitAttribute(XmlContext context, org.w3c.dom.Attr attribute) {
        if (context.getMainProject().getMinSdkVersion().getFeatureLevel() >= 17) {
            return;
        }
        org.w3c.dom.Element element = attribute.getOwnerElement();
        String ns = "http://schemas.android.com/apk/res/android";
        if (element.hasAttributeNS(ns, "gravity") || element.hasAttributeNS(ns, "layout_gravity")) {
            return;
        }
        context.report(ISSUE, attribute, context.getLocation(attribute),
                "When targeting API < 17, you must also specify `gravity` or `layout_gravity` " +
                "along with `textAlignment` for RTL compatibility.");
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(USimpleReferenceExpression.class);
    }

    @Override
    public UElementHandler createUastHandler() {
        return null;
    }

    @Override
    public void visitSimpleNameReferenceExpression(JavaContext context, UReferenceExpression node, UElement referenced) {
        if (context.getMainProject().getMinSdkVersion().getFeatureLevel() >= 17) {
            return;
        }
        String name = node.asSourceString();
        if ("setTextAlignment".equals(name) || name.startsWith("TEXT_ALIGNMENT_")) {
            context.report(ISSUE, node, context.getLocation(node),
                    "When targeting API < 17, you must also specify `gravity` or `layout_gravity` " +
                    "along with `textAlignment` for RTL compatibility.");
        }
    }
}