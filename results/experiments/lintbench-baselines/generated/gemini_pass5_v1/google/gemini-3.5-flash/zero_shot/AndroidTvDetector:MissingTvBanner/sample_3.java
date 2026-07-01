package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class AndroidTvDetector extends Detector implements Detector.XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "MissingTvBanner",
            "TV Missing Banner",
            "A TV application must provide a home screen banner for each localization " +
            "if it includes a Leanback launcher intent filter. The banner is the app " +
            "launch point that appears on the home screen in the apps and games rows.",
            Category.USABILITY,
            5,
            Severity.WARNING,
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE)
    );

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("category");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String name = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
        if ("android.intent.category.LEANBACK_LAUNCHER".equals(name)) {
            Element activity = null;
            Node parent = element.getParentNode(); // <intent-filter>
            if (parent != null) {
                Node grandParent = parent.getParentNode(); // <activity> or <activity-alias>
                if (grandParent instanceof Element) {
                    activity = (Element) grandParent;
                }
            }

            if (activity != null) {
                String activityName = activity.getTagName();
                if ("activity".equals(activityName) || "activity-alias".equals(activityName)) {
                    if (activity.hasAttributeNS(SdkConstants.ANDROID_URI, "banner")) {
                        return;
                    }
                    Node appNode = activity.getParentNode();
                    if (appNode instanceof Element && "application".equals(appNode.getNodeName())) {
                        Element application = (Element) appNode;
                        if (application.hasAttributeNS(SdkConstants.ANDROID_URI, "banner")) {
                            return;
                        }
                    }

                    context.report(
                            ISSUE,
                            element,
                            context.getLocation(element),
                            "The Leanback launcher activity should be accompanied by a TV banner " +
                            "defined in the application or activity tag."
                    );
                }
            }
        }
    }
}