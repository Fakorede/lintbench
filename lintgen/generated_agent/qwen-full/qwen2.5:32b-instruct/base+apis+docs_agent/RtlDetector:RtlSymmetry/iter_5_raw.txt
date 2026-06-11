package com.android.tools.lint.checks;

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
            "RtlPaddingSymmetry",
            "If you specify padding or margin on the left side of a layout, you should probably also specify padding on the right side (and vice versa) for right-to-left layout symmetry.",
            "This issue reports cases where padding or margin attributes are specified only on one side (left/right), which can cause asymmetry in RTL layouts. It is recommended to use `paddingStart`, `paddingEnd`, `marginStart`, and `marginEnd` instead of the left/right variants for better RTL support.",
            Category.I18N,
            5,
            Severity.WARNING,
            new Implementation(
                    RtlDetector.class,
                    Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public List<String> getApplicableAttributes() {
        return Collections.singletonList("*");
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        String attributeName = attribute.getName();
        if (attributeName.equals("android:paddingLeft") || attributeName.equals("android:paddingRight")
                || attributeName.equals("android:marginLeft") || attributeName.equals("android:marginRight")) {

            Element element = attribute.getOwnerElement();

            boolean hasOppositePaddingOrMargin = false;
            NamedNodeMap attributes = element.getAttributes();
            for (int i = 0; i < attributes.getLength(); i++) {
                Attr attr = (Attr) attributes.item(i);
                String attrName = attr.getName();
                if ((attributeName.equals("android:paddingLeft") && attrName.equals("android:paddingRight"))
                        || (attributeName.equals("android:paddingRight") && attrName.equals("android:paddingLeft"))
                        || (attributeName.equals("android:marginLeft") && attrName.equals("android:marginRight"))
                        || (attributeName.equals("android:marginRight") && attrName.equals("android:marginLeft"))) {
                    hasOppositePaddingOrMargin = true;
                    break;
                }
            }

            if (!hasOppositePaddingOrMargin) {
                context.report(ISSUE, attribute, context.getLocation(attribute),
                        "Consider specifying the opposite padding/margin for RTL symmetry.");
            }
        }
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }
}