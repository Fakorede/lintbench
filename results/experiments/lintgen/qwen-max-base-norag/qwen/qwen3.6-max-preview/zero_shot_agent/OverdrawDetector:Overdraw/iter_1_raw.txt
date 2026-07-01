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

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.Collections;
import java.util.List;

public class OverdrawDetector extends ResourceXmlDetector {

    public static final Issue ISSUE = Issue.create(
            "Overdraw",
            "Overdraw: Painting regions more than once",
            "If you set a background drawable on a root view, then you should use a " +
            "custom theme where the theme background is null. Otherwise, the theme background " +
            "will be painted first, only to have your custom background completely cover it; " +
            "this is called \"overdraw\".\n\n" +
            "NOTE: This detector relies on figuring out which layouts are associated with " +
            "which activities based on scanning the Java code, and it's currently doing that " +
            "using an inexact pattern matching algorithm. Therefore, it can incorrectly " +
            "conclude which activity the layout is associated with and then wrongly complain " +
            "that a background-theme is hidden.\n\n" +
            "If you want your custom background on multiple pages, then you should consider " +
            "making a custom theme with your custom background and just using that theme " +
            "instead of a root element background.\n\n" +
            "Of course it's possible that your custom drawable is translucent and you want " +
            "it to be mixed with the background. However, you will get better performance " +
            "if you pre-mix the background with your drawable and use that resulting image or " +
            "color as a custom theme background instead.",
            Category.PERFORMANCE,
            3,
            Severity.WARNING,
            new Implementation(OverdrawDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public List<String> getApplicableElements() {
        return Collections.singletonList("*");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        Node parent = element.getParentNode();
        if (parent == null || parent.getNodeType() != Node.DOCUMENT_NODE) {
            return;
        }

        String tag = element.getTagName();
        if ("merge".equals(tag)) {
            return;
        }

        String background = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_BACKGROUND);
        if (background != null && !background.isEmpty()) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_BACKGROUND)),
                    "Possible overdraw: Root element paints background `" + background + "` with a theme that also paints a background (inferred theme is `?android:attr/windowBackground`)"
            );
        }
    }
}