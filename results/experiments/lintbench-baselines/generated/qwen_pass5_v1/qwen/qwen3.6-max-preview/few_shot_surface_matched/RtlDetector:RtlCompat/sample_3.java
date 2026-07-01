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
import com.android.tools.lint.detector.api.UElementHandler;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.USimpleReferenceExpression;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class RtlDetector extends LayoutDetector implements XmlScanner, SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
            "RtlCompat",
            "Right-to-left text compatibility issues",
            "API 17 adds a `textAlignment` attribute to specify text alignment. However, if you are "
                    + "supporting older versions than API 17, you must also specify a gravity or "
                    + "layout_gravity attribute, since older platforms will ignore the `textAlignment` attribute.",
            Category.RTL,
            5,
            Severity.ERROR,
            new Implementation(RtlDetector.class, Scope.RESOURCE_FILE_SCOPE, Scope.JAVA_FILE_SCOPE));

    @Override
    public boolean filterIncident(@NonNull Context context, @NonNull Incident incident) {
        return true;
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        super.afterCheckRootProject(context);
    }

    @Nullable
    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList("textAlignment");
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        Element element = attribute.getOwnerElement();
        String androidUri = "http://schemas.android.com/apk/res/android";
        String gravity = element.getAttributeNS(androidUri, "gravity");
        String layoutGravity = element.getAttributeNS(androidUri, "layout_gravity");

        if ((gravity != null && !gravity.isEmpty()) ||
                (layoutGravity != null && !layoutGravity.isEmpty())) {
            return;
        }

        context.report(ISSUE, attribute, context.getLocation(attribute),
                "To support older versions than API 17, you should also specify "
                        + "`android:gravity` or `android:layout_gravity`");
    }

    @Nullable
    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(USimpleReferenceExpression.class);
    }

    @Nullable
    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitSimpleNameReferenceExpression(@NonNull USimpleReferenceExpression node) {
                String name = node.getIdentifier();
                if (name != null && name.contains("TEXT_ALIGNMENT")) {
                    context.report(ISSUE, node, context.getLocation(node),
                            "To support older versions than API 17, you should also call "
                                    + "`setGravity()` or set `layout_gravity`");
                }
            }
        };
    }
}