package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
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
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UElementHandler;
import org.jetbrains.uast.UReferenceExpression;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class RtlDetector extends LayoutDetector implements SourceCodeScanner, XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "RtlSymmetry",
            "Padding and margin symmetry",
            "If you specify padding or margin on the left side of a layout, you should "
                    + "probably also specify padding on the right side (and vice versa) for "
                    + "right-to-left layout symmetry.",
            Category.RTL,
            5,
            Severity.WARNING,
            new Implementation(RtlDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public boolean filterIncident(@NonNull Incident incident) {
        return true;
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        // No-op
    }

    @Nullable
    @Override
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList("paddingLeft", "paddingRight", "layout_marginLeft", "layout_marginRight");
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull org.w3c.dom.Attr attribute) {
        String name = attribute.getLocalName();
        org.w3c.dom.Element element = attribute.getOwnerElement();
        String namespace = attribute.getNamespaceURI();
        if (!"http://schemas.android.com/apk/res/android".equals(namespace)) {
            return;
        }

        boolean isLeft = name.equals("paddingLeft") || name.equals("layout_marginLeft");
        boolean isRight = name.equals("paddingRight") || name.equals("layout_marginRight");

        if (isLeft) {
            String counterpart = name.equals("paddingLeft") ? "paddingRight" : "layout_marginRight";
            if (!element.hasAttributeNS(namespace, counterpart)) {
                context.report(ISSUE, attribute, context.getLocation(attribute),
                        "When you define `" + name + "` you should probably also define `" + counterpart + "` for right-to-left symmetry");
            }
        } else if (isRight) {
            String counterpart = name.equals("paddingRight") ? "paddingLeft" : "layout_marginLeft";
            if (!element.hasAttributeNS(namespace, counterpart)) {
                context.report(ISSUE, attribute, context.getLocation(attribute),
                        "When you define `" + name + "` you should probably also define `" + counterpart + "` for right-to-left symmetry");
            }
        }
    }

    @Nullable
    @Override
    public UElementHandler createUastHandler() {
        return new UElementHandler() {
            @Override
            public void visitReferenceExpression(@NonNull UReferenceExpression node) {
                // Delegates to visitSimpleNameReferenceExpression
            }
        };
    }

    @Override
    public void visitSimpleNameReferenceExpression(@NonNull JavaContext context, @NonNull UReferenceExpression node, @Nullable PsiElement target) {
        String methodName = node.getResolvedName();
        if ("setPadding".equals(methodName) || "setMargins".equals(methodName)) {
            context.report(ISSUE, node, context.getLocation(node),
                    "Ensure padding/margin values are symmetric for right-to-left layout support");
        }
    }

    @Nullable
    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UReferenceExpression.class);
    }
}