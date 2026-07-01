package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN;
import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN_LEFT;
import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT;
import static com.android.SdkConstants.ATTR_PADDING;
import static com.android.SdkConstants.ATTR_PADDING_LEFT;
import static com.android.SdkConstants.ATTR_PADDING_RIGHT;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Collection;

public class RtlDetector extends ResourceXmlDetector {

    public static final Issue ISSUE = Issue.create(
            "RtlSymmetry",
            "Padding and margin symmetry",
            "If you specify padding or margin on the left side of a layout, you should " +
            "probably also specify padding on the right side (and vice versa) for " +
            "right-to-left layout symmetry.",
            Category.RTL,
            5,
            Severity.WARNING,
            new Implementation(RtlDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private static final Collection<String> ATTRIBUTES = Arrays.asList(
            ATTR_PADDING_LEFT,
            ATTR_PADDING_RIGHT,
            ATTR_LAYOUT_MARGIN_LEFT,
            ATTR_LAYOUT_MARGIN_RIGHT);

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    @NonNull
    public Collection<String> getApplicableAttributes() {
        return ATTRIBUTES;
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        if (!ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }

        String name = attribute.getLocalName();
        Element element = attribute.getOwnerElement();

        if (ATTR_PADDING_LEFT.equals(name)) {
            if (!hasAndroidAttribute(element, ATTR_PADDING_RIGHT)
                    && !hasAndroidAttribute(element, ATTR_PADDING)) {
                context.report(ISSUE, attribute, context.getLocation(attribute),
                        "When you define paddingLeft you should probably also define "
                                + "paddingRight for right-to-left symmetry");
            }
        } else if (ATTR_PADDING_RIGHT.equals(name)) {
            if (!hasAndroidAttribute(element, ATTR_PADDING_LEFT)
                    && !hasAndroidAttribute(element, ATTR_PADDING)) {
                context.report(ISSUE, attribute, context.getLocation(attribute),
                        "When you define paddingRight you should probably also define "
                                + "paddingLeft for right-to-left symmetry");
            }
        } else if (ATTR_LAYOUT_MARGIN_LEFT.equals(name)) {
            if (!hasAndroidAttribute(element, ATTR_LAYOUT_MARGIN_RIGHT)
                    && !hasAndroidAttribute(element, ATTR_LAYOUT_MARGIN)) {
                context.report(ISSUE, attribute, context.getLocation(attribute),
                        "When you define layout_marginLeft you should probably also define "
                                + "layout_marginRight for right-to-left symmetry");
            }
        } else if (ATTR_LAYOUT_MARGIN_RIGHT.equals(name)) {
            if (!hasAndroidAttribute(element, ATTR_LAYOUT_MARGIN_LEFT)
                    && !hasAndroidAttribute(element, ATTR_LAYOUT_MARGIN)) {
                context.report(ISSUE, attribute, context.getLocation(attribute),
                        "When you define layout_marginRight you should probably also define "
                                + "layout_marginLeft for right-to-left symmetry");
            }
        }
    }

    private static boolean hasAndroidAttribute(@NonNull Element element, @NonNull String name) {
        return element.hasAttributeNS(ANDROID_URI, name);
    }
}