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
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class AndroidTvDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "MissingTvBanner",
            "TV Missing Banner",
            "A TV application must provide a home screen banner for each " +
            "localization if it includes a Leanback launcher intent filter. " +
            "The banner is the app launch point that appears on the home " +
            "screen in the apps and games rows.",
            Category.CORRECTNESS,
            5,
            Severity.ERROR,
            new Implementation(
                    AndroidTvDetector.class,
                    Scope.MANIFEST_SCOPE
            )
    );

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("category");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String name = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
        if ("android.intent.category.LEANBACK_LAUNCHER".equals(name)) {
            Node parent = element.getParentNode(); // <intent-filter>
            if (parent != null && "intent-filter".equals(parent.getNodeName())) {
                parent = parent.getParentNode(); // <activity> or <activity-alias>
            }

            if (parent instanceof Element) {
                Element activityElement = (Element) parent;
                String tagName = activityElement.getTagName();
                if ("activity".equals(tagName) || "activity-alias".equals(tagName)) {
                    if (activityElement.hasAttributeNS(SdkConstants.ANDROID_URI, "banner")) {
                        return;
                    }

                    Node appNode = activityElement.getParentNode();
                    if (appNode instanceof Element && "application".equals(appNode.getNodeName())) {
                        Element appElement = (Element) appNode;
                        if (appElement.hasAttributeNS(SdkConstants.ANDROID_URI, "banner")) {
                            return;
                        }
                    }

                    context.report(
                            ISSUE,
                            activityElement,
                            context.getLocation(activityElement),
                            "The TV activity should define a banner using the `android:banner` attribute"
                    );
                }
            }
        }
    }
}