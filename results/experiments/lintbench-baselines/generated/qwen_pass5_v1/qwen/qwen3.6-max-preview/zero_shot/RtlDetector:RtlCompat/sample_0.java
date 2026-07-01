package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Collection;
import java.util.Collections;

public class RtlDetector extends ResourceXmlDetector {
    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_TEXT_ALIGNMENT = "textAlignment";
    private static final String ATTR_GRAVITY = "gravity";
    private static final String ATTR_LAYOUT_GRAVITY = "layout_gravity";

    public static final Issue ISSUE = Issue.create(
        "RtlCompat",
        "Right-to-left text compatibility issues",
        "API 17 adds a `textAlignment` attribute to specify text alignment. However, " +
        "if you are supporting older versions than API 17, you must also specify a " +
        "gravity or layout_gravity attribute, since older platforms will ignore the " +
        "`textAlignment` attribute.",
        Category.COMPATIBILITY,
        6,
        Severity.WARNING,
        new Implementation(RtlDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Nullable
    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_TEXT_ALIGNMENT);
    }

    @Override
    public void visitAttribute(@NotNull XmlContext context, @NotNull Attr attribute) {
        if (!ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }

        int minSdk = context.getProject().getMinSdkVersion().getApiLevel();
        if (minSdk >= 17) {
            return;
        }

        Element element = attribute.getOwnerElement();
        boolean hasGravity = element.hasAttributeNS(ANDROID_URI, ATTR_GRAVITY);
        boolean hasLayoutGravity = element.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_GRAVITY);

        if (!hasGravity && !hasLayoutGravity) {
            context.report(ISSUE, attribute, context.getLocation(attribute),
                "When using `textAlignment` on older platforms, you must also specify " +
                "`gravity` or `layout_gravity`");
        }
    }
}