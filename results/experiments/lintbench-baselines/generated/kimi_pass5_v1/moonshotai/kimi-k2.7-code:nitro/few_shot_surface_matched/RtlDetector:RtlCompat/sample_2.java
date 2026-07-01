package com.android.tools.lint.checks;

import static com.android.SdkConstants.ATTR_GRAVITY;
import static com.android.SdkConstants.ATTR_LAYOUT_GRAVITY;
import static com.android.SdkConstants.ATTR_TEXT_ALIGNMENT;
import static com.android.SdkConstants.VIEW;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
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
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UElementHandler;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class RtlDetector extends LayoutDetector implements SourceCodeScanner, XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "RtlCompat",
                    "Right-to-left text compatibility issues",
                    "API 17 adds a `textAlignment` attribute to specify text alignment. However, "
                            + "if you are supporting older versions than API 17, you must also "
                            + "specify a `gravity` or `layout_gravity` attribute, since older "
                            + "platforms will ignore the `textAlignment` attribute.",
                    Category.RTL,
                    5,
                    Severity.ERROR,
                    new Implementation(
                            RtlDetector.class,
                            EnumSet.of(Scope.RESOURCE_FILE_SCOPE, Scope.JAVA_FILE_SCOPE)));

    @Override
    @Nullable
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_TEXT_ALIGNMENT);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        Element element = attribute.getOwnerElement();
        if (element.getAttributeNode(ATTR_GRAVITY) == null
                && element.getAttributeNode(ATTR_LAYOUT_GRAVITY) == null) {
            context.report(
                    ISSUE,
                    attribute,
                    context.getLocation(attribute),
                    "To support older versions than API 17, you must also specify a "
                            + "`gravity` or `layout_gravity` attribute");
        }
    }

    @Override
    @Nullable
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(USimpleNameReferenceExpression.class);
    }

    @Override
    @Nullable
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitSimpleNameReferenceExpression(
                    @NonNull USimpleNameReferenceExpression expression) {
                String name = expression.getIdentifier();
                if (!name.startsWith("TEXT_ALIGNMENT_")) {
                    return;
                }
                PsiElement resolved = expression.resolve();
                if (!(resolved instanceof PsiField)) {
                    return;
                }
                PsiField field = (PsiField) resolved;
                if (field.getContainingClass() == null
                        || !VIEW.equals(field.getContainingClass().getQualifiedName())) {
                    return;
                }
                context.report(
                        ISSUE,
                        expression,
                        context.getLocation(expression),
                        "To support older versions than API 17, you must also call `setGravity()`");
            }
        };
    }

    @Override
    public boolean filterIncident(@NonNull Context context, @NonNull Incident incident) {
        return context.getProject().getMinSdk() < 17;
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
    }
}