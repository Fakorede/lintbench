package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_SRC;
import static com.android.SdkConstants.IMAGE_VIEW;
import static com.android.SdkConstants.LINEAR_LAYOUT;
import static com.android.SdkConstants.TEXT_VIEW;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Arrays;
import java.util.Collection;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class UseCompoundDrawableDetector extends LayoutDetector {
    private static final String EXPLANATION = "..." ;

    public static final Issue ISSUE = Issue.create(
        id = "UseCompoundDrawables",
        briefDescription = "Node can be replaced by a `TextView` with compound drawables",
        explanation = EXPLANATION,
        category = Category.PERFORMANCE,
        priority = 5,
        severity = Severity.WARNING,
        new Implementation(UseCompoundDrawableDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(LINEAR_LAYOUT);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (!element.getTagName().equals(LINEAR_LAYOUT)) {
            return;
        }
        NodeList childNodes = element.getChildNodes();
        int childCount = childNodes.getLength();
        if (childCount != 2) {
            return;
        }
        Element first = (Element) childNodes.item(0);
        Element second = (Element) childNodes.item(1);
        String firstTag = first.getTagName();
        String secondTag = second.getTagName();
        if (!((firstTag.equals(IMAGE_VIEW) && secondTag.equals(TEXT_VIEW))
                || (firstTag.equals(TEXT_VIEW) && secondTag.equals(IMAGE_VIEW)))) {
            return;
        }
        Element imageView = firstTag.equals(IMAGE_VIEW) ? first : second;
        if (!imageView.hasAttributeNS(ANDROID_URI, ATTR_SRC)) {
            return;
        }
        String message = "This tag and its children can be replaced by a single " +
                "`<TextView/>` with a compound drawable";
        context.report(ISSUE, element, context.getElementLocation(element), message);
    }
}