package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.LintMap;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class RtlDetector extends LayoutDetector {

    private static final int JELLY_BEAN_MR1 = 17;
    private static final int ICE_CREAM_SANDWICH = 14;
    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_TEXT_ALIGNMENT = "textAlignment";
    private static final String ATTR_GRAVITY = "gravity";
    private static final String ATTR_LAYOUT_GRAVITY = "layout_gravity";
    private static final String GRAVITY_CLS = "android.view.Gravity";
    private static final String KEY_MIN_SDK = "minSdk";

    private static final Implementation IMPLEMENTATION =
            new Implementation(RtlDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "RtlCompat",
                    "Right-to-left text compatibility issues",
                    "To support right-to-left text on devices running API levels lower than 17, "
                            + "you must provide a fallback for attributes that newer platforms "
                            + "introduced. For example, `android:textAlignment` is only honored on "
                            + "API 17 and later; older platforms ignore it, so you should also "
                            + "specify `android:gravity` or `android:layout_gravity`.",
                    Category.RTL,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private final List<PendingAttribute> mPendingAttributes = new ArrayList<>();

    private static class PendingAttribute {
        final XmlContext context;
        final Attr attribute;

        PendingAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
            this.context = context;
            this.attribute = attribute;
        }
    }

    @Override
    public boolean filterIncident(
            @NonNull Context context, @NonNull Incident incident, @NonNull LintMap map) {
        int minSdk = map != null ? map.getInt(KEY_MIN_SDK, -1) : -1;
        if (minSdk == -1) {
            minSdk = context.getMainProject().getMinSdk();
        }
        return minSdk < JELLY_BEAN_MR1;
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mPendingAttributes.clear();
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        for (PendingAttribute pending : mPendingAttributes) {
            Element element = pending.attribute.getOwnerElement();
            if (element == null) {
                continue;
            }
            boolean hasGravity =
                    !element.getAttributeNS(ANDROID_URI, ATTR_GRAVITY).isEmpty();
            boolean hasLayoutGravity =
                    !element.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_GRAVITY).isEmpty();

            if (!hasGravity && !hasLayoutGravity) {
                Location location = pending.context.getLocation(pending.attribute);
                String message =
                        "When using `android:"
                                + ATTR_TEXT_ALIGNMENT
                                + "` you must also specify `android:"
                                + ATTR_GRAVITY
                                + "` or `android:"
                                + ATTR_LAYOUT_GRAVITY
                                + "` for devices running API levels lower than "
                                + JELLY_BEAN_MR1;
                context.report(ISSUE, location, message, null);
            }
        }
        mPendingAttributes.clear();
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_TEXT_ALIGNMENT);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        if (!ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }
        if (ATTR_TEXT_ALIGNMENT.equals(attribute.getLocalName())) {
            mPendingAttributes.add(new PendingAttribute(context, attribute));
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
            public void visitSimpleNameReferenceExpression(
                    @NonNull USimpleNameReferenceExpression node) {
                if (context.getMainProject().getMinSdk() >= ICE_CREAM_SANDWICH) {
                    return;
                }
                PsiElement resolved = node.resolve();
                if (!(resolved instanceof PsiField)) {
                    return;
                }
                PsiField field = (PsiField) resolved;
                PsiClass containingClass = field.getContainingClass();
                if (containingClass == null
                        || !GRAVITY_CLS.equals(containingClass.getQualifiedName())) {
                    return;
                }
                String name = field.getName();
                if ("START".equals(name) || "END".equals(name)) {
                    String message =
                            "Gravity.START and Gravity.END are not supported on API levels lower "
                                    + "than "
                                    + ICE_CREAM_SANDWICH
                                    + "; use Gravity.LEFT and Gravity.RIGHT for older platforms";
                    context.report(ISSUE, context.getLocation(node), message, null);
                }
            }
        };
    }
}