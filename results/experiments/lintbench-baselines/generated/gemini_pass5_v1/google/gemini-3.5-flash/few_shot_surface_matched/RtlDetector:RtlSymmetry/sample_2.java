package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.LintMap;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiElement;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.USimpleNameReferenceExpression;

public class RtlDetector extends LayoutDetector implements SourceCodeScanner, XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "RtlSymmetry",
                    "Padding and margin symmetry",
                    "If you specify padding or margin on the left side of a layout, you should "
                            + "probably also specify padding on the right side (and vice versa) "
                            + "for right-to-left layout symmetry.",
                    Category.RTL,
                    5,
                    Severity.WARNING,
                    new Implementation(
                            RtlDetector.class,
                            Scope.JAVA_AND_RESOURCE_FILES));

    public RtlDetector() {}

    @Override
    public boolean filterIncident(@NonNull Incident incident, @NonNull LintMap map) {
        return true;
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        super.afterCheckRootProject(context);
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList(
                "paddingLeft",
                "paddingRight",
                "layout_marginLeft",
                "layout_marginRight"
        );
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull org.w3c.dom.Attr attribute) {
        String name = attribute.getLocalName();
        if (name == null) {
            name = attribute.getName();
        }
        org.w3c.dom.Element element = attribute.getOwnerElement();
        if ("paddingLeft".equals(name)) {
            if (!element.hasAttributeNS(com.android.SdkConstants.ANDROID_URI, "paddingRight") &&
                    !element.hasAttribute("android:paddingRight")) {
                context.report(ISSUE, attribute, context.getLocation(attribute),
                        "To support left-to-right and right-to-left layouts, when you define `paddingLeft` you should also define `paddingRight` for symmetry");
            }
        } else if ("paddingRight".equals(name)) {
            if (!element.hasAttributeNS(com.android.SdkConstants.ANDROID_URI, "paddingLeft") &&
                    !element.hasAttribute("android:paddingLeft")) {
                context.report(ISSUE, attribute, context.getLocation(attribute),
                        "To support left-to-right and right-to-left layouts, when you define `paddingRight` you should also define `paddingLeft` for symmetry");
            }
        } else if ("layout_marginLeft".equals(name)) {
            if (!element.hasAttributeNS(com.android.SdkConstants.ANDROID_URI, "layout_marginRight") &&
                    !element.hasAttribute("android:layout_marginRight")) {
                context.report(ISSUE, attribute, context.getLocation(attribute),
                        "To support left-to-right and right-to-left layouts, when you define `layout_marginLeft` you should also define `layout_marginRight` for symmetry");
            }
        } else if ("layout_marginRight".equals(name)) {
            if (!element.hasAttributeNS(com.android.SdkConstants.ANDROID_URI, "layout_marginLeft") &&
                    !element.hasAttribute("android:layout_marginLeft")) {
                context.report(ISSUE, attribute, context.getLocation(attribute),
                        "To support left-to-right and right-to-left layouts, when you define `layout_marginRight` you should also define `layout_marginLeft` for symmetry");
            }
        }
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(USimpleNameReferenceExpression.class);
    }

    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitSimpleNameReferenceExpression(@NonNull USimpleNameReferenceExpression node) {
                RtlDetector.this.visitSimpleNameReferenceExpression(context, node);
            }
        };
    }

    public void visitSimpleNameReferenceExpression(@NonNull JavaContext context, @NonNull USimpleNameReferenceExpression node) {
        String identifier = node.getIdentifier();
        if ("paddingLeft".equals(identifier) || "paddingRight".equals(identifier) ||
                "layout_marginLeft".equals(identifier) || "layout_marginRight".equals(identifier)) {
            // Placeholder for scanning source code references to padding or margins if necessary.
        }
    }
}