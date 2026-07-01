package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Arrays;
import java.util.Collection;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class AndroidTvDetector extends Detector implements XmlScanner {

    private static final String NODE_APPLICATION = "application";
    private static final String NODE_INTENT_FILTER = "intent-filter";
    private static final String NODE_ACTION = "action";
    private static final String NODE_CATEGORY = "category";
    private static final String ATTR_NAME = "name";
    private static final String ACTION_MAIN = "android.intent.action.MAIN";
    private static final String CATEGORY_LEANBACK_LAUNCHER = "android.intent.category.LEANBACK_LAUNCHER";
    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    private boolean mHasLeanbackLauncher;
    private boolean mHasBanner;
    private Element mApplicationElement;

    public static final Issue ISSUE =
            Issue.create(
                    "MissingTvBanner",
                    "Missing TV Banner",
                    "A TV application must provide a home screen banner for each localization if it "
                            + "includes a Leanback launcher intent filter. The banner is the app "
                            + "launch point that appears on the home screen in the apps and games rows.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE));

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(NODE_APPLICATION, NODE_INTENT_FILTER);
    }

    @Override
    public void beforeCheckFile(Context context) {
        mHasLeanbackLauncher = false;
        mHasBanner = false;
        mApplicationElement = null;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String tag = element.getTagName();
        if (NODE_APPLICATION.equals(tag)) {
            mApplicationElement = element;
            String banner = element.getAttributeNS(ANDROID_URI, "banner");
            if (banner != null && !banner.isEmpty()) {
                mHasBanner = true;
            }
        } else if (NODE_INTENT_FILTER.equals(tag)) {
            boolean hasMainAction = false;
            boolean hasLeanbackCategory = false;

            NodeList children = element.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node child = children.item(i);
                if (child.getNodeType() != Node.ELEMENT_NODE) {
                    continue;
                }
                Element childElement = (Element) child;
                String childTag = childElement.getTagName();
                String name = childElement.getAttributeNS(ANDROID_URI, ATTR_NAME);

                if (NODE_ACTION.equals(childTag) && ACTION_MAIN.equals(name)) {
                    hasMainAction = true;
                } else if (NODE_CATEGORY.equals(childTag)
                        && CATEGORY_LEANBACK_LAUNCHER.equals(name)) {
                    hasLeanbackCategory = true;
                }
            }

            if (hasMainAction && hasLeanbackCategory) {
                mHasLeanbackLauncher = true;
            }
        }
    }

    @Override
    public void afterCheckFile(Context context) {
        if (mHasLeanbackLauncher && !mHasBanner && mApplicationElement != null) {
            XmlContext xmlContext = (XmlContext) context;
            xmlContext.report(
                    ISSUE,
                    mApplicationElement,
                    xmlContext.getLocation(mApplicationElement),
                    "TV application is missing a banner for the Leanback launcher");
        }
    }
}