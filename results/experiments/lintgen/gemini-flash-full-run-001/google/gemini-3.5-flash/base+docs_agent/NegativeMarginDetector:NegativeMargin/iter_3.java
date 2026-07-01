package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class NegativeMarginDetector extends ResourceXmlDetector {

    public static final Issue ISSUE = Issue.create(
            "NegativeMargin",
            "Negative Margins",
            "Margin values should be positive. Negative values are generally a sign that " +
            "you are making assumptions about views surrounding the current one, or may be " +
            "tempted to turn off child clipping to allow a view to escape its parent. " +
            "Turning off child clipping to do this not only leads to poor graphical " +
            "performance, it also results in wrong touch event handling since touch events " +
            "are based strictly on a chain of parent-rect hit tests. Finally, making " +
            "assumptions about the size of strings can lead to localization problems.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(
                    NegativeMarginDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    private static final String ATTR_LAYOUT_MARGIN = "layout_margin";
    private static final String ATTR_LAYOUT_MARGIN_LEFT = "layout_marginLeft";
    private static final String ATTR_LAYOUT_MARGIN_TOP = "layout_marginTop";
    private static final String ATTR_LAYOUT_MARGIN_RIGHT = "layout_marginRight";
    private static final String ATTR_LAYOUT_MARGIN_BOTTOM = "layout_marginBottom";
    private static final String ATTR_LAYOUT_MARGIN_START = "layout_marginStart";
    private static final String ATTR_LAYOUT_MARGIN_END = "layout_marginEnd";
    private static final String ATTR_LAYOUT_MARGIN_HORIZONTAL = "layout_marginHorizontal";
    private static final String ATTR_LAYOUT_MARGIN_VERTICAL = "layout_marginVertical";

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT || folderType == ResourceFolderType.VALUES;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList(
                ATTR_LAYOUT_MARGIN,
                ATTR_LAYOUT_MARGIN_LEFT,
                ATTR_LAYOUT_MARGIN_TOP,
                ATTR_LAYOUT_MARGIN_RIGHT,
                ATTR_LAYOUT_MARGIN_BOTTOM,
                ATTR_LAYOUT_MARGIN_START,
                ATTR_LAYOUT_MARGIN_END,
                ATTR_LAYOUT_MARGIN_HORIZONTAL,
                ATTR_LAYOUT_MARGIN_VERTICAL
        );
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("dimen", "item");
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        File parentFile = context.file.getParentFile();
        if (parentFile == null) {
            return;
        }
        ResourceFolderType folderType = ResourceFolderType.getFolderType(parentFile.getName());
        if (folderType != ResourceFolderType.LAYOUT) {
            return;
        }
        if (!SdkConstants.ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }

        String value = attribute.getValue();
        if (value != null && value.startsWith("-")) {
            if (value.length() > 1 && (Character.isDigit(value.charAt(1)) || value.charAt(1) == '.')) {
                context.report(
                        ISSUE,
                        attribute,
                        context.getLocation(attribute),
                        "Margin values should not be negative (`" + value + "`)"
                );
            }
        }
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        File parentFile = context.file.getParentFile();
        if (parentFile == null) {
            return;
        }
        ResourceFolderType folderType = ResourceFolderType.getFolderType(parentFile.getName());
        if (folderType != ResourceFolderType.VALUES) {
            return;
        }
        String tagName = element.getTagName();
        if ("dimen".equals(tagName) || ("item".equals(tagName) && "dimen".equals(element.getAttribute("type")))) {
            String name = element.getAttribute("name");
            if (name != null && name.toLowerCase().contains("margin")) {
                String value = element.getTextContent().trim();
                if (value.startsWith("-")) {
                    if (value.length() > 1 && (Character.isDigit(value.charAt(1)) || value.charAt(1) == '.')) {
                        context.report(
                                ISSUE,
                                element,
                                context.getLocation(element),
                                "Margin values should not be negative (`" + value + "`)"
                        );
                    }
                }
            }
        }
    }
}