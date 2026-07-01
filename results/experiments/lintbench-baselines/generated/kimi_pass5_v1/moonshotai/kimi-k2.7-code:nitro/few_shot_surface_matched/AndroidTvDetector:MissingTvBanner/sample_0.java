package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Arrays;
import java.util.Collection;

public class AndroidTvDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "MissingTvBanner",
                    "Missing TV Banner",
                    "A TV application must provide a home screen banner for each localization if it "
                            + "includes a Leanback launcher intent filter. The banner is the app "
                            + "launch point that appears on the home screen in the apps and games rows.",
                    Category.CORRECTNESS,
                    5,
                    Severity.ERROR,
                    new Implementation(
                            AndroidTvDetector.class, Scope.MANIFEST_SCOPE));

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String NODE_APPLICATION = "application";
    private static final String NODE_INTENT_FILTER = "intent-filter";
    private static final String NODE_ACTION = "action";
    private static final String NODE_CATEGORY = "category";
    private static final String ATTR_NAME = "name";
    private static final String ATTR_BANNER = "banner";
    private static final String ACTION_MAIN = "android.intent.action.MAIN";
    private static final String CATEGORY_LEANBACK_LAUNCHER = "android.intent.category.LEANBACK_LAUNCHER";

    private boolean mHasLeanbackLauncher;
    private boolean mHasBanner;
    private Element mApplicationElement;

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(NODE_APPLICATION, NODE_INTENT_FILTER);
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mHasLeanbackLauncher = false;
        mHasBanner = false;
        mApplicationElement = null;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();
        if (NODE_APPLICATION.equals(tagName)) {
            mApplicationElement = element;
            if (element.hasAttributeNS(ANDROID_URI, ATTR_BANNER)) {
                mHasBanner = true;
            }
        } else if (NODE_INTENT_FILTER.equals(tagName)) {
            if (isLeanbackLauncherIntentFilter(element)) {
                mHasLeanbackLauncher = true;
            }
        }
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (mHasLeanbackLauncher && !mHasBanner && mApplicationElement != null) {
            XmlContext xmlContext = (XmlContext) context;
            xmlContext.report(
                    ISSUE,
                    mApplicationElement,
                    xmlContext.getLocation(mApplicationElement),
                    "TV application missing banner: declare `android:banner` on the application element");
        }
    }

    private static boolean isLeanbackLauncherIntentFilter(@NonNull Element element) {
        boolean hasMainAction = false;
        boolean hasLeanbackCategory = false;
        NodeList children = element.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element childElement = (Element) child;
            String childTag = childElement.getTagName();
            if (NODE_ACTION.equals(childTag)) {
                String name = childElement.getAttributeNS(ANDROID_URI, ATTR_NAME);
                if (ACTION_MAIN.equals(name)) {
                    hasMainAction = true;
                }
            } else if (NODE_CATEGORY.equals(childTag)) {
                String name = childElement.getAttributeNS(ANDROID_URI, ATTR_NAME);
                if (CATEGORY_LEANBACK_LAUNCHER.equals(name)) {
                    hasLeanbackCategory = true;
                }
            }
        }
        return hasMainAction && hasLeanbackCategory;
    }
}