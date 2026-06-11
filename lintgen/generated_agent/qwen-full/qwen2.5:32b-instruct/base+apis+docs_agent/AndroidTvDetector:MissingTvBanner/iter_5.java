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
        if (element.getTagName().equals("action")) {
            Attr nameAttr = element.getAttributeNode("android:name");
            if (nameAttr != null && "android.intent.action.MAIN".equals(nameAttr.getValue())) {
                hasLeanbackLauncher = true;
            }
        }
    }

    @Override
    public void visitDocument(XmlContext context, Document document) {
        if (hasLeanbackLauncher) {
            List<String> locales = context.getDriver().getProject().getLocales();
            for (String locale : locales) {
                String bannerPath = "res/drawable-" + locale + "/tv_banner.png";
                if (!context.getFilesystem().exists(context.getDriver(), bannerPath)) {
                    context.report(ISSUE, document, context.getLocation(document),
                            "Missing TV home screen banner for localization: " + locale);
                }
            }
        }
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return ResourceFolderType.MANIFEST.equals(folderType);
    }
}