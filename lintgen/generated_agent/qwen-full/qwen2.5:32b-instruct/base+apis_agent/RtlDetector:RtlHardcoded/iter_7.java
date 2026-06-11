package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
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
            5,
            Severity.WARNING,
            new Implementation(RtlDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public List<String> getApplicableElements() {
        return Collections.singletonList("*");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        for (int i = 0; i < element.getAttributes().getLength(); i++) {
            Attr attr = (Attr) element.getAttributes().item(i);
            String name = attr.getName();
            if (name.equals(SdkConstants.ATTR_LAYOUT_MARGIN_LEFT)
                    || name.equals(SdkConstants.ATTR_PADDING_LEFT)
                    || name.equals(SdkConstants.GRAVITY_VALUE_LEFT)) {
                reportIssue(context, element, attr);
            }
        }
    }

    private void reportIssue(XmlContext context, Element element, Attr attr) {
        context.report(ISSUE, context.getLocation(attr), "Use start/end attributes instead of left/right");
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return ResourceFolderType.LAYOUT.equals(folderType)
                || ResourceFolderType.MENU.equals(folderType);
    }
}