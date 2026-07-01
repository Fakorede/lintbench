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
import com.android.tools.lint.detector.api.UastScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiElement;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UReferenceExpression;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class RtlDetector extends LayoutDetector implements SourceCodeScanner, XmlScanner {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    public static final Issue ISSUE = Issue.create(
            "RtlSymmetry",
            "Padding and margin symmetry",
            "If you specify padding or margin on the left side of a layout, you should "
                    + "probably also specify padding on the right side (and vice versa) for "
                    + "right-to-left layout symmetry.",
            Category.RTL,
            5,
            Severity.WARNING,
            new Implementation(RtlDetector.class, Scope.RESOURCE_FILE_SCOPE, Scope.JAVA_FILE_SCOPE));

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
        return Arrays.asList(
                "paddingLeft", "paddingRight",
                "layout_marginLeft", "layout_marginRight");
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String name = attribute.getLocalName();
        String counterpart = null;
        if ("paddingLeft".equals(name)) {
            counterpart = "paddingRight";
        } else if ("paddingRight".equals(name)) {
            counterpart = "paddingLeft";
        } else if ("layout_marginLeft".equals(name)) {
            counterpart = "layout_marginRight";
        } else if ("layout_marginRight".equals(name)) {
            counterpart = "layout_marginLeft";
        }

        if (counterpart != null) {
            Element element = attribute.getOwnerElement();
            if (!element.hasAttributeNS(ANDROID_URI, counterpart)) {
                context.report(ISSUE, attribute, context.getLocation(attribute),
                        "When specifying `" + name + "` you should probably also specify `"
                                + counterpart + "` for RTL symmetry");
            }
        }
    }

    @Nullable
    @Override
    public UastScanner createUastHandler() {
        return this;
    }

    @Nullable
    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UReferenceExpression.class);
    }

    @Override
    public boolean visitSimpleNameReferenceExpression(
            @NonNull JavaContext context,
            @Nullable UElement node,
            @NonNull PsiElement target) {
        String name = target.getText();
        if ("LEFT".equals(name) || "RIGHT".equals(name)) {
            context.report(ISSUE, node, context.getLocation(node),
                    "Using `" + name + "` instead of `START` or `END` can break RTL symmetry. "
                            + "Consider using start/end attributes for padding and margins.");
        }
        return false;
    }
}