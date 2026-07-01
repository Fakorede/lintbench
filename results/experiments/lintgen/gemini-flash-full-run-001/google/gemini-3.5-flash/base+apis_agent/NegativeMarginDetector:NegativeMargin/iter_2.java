package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class NegativeMarginDetector extends Detector implements XmlScanner {

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

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT || folderType == ResourceFolderType.VALUES;
    }

    private ResourceFolderType getFolderType(XmlContext context) {
        ResourceFolderType folderType = context.getFolderType();
        if (folderType == null) {
            File file = context.file;
            if (file != null) {
                File parent = file.getParentFile();
                if (parent != null) {
                    folderType = ResourceFolderType.getFolderType(parent.getName());
                }
            }
        }
        return folderType;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList(
                "layout_margin",
                "layout_marginLeft",
                "layout_marginTop",
                "layout_marginRight",
                "layout_marginBottom",
                "layout_marginStart",
                "layout_marginEnd"
        );
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        if (getFolderType(context) != ResourceFolderType.LAYOUT) {
            return;
        }

        if (!SdkConstants.ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }

        String value = attribute.getValue();
        if (value != null) {
            value = value.trim();
            if (value.startsWith("-") && value.length() > 1 && Character.isDigit(value.charAt(1))) {
                context.report(
                        ISSUE,
                        attribute,
                        context.getLocation(attribute),
                        "Margin values should be positive"
                );
            }
        }
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("dimen", "item");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (getFolderType(context) != ResourceFolderType.VALUES) {
            return;
        }

        String name = element.getAttribute("name");
        if (name != null && name.toLowerCase().contains("margin")) {
            String text = element.getTextContent();
            if (text != null) {
                text = text.trim();
                if (text.startsWith("-") && text.length() > 1 && Character.isDigit(text.charAt(1))) {
                    context.report(
                            ISSUE,
                            element,
                            context.getLocation(element),
                            "Margin values should be positive"
                    );
                }
            }
        }
    }
}