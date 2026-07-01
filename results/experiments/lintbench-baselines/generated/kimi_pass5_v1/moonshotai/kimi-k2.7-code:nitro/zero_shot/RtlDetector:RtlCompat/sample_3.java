package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Collection;
import java.util.Collections;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class RtlDetector extends ResourceXmlDetector {

    public static final Issue ISSUE = Issue.create(
            "RtlCompat",
            "Right-to-left text compatibility issues",
            "The `textAlignment` attribute was added in API 17. "
                    + "When supporting devices running older platforms, "
                    + "you should also specify `android:gravity` or "
                    + "`android:layout_gravity`; otherwise older devices will "
                    + "ignore the `textAlignment` attribute.",
            Category.I18N,
            5,
            Severity.WARNING,
            new Implementation(RtlDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public @NotNull Collection<String> getApplicableAttributes() {
        return Collections.singletonList(SdkConstants.ATTR_TEXT_ALIGNMENT);
    }

    @Override
    public void visitAttribute(@NotNull XmlContext context, @NotNull Attr attribute) {
        if (!SdkConstants.ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }

        if (context.getMainProject().getMinSdk() >= 17) {
            return;
        }

        Element element = attribute.getOwnerElement();
        if (hasAndroidAttribute(element, SdkConstants.ATTR_GRAVITY)
                || hasAndroidAttribute(element, SdkConstants.ATTR_LAYOUT_GRAVITY)) {
            return;
        }

        context.report(
                ISSUE,
                attribute,
                context.getValueLocation(attribute),
                "Use \"android:gravity\" or \"android:layout_gravity\" in addition to \"android:textAlignment\"");
    }

    private static boolean hasAndroidAttribute(@NotNull Element element, @NotNull String localName) {
        return element.hasAttributeNS(SdkConstants.ANDROID_URI, localName);
    }
}