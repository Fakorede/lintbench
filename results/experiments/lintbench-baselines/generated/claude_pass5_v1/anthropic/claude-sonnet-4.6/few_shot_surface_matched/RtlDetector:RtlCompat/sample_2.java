package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_GRAVITY;
import static com.android.SdkConstants.ATTR_LAYOUT_GRAVITY;
import static com.android.SdkConstants.ATTR_TEXT_ALIGNMENT;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.LintFix;
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

    private static final Implementation IMPLEMENTATION =
            new Implementation(
                    RtlDetector.class,
                    EnumSet.of(Scope.RESOURCE_FILE, Scope.JAVA_FILE),
                    Scope.RESOURCE_FILE_SCOPE,
                    Scope.JAVA_FILE_SCOPE);

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
                    IMPLEMENTATION);

    // Gravity values that relate to RTL/text alignment
    private static final List<String> GRAVITY_TEXT_ALIGNMENT_VALUES =
            Arrays.asList(
                    "start",
                    "end",
                    "left",
                    "right",
                    "center",
                    "center_horizontal",
                    "fill_horizontal");

    // Track whether we've seen a textAlignment without corresponding gravity
    private boolean mPendingError = false;

    @Override
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList(ATTR_TEXT_ALIGNMENT, ATTR_GRAVITY, ATTR_LAYOUT_GRAVITY);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String name = attribute.getLocalName();
        if (name == null) {
            return;
        }

        if (ATTR_TEXT_ALIGNMENT.equals(name)) {
            // Check if the element also has gravity or layout_gravity
            Element element = attribute.getOwnerElement();
            boolean hasGravity =
                    element.hasAttributeNS(ANDROID_URI, ATTR_GRAVITY)
                            || element.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_GRAVITY);

            if (!hasGravity) {
                // Only report if min SDK < 17
                int minSdk = context.getMainProject().getMinSdk();
                if (minSdk < 17) {
                    String message =
                            "To support older versions than API 17 (current min is "
                                    + minSdk
                                    + ") you should also specify `gravity` or `layout_gravity` "
                                    + "when specifying `textAlignment`";
                    Incident incident =
                            new Incident(
                                    ISSUE,
                                    attribute,
                                    context.getLocation(attribute),
                                    message,
                                    null);
                    context.report(incident);
                }
            }
        }
    }

    @Override
    public boolean filterIncident(
            @NonNull Context context, @NonNull Incident incident, @NonNull com.android.tools.lint.detector.api.LintMap map) {
        // Filter based on min SDK at the project level if needed
        int minSdk = context.getMainProject().getMinSdk();
        return minSdk < 17;
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        // Nothing additional needed after checking root project
    }

    @Override
    @Nullable
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(USimpleNameReferenceExpression.class);
    }

    @Override
    @Nullable
    public com.android.tools.lint.detector.api.UastVisitor createUastHandler(
            @NonNull JavaContext context) {
        return new RtlReferenceChecker(context);
    }

    @Override
    public void visitSimpleNameReferenceExpression(
            @NonNull JavaContext context,
            @NonNull USimpleNameReferenceExpression node) {
        // Handled via createUastHandler
    }

    private static class RtlReferenceChecker
            extends com.android.tools.lint.detector.api.UastVisitor {

        private final JavaContext mContext;

        RtlReferenceChecker(JavaContext context) {
            mContext = context;
        }

        @Override
        public boolean visitSimpleNameReferenceExpression(
                @NonNull USimpleNameReferenceExpression node) {
            String name = node.getIdentifier();

            // Check for references to textAlignment-related constants in code
            if ("TEXT_ALIGNMENT_GRAVITY".equals(name)
                    || "TEXT_ALIGNMENT_TEXT_START".equals(name)
                    || "TEXT_ALIGNMENT_TEXT_END".equals(name)
                    || "TEXT_ALIGNMENT_CENTER".equals(name)
                    || "TEXT_ALIGNMENT_VIEW_START".equals(name)
                    || "TEXT_ALIGNMENT_VIEW_END".equals(name)
                    || "TEXT_ALIGNMENT_INHERIT".equals(name)) {

                int minSdk = mContext.getMainProject().getMinSdk();
                if (minSdk < 17) {
                    PsiElement resolved = node.resolve();
                    if (resolved != null) {
                        String fqn = resolved.toString();
                        if (fqn != null && fqn.contains("TEXT_ALIGNMENT")) {
                            String message =
                                    "Referencing `"
                                            + name
                                            + "` which is only available from API 17 "
                                            + "(current min is "
                                            + minSdk
                                            + ")";
                            mContext.report(
                                    ISSUE,
                                    node,
                                    mContext.getLocation(node),
                                    message);
                        }
                    }
                }
            }

            return false;
        }
    }
}