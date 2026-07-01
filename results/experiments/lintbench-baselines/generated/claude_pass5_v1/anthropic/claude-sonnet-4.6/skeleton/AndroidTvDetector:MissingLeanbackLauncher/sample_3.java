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
import java.util.Arrays;
import java.util.Collection;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class AndroidTvDetector extends Detector implements XmlScanner {

    private static final String TAG_MANIFEST = "manifest";
    private static final String TAG_APPLICATION = "application";
    private static final String TAG_ACTIVITY = "activity";
    private static final String TAG_INTENT_FILTER = "intent-filter";
    private static final String TAG_CATEGORY = "category";
    private static final String TAG_ACTION = "action";
    private static final String ATTR_NAME = "name";
    private static final String LEANBACK_LAUNCHER_CATEGORY =
            "android.intent.category.LEANBACK_LAUNCHER";
    private static final String ACTION_MAIN = "android.intent.action.MAIN";
    private static final String USES_FEATURE = "uses-feature";
    private static final String LEANBACK_FEATURE = "android.software.leanback";
    private static final String ATTR_REQUIRED = "required";

    private static final Implementation IMPLEMENTATION =
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "MissingLeanbackLauncher",
                    "Missing Leanback Launcher Intent Filter",
                    "An application intended to run on TV devices must declare a launcher "
                            + "activity for TV in its manifest using a "
                            + "`android.intent.category.LEANBACK_LAUNCHER` intent filter.",
                    Category.CORRECTNESS,
                    8,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private boolean mHasLeanbackLauncherActivity;
    private boolean mHasLeanbackFeature;
    private Element mApplicationElement;

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_APPLICATION, USES_FEATURE);
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mHasLeanbackLauncherActivity = false;
        mHasLeanbackFeature = false;
        mApplicationElement = null;
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (mHasLeanbackFeature && !mHasLeanbackLauncherActivity && mApplicationElement != null) {
            XmlContext xmlContext = (XmlContext) context;
            xmlContext.report(
                    ISSUE,
                    mApplicationElement,
                    xmlContext.getLocation(mApplicationElement),
                    "Expecting an `<activity>` tag declaring a `"
                            + LEANBACK_LAUNCHER_CATEGORY
                            + "` intent filter.");
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();
        if (USES_FEATURE.equals(tagName)) {
            String nameAttr = element.getAttributeNS(
                    "http://schemas.android.com/apk/res/android", ATTR_NAME);
            if (LEANBACK_FEATURE.equals(nameAttr)) {
                // Check if required is explicitly false; if not present or true, it counts
                String required = element.getAttributeNS(
                        "http://schemas.android.com/apk/res/android", ATTR_REQUIRED);
                if (required == null || required.isEmpty() || !required.equals("false")) {
                    mHasLeanbackFeature = true;
                } else {
                    // required="false" still means the app targets TV
                    mHasLeanbackFeature = true;
                }
            }
        } else if (TAG_APPLICATION.equals(tagName)) {
            mApplicationElement = element;
            // Check all activities within application
            NodeList children = element.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node child = children.item(i);
                if (child.getNodeType() == Node.ELEMENT_NODE) {
                    Element childElement = (Element) child;
                    if (TAG_ACTIVITY.equals(childElement.getTagName())) {
                        if (hasLeanbackLauncherIntentFilter(childElement)) {
                            mHasLeanbackLauncherActivity = true;
                            break;
                        }
                    }
                }
            }
        }
    }

    private boolean hasLeanbackLauncherIntentFilter(@NonNull Element activityElement) {
        NodeList children = activityElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                if (TAG_INTENT_FILTER.equals(childElement.getTagName())) {
                    if (intentFilterHasLeanbackLauncher(childElement)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean intentFilterHasLeanbackLauncher(@NonNull Element intentFilterElement) {
        boolean hasMainAction = false;
        boolean hasLeanbackCategory = false;

        NodeList children = intentFilterElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                String tagName = childElement.getTagName();
                String nameAttr = childElement.getAttributeNS(
                        "http://schemas.android.com/apk/res/android", ATTR_NAME);
                if (TAG_ACTION.equals(tagName) && ACTION_MAIN.equals(nameAttr)) {
                    hasMainAction = true;
                } else if (TAG_CATEGORY.equals(tagName)
                        && LEANBACK_LAUNCHER_CATEGORY.equals(nameAttr)) {
                    hasLeanbackCategory = true;
                }
            }
        }

        return hasLeanbackCategory;
    }
}