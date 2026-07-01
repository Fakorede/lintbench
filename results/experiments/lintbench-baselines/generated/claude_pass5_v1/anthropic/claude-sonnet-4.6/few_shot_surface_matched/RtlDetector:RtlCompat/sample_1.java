package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_GRAVITY;
import static com.android.SdkConstants.ATTR_LAYOUT_GRAVITY;
import static com.android.SdkConstants.ATTR_TEXT_ALIGNMENT;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.LintMap;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiElement;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class RtlDetector extends LayoutDetector implements SourceCodeScanner, XmlScanner {

    private static final String KEY_REQUIRES_GRAVITY = "requiresGravity";

    public static final Issue ISSUE =
            Issue.create(
                    "RtlCompat",
                    "Right-to-left text compatibility issues",
                    "API 17 adds a `textAlignment` attribute to specify text alignment. However, "
                            + "if you are supporting older versions than API 17, you must **also** "
                            + "specify a gravity or layout_gravity attribute, since older platforms "
                            + "will ignore the `textAlignment` attribute.",
                    Category.RTL,
                    6,
                    Severity.ERROR,
                    new Implementation(
                            RtlDetector.class,
                            EnumSet.of(Scope.RESOURCE_FILE, Scope.JAVA_FILE),
                            Scope.RESOURCE_FILE_SCOPE,
                            Scope.JAVA_FILE_SCOPE));

    public RtlDetector() {}

    // ---- XmlScanner ----

    @Override
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList(ATTR_TEXT_ALIGNMENT, ATTR_GRAVITY, ATTR_LAYOUT_GRAVITY);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String name = attribute.getLocalName();

        if (ATTR_TEXT_ALIGNMENT.equals(name)) {
            // Check if gravity or layout_gravity is also specified on the same element
            Element element = attribute.getOwnerElement();
            boolean hasGravity =
                    element.hasAttributeNS(ANDROID_URI, ATTR_GRAVITY)
                            || element.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_GRAVITY);

            if (!hasGravity) {
                // Report a conditional incident — if minSdk < 17 this is a problem
                Location location = context.getLocation(attribute);
                Incident incident =
                        new Incident(
                                ISSUE,
                                attribute,
                                location,
                                "Specify `gravity` or `layout_gravity` in addition to "
                                        + "`textAlignment` to support older platforms",
                                LintFix.create()
                                        .name("Add gravity attribute")
                                        .set(ANDROID_URI, ATTR_GRAVITY, "center")
                                        .build());
                LintMap map = new LintMap();
                map.put(KEY_REQUIRES_GRAVITY, true);
                context.report(incident, map);
            }
        }
    }

    @Override
    public boolean filterIncident(
            @NonNull Context context, @NonNull Incident incident, @NonNull LintMap map) {
        if (map.getBoolean(KEY_REQUIRES_GRAVITY, false)) {
            // Only flag if the project targets API < 17
            int minSdk = context.getMainProject().getMinSdk();
            return minSdk < 17;
        }
        return true;
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        // Nothing additional to do after checking the root project;
        // incidents are already reported during attribute/reference visits.
    }

    // ---- SourceCodeScanner ----

    @Override
    @Nullable
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(USimpleNameReferenceExpression.class);
    }

    @Override
    @Nullable
    public UElementHandler createUastHandler(@NonNull com.android.tools.lint.detector.api.JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitSimpleNameReferenceExpression(
                    @NonNull USimpleNameReferenceExpression node) {
                RtlDetector.this.visitSimpleNameReferenceExpression(context, node);
            }
        };
    }

    public void visitSimpleNameReferenceExpression(
            @NonNull com.android.tools.lint.detector.api.JavaContext context,
            @NonNull USimpleNameReferenceExpression node) {
        String name = node.getIdentifier();
        // Check for references to textAlignment resource field in Java/Kotlin code
        if ("textAlignment".equals(name)) {
            PsiElement resolved = node.resolve();
            if (resolved != null) {
                int minSdk = context.getMainProject().getMinSdk();
                if (minSdk < 17) {
                    context.report(
                            ISSUE,
                            node,
                            context.getLocation(node),
                            "Attribute `textAlignment` requires API level 17; "
                                    + "the project specifies a minimum API level of "
                                    + minSdk
                                    + ". Consider also specifying `gravity` to support "
                                    + "older platforms.");
                }
            }
        }
    }
}