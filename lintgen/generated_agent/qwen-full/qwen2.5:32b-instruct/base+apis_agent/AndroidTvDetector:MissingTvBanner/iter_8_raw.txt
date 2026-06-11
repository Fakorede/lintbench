package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.Collections;
import java.util.List;

public class AndroidTvDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "MissingTvbBanner",
            "A TV application must provide a home screen banner for each localization if it includes a Leanback launcher intent filter.",
            "The banner is the app launch point that appears on the home screen in the apps and games rows. Ensure you have provided banners for all localizations.",
            Category.USABILITY,
            6, Severity.WARNING,
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE));

    private boolean hasLeanbackLauncher = false;

    @Override
    public List<String> getApplicableElements() {
        return Collections.singletonList("intent-filter");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (element.getTagName().equals("intent-filter")) {
            List<Element> actions = getChildrenByTagName(element, "action");
            for (Element action : actions) {
                Attr nameAttr = action.getAttributeNode("android:name");
                if (nameAttr != null && nameAttr.getValue().equals("android.intent.action.MAIN")) {
                    hasLeanbackLauncher = true;
                }
            }
        }
    }

    @Override
    public void visitDocument(XmlContext context, Document document) {
        if (hasLeanbackLauncher) {
            // Check for the presence of banners in drawable folders.
            boolean bannerExists = false;
            for (ResourceFolderType folderType : ResourceFolderType.values()) {
                if (folderType == ResourceFolderType.DRAWABLE) {
                    List<String> files = context.getFiles(folderType);
                    for (String file : files) {
                        if (file.startsWith("tv_banner")) {
                            bannerExists = true;
                            break;
                        }
                    }
                }
            }

            if (!bannerExists) {
                context.report(ISSUE, document, context.getLocation(document),
                        "A TV application must provide a home screen banner for each localization.");
            }
        }
    }

    private List<Element> getChildrenByTagName(Element element, String tagName) {
        return XmlUtils.getChildrenByTagName(element, tagName);
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.MANIFEST;
    }
}