package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Collection;

public class EllipsizeMaxLinesDetector extends ResourceXmlDetector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "EllipsizeMaxLines",
            "Combining ellipsize and maxLines=1",
            "Combining `android:ellipsize` and `android:maxLines=\"1\"` can lead to "
                    + "crashes on some devices. Earlier versions of lint recommended "
                    + "replacing `android:singleLine=\"true\"` with `android:maxLines=\"1\"`, "
                    + "but that should not be done when using `android:ellipsize`.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(EllipsizeMaxLinesDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList(SdkConstants.ATTR_MAX_LINES, SdkConstants.ATTR_ELLIPSIZE);
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        if (!SdkConstants.ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }

        String name = attribute.getLocalName();
        if (name == null) {
            name = attribute.getName();
        }
        if (!SdkConstants.ATTR_MAX_LINES.equals(name)) {
            return;
        }

        if (!"1".equals(attribute.getValue())) {
            return;
        }

        Element owner = attribute.getOwnerElement();
        if (owner == null) {
            return;
        }

        Attr ellipsize = owner.getAttributeNodeNS(
                SdkConstants.ANDROID_URI, SdkConstants.ATTR_ELLIPSIZE);
        if (ellipsize != null) {
            context.report(
                    ISSUE,
                    attribute,
                    context.getLocation(attribute),
                    "Combining `android:ellipsize` with `android:maxLines=\"1\"` can cause "
                            + "crashes on some devices; use `android:singleLine=\"true\"` instead");
        }
    }
}