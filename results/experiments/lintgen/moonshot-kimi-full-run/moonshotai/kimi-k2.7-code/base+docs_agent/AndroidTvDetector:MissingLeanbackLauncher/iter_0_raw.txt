package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Arrays;
import java.util.Collection;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class AndroidTvDetector extends Detector implements Detector.XmlScanner {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    private static final String TAG_USES_FEATURE = "uses-feature";
    private static final String TAG_ACTIVITY = "activity";
    private static final String TAG_ACTIVITY_ALIAS = "activity-alias";
    private static final String TAG_INTENT_FILTER = "intent-filter";
    private static final String TAG_ACTION = "action";
    private static final String TAG_CATEGORY = "category";

    private static final String ATTR_NAME = "name";
    private static final String ATTR_REQUIRED = "required";

    private static final String ACTION_MAIN = "android.intent.action.MAIN";
    private static final String CATEGORY_LEANBACK_LAUNCHER =
            "android.intent.category.LEANBACK_LAUNCHER";
    private static final String TV_FEATURE_LEANBACK = "android.software.leanback";
    private static final String TV_FEATURE_TELEVISION = "android.hardware.type.television";

    public static final Issue ISSUE = Issue.create(
            "MissingLeanbackLauncher",
            "Missing Leanback Launcher Intent Filter",
            "An application intended to run on TV devices must declare a launcher "
                    + "activity for TV in its manifest using an "
                    + "`android.intent.category.LEANBACK_LAUNCHER` intent filter.",
            Category.CORRECTNESS,
            6,
            Severity.FATAL,
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE),
            "https://developer.android.com/training/tv/start/start.html#tv-activity"
    );

    private boolean mIsTvRequired;
    private Location mTvFeatureLocation;
    private boolean mHasLeanbackLauncher;

    @Override
    public void beforeCheckEachProject(@NotNull Context context) {
        mIsTvRequired = false;
        mTvFeatureLocation = null;
        mHasLeanbackLauncher = false;
    }

    @Override
    @NotNull
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_USES_FEATURE, TAG_ACTIVITY, TAG_ACTIVITY_ALIAS);
    }

    @Override
    public void visitElement(@NotNull XmlContext context, @NotNull Element element) {
        String tag = element.getTagName();
        if (TAG_USES_FEATURE.equals(tag)) {
            String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
            if (TV_FEATURE_LEANBACK.equals(name) || TV_FEATURE_TELEVISION.equals(name)) {
                String required = element.getAttributeNS(ANDROID_URI, ATTR_REQUIRED);
                if (required == null || required.isEmpty() || Boolean.parseBoolean(required)) {
                    mIsTvRequired = true;
                    if (mTvFeatureLocation == null) {
                        mTvFeatureLocation = context.getElementLocation(element);
                    }
                }
            }
        } else if (TAG_ACTIVITY.equals(tag) || TAG_ACTIVITY_ALIAS.equals(tag)) {
            if (hasLeanbackLauncher(element)) {
                mHasLeanbackLauncher = true;
            }
        }
    }

    private boolean hasLeanbackLauncher(@NotNull Element activity) {
        NodeList filters = activity.getElementsByTagName(TAG_INTENT_FILTER);
        for (int i = 0; i < filters.getLength(); i++) {
            Element filter = (Element) filters.item(i);
            boolean hasMain = false;
            boolean hasLeanback = false;

            NodeList children = filter.getChildNodes();
            for (int j = 0; j < children.getLength(); j++) {
                Node child = children.item(j);
                if (child.getNodeType() == Node.ELEMENT_NODE) {
                    Element childElement = (Element) child;
                    String childTag = childElement.getTagName();
                    if (TAG_ACTION.equals(childTag)) {
                        String actionName = childElement.getAttributeNS(ANDROID_URI, ATTR_NAME);
                        if (ACTION_MAIN.equals(actionName)) {
                            hasMain = true;
                        }
                    } else if (TAG_CATEGORY.equals(childTag)) {
                        String categoryName = childElement.getAttributeNS(ANDROID_URI, ATTR_NAME);
                        if (CATEGORY_LEANBACK_LAUNCHER.equals(categoryName)) {
                            hasLeanback = true;
                        }
                    }
                }
            }

            if (hasMain && hasLeanback) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void afterCheckEachProject(@NotNull Context context) {
        if (mIsTvRequired && !mHasLeanbackLauncher && mTvFeatureLocation != null) {
            context.report(
                    ISSUE,
                    mTvFeatureLocation,
                    "Missing `LEANBACK_LAUNCHER` intent filter. TV apps must have an activity "
                            + "with action MAIN and category LEANBACK_LAUNCHER."
            );
        }
    }
}