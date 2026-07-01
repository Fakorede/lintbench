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
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class RtlDetector extends LayoutDetector implements SourceCodeScanner, XmlScanner {

    private static final String KEY_MIN_SDK = "minSdk";
    private static final String KEY_TARGET_SDK = "targetSdk";
    private static final int RTL_API = 17;

    public static final Issue ISSUE =
            Issue.create(
                    "RtlCompat",
                    "Right-to-left text compatibility issues",
                    "API 17 adds a `textAlignment` attribute to specify text alignment. However, "
                            + "if you are supporting older versions than API 17, you must **also** "
                            + "specify a `gravity` or `layout_gravity` attribute, since older "
                            + "platforms will ignore the `textAlignment` attribute.",
                    Category.RTL,
                    6,
                    Severity.ERROR,
                    new Implementation(
                            RtlDetector.class,
                            EnumSet.of(Scope.RESOURCE_FILE, Scope.JAVA_FILE),
                            Scope.RESOURCE_FILE_SCOPE,
                            Scope.JAVA_FILE_SCOPE))
                    .setAndroidSpecific(true);

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_TEXT_ALIGNMENT);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        // Only flag if minSdkVersion < RTL_API
        int minSdk = context.getMainProject().getMinSdk();
        if (minSdk >= RTL_API) {
            return;
        }

        Element element = attribute.getOwnerElement();

        // Check whether the element also specifies gravity or layout_gravity
        boolean hasGravity = element.hasAttributeNS(ANDROID_URI, ATTR_GRAVITY)
                || element.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_GRAVITY);

        if (!hasGravity) {
            String message =
                    "To support older versions than API 17 (minSdkVersion is "
                            + minSdk
                            + ") you should **also** specify `gravity` or `layout_gravity` when "
                            + "specifying `textAlignment`";
            Incident incident = new Incident(
                    ISSUE,
                    attribute,
                    context.getLocation(attribute),
                    message);
            context.report(incident, map().put(KEY_MIN_SDK, minSdk));
        }
    }

    @Override
    public boolean filterIncident(
            @NonNull Context context,
            @NonNull Incident incident,
            @NonNull LintMap map) {
        if (context.getMainProject().getMinSdk() >= RTL_API) {
            return false;
        }
        int minSdk = map.getInt(KEY_MIN_SDK, 1);
        return minSdk < RTL_API;
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        // Nothing special needed after checking the root project for this detector.
    }

    // SourceCodeScanner methods

    @Nullable
    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(USimpleNameReferenceExpression.class);
    }

    @Nullable
    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        int minSdk = context.getMainProject().getMinSdk();
        if (minSdk >= RTL_API) {
            return null;
        }
        return new UElementHandler() {
            @Override
            public void visitSimpleNameReferenceExpression(
                    @NonNull USimpleNameReferenceExpression node) {
                String name = node.getIdentifier();
                if ("textAlignment".equals(name)) {
                    PsiElement resolved = node.resolve();
                    if (resolved != null) {
                        String message =
                                "To support older versions than API 17 (minSdkVersion is "
                                        + minSdk
                                        + ") you should **also** specify `gravity` or "
                                        + "`layout_gravity` when specifying `textAlignment`";
                        context.report(
                                ISSUE,
                                node,
                                context.getLocation(node),
                                message);
                    }
                }
            }
        };
    }

    private static LintMap map() {
        return new LintMap();
    }

    /**
     * Handler for UAST elements.
     */
    public abstract static class UElementHandler
            extends com.android.tools.lint.detector.api.UElementHandler {
        public void visitSimpleNameReferenceExpression(
                @NonNull USimpleNameReferenceExpression node) {}
    }
}