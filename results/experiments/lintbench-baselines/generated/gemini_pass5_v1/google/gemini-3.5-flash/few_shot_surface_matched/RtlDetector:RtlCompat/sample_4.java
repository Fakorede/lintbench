package com.android.tools.lint.checks;

import com.android.tools.lint.client.api.UElementHandler;
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
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

public class RtlDetector extends LayoutDetector implements SourceCodeScanner, XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "RtlCompat",
                    "Right-to-left text compatibility issues",
                    "API 17 adds a `textAlignment` attribute to specify text alignment. However, "
                            + "if you are supporting older versions than API 17, you must **also** "
                            + "specify a gravity or layout_gravity attribute, since older platforms "
                            + "will ignore the `textAlignment` attribute.",
                    Category.RTL,
                    5,
                    Severity.ERROR,
                    new Implementation(
                            RtlDetector.class,
                            EnumSet.of(Scope.RESOURCE_FILE, Scope.JAVA_FILE)
                    )
            );

    public RtlDetector() {}

    @Override
    public void filterIncident(Incident incident, Context context) {
        super.filterIncident(incident, context);
    }

    @Override
    public void afterCheckRootProject(Context context) {
        super.afterCheckRootProject(context);
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList("textAlignment");
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        if (!"textAlignment".equals(attribute.getLocalName())) {
            return;
        }

        if (context.getProject().getMinSdk() < 17) {
            Element element = attribute.getOwnerElement();
            boolean hasGravity = element.hasAttributeNS("http://schemas.android.com/apk/res/android", "gravity")
                    || element.hasAttributeNS("http://schemas.android.com/apk/res/android", "layout_gravity")
                    || element.hasAttribute("android:gravity")
                    || element.hasAttribute("android:layout_gravity");

            if (!hasGravity) {
                context.report(
                        ISSUE,
                        attribute,
                        context.getLocation(attribute),
                        "When supporting API level < 17, you must also specify `gravity` or "
                                + "`layout_gravity` alongside `textAlignment`"
                );
            }
        }
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
                RtlDetector.this.visitSimpleNameReferenceExpression(context, node);
            }
        };
    }

    public void visitSimpleNameReferenceExpression(JavaContext context, USimpleNameReferenceExpression node) {
        // Designed for checking programmatic RTL-related configurations if necessary
    }
}