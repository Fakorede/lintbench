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
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class OverdrawDetector extends Detector implements Detector.XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "Overdraw",
            "Overdraw: Painting regions more than once",
            "If you set a background drawable on a root view, then you should use a custom theme " +
            "where the theme background is null. Otherwise, the theme background will be painted " +
            "first, only to have your custom background completely cover it; this is called \"overdraw\".\n\n" +
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
            new Implementation(
                    OverdrawDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public void visitDocument(XmlContext context, Document document) {
        Element root = document.getDocumentElement();
        if (root != null) {
            if (root.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_BACKGROUND)) {
                String background = root.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_BACKGROUND);
                if (background != null && !background.isEmpty()) {
                    context.report(
                            ISSUE,
                            root,
                            context.getLocation(root.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_BACKGROUND)),
                            "Possible overdraw: Root element has background \"" + background + "\", which could be " +
                            "painted on top of the window background. Consider using a theme with a null background instead."
                    );
                }
            }
        }
    }
}