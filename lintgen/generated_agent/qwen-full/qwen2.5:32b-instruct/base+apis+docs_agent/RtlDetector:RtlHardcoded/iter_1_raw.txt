package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Collections;
import java.util.List;

public class RtlDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "UseStartEndAttributes",
            "Using left/right instead of start/end attributes can lead to problems when a layout is rendered in locales where text flows from right to left.",
            "Use `Gravity#START` and `Gravity#END` instead. Similarly, in XML `gravity` and `layout_gravity` attributes, use `start` rather than `left`. For XML attributes such as paddingLeft and `layout_marginLeft`, use `paddingStart` and `layout_marginStart`.",
            Category.I18N,
            6,
            Severity.WARNING,
            new Implementation(RtlDetector.class, EnumSet.of(Scope.RESOURCE_FILE_SCOPE))
    );

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public List<String> getApplicableAttributes() {
        return Collections.singletonList(SdkConstants.ATTR_GRAVITY);
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        String value = attribute.getValue();
        if (value != null && (value.contains(SdkConstants.GRAVITY_VALUE_LEFT) || value.contains("right"))) {
            context.report(ISSUE, attribute, context.getLocation(attribute),
                    "Use `start` and `end` instead of `left` and `right` for better RTL support");
        }
    }

    @Override
    public List<String> getApplicableElements() {
        return Collections.singletonList("*");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String name = element.getAttribute(SdkConstants.ATTR_PADDING_LEFT);
        if (name != null && !name.isEmpty()) {
            context.report(ISSUE, element, context.getLocation(element),
                    "Use `paddingStart` instead of `paddingLeft` for better RTL support");
        }

        name = element.getAttribute(SdkConstants.ATTR_LAYOUT_MARGIN_LEFT);
        if (name != null && !name.isEmpty()) {
            context.report(ISSUE, element, context.getLocation(element),
                    "Use `layout_marginStart` instead of `layout_marginLeft` for better RTL support");
        }
    }
}