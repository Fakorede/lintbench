package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UElementHandler;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

public class RtlDetector extends LayoutDetector implements SourceCodeScanner, XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "RtlSymmetry",
            "Padding and margin symmetry",
            "If you specify padding or margin on the left side of a layout, you should " +
            "probably also specify padding on the right side (and vice versa) for " +
            "right-to-left layout symmetry.",
            Category.RTL,
            5,
            Severity.WARNING,
            new Implementation(RtlDetector.class, EnumSet.of(Scope.RESOURCE_FILE, Scope.JAVA_FILE)));

    @Nullable
    @Override
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList(
                "android:paddingLeft", "android:paddingRight",
                "android:layout_marginLeft", "android:layout_marginRight"
        );
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String name = attribute.getLocalName();
        Element element = attribute.getOwnerElement();
        boolean isLeft = name.endsWith("Left");
        String counterpart = isLeft ? name.replace("Left", "Right") : name.replace("Right", "Left");

        if (!element.hasAttributeNS(attribute.getNamespaceURI(), counterpart)) {
            context.report(ISSUE, attribute, context.getLocation(attribute),
                    "When you define " + name + " you should probably also define " + counterpart +
                    " for right-to-left symmetry");
        }
    }

    @Nullable
    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(USimpleNameReferenceExpression.class);
    }

    @Nullable
    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitSimpleNameReferenceExpression(@NonNull USimpleNameReferenceExpression node) {
                String name = node.getIdentifier();
                if (name == null) return;
                boolean isLeft = name.contains("Left") || name.contains("left");
                boolean isRight = name.contains("Right") || name.contains("right");
                boolean isMarginOrPadding = name.contains("Margin") || name.contains("margin") ||
                                            name.contains("Padding") || name.contains("padding");
                if ((isLeft || isRight) && isMarginOrPadding) {
                    context.report(ISSUE, node, context.getLocation(node),
                            "Using " + name + " can lead to RTL symmetry issues. Consider using start/end variants or ensuring symmetry.");
                }
            }
        };
    }

    @Override
    public boolean filterIncident(@NonNull Context context, @NonNull Issue issue) {
        return true;
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        // No-op
    }
}