package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Collections;
import java.util.List;

public class RtlDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "RtlSymmetry",
            "Padding and margin symmetry for right-to-left layout support.",
            "If you specify padding or margin on the left side of a layout, you should probably also specify padding on the right side (and vice versa) for right-to-left layout symmetry.",
            Category.I18N,
            6,
            Severity.WARNING,
            new Implementation(
                    RtlDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return ResourceFolderType.LAYOUT.equals(folderType);
    }

    @Override
    public List<String> getApplicableAttributes() {
        return Collections.singletonList("android:paddingLeft");
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        Element element = (Element) attribute.getOwnerElement();
        String attributeName = attribute.getName();

        if ("android:paddingLeft".equals(attributeName)) {
            checkSymmetry(context, element, "android:paddingRight");
        } else if ("android:paddingRight".equals(attributeName)) {
            checkSymmetry(context, element, "android:paddingLeft");
        }

        if ("android:marginLeft".equals(attributeName)) {
            checkSymmetry(context, element, "android:marginRight");
        } else if ("android:marginRight".equals(attributeName)) {
            checkSymmetry(context, element, "android:marginLeft");
        }
    }

    private void checkSymmetry(XmlContext context, Element element, String oppositeAttribute) {
        Attr oppositeAttr = getAttribute(element, oppositeAttribute);
        if (oppositeAttr == null) {
            context.report(ISSUE, element, context.getLocation(element),
                    "Consider specifying the opposite padding/margin attribute for RTL symmetry.");
        }
    }

    private Attr getAttribute(Element element, String attributeName) {
        return (Attr) element.getAttributes().getNamedItem(attributeName);
    }
}