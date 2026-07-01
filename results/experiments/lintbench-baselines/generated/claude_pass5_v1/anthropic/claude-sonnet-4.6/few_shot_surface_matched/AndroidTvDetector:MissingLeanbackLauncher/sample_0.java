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

    public static final Issue MISSING_LEANBACK_LAUNCHER =
            Issue.create(
                    "MissingLeanbackLauncher",
                    "Missing Leanback Launcher Intent Filter",
                    "An application intended to run on TV devices must declare a launcher activity "
                            + "for TV in its manifest using a "
                            + "`android.intent.category.LEANBACK_LAUNCHER` intent filter.",
                    Category.CORRECTNESS,
                    8,
                    Severity.ERROR,
                    new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE));

    private static final String LEANBACK_LAUNCHER_CATEGORY =
            "android.intent.category.LEANBACK_LAUNCHER";
    private static final String LEANBACK_FEATURE = "android.software.leanback";

    private static final String NODE_USES_FEATURE = "uses-feature";
    private static final String NODE_APPLICATION = "application";
    private static final String NODE_ACTIVITY = "activity";
    private static final String NODE_INTENT_FILTER = "intent-filter";
    private static final String NODE_CATEGORY = "category";
    private static final String ATTR_NAME = "name";
    private static final String ATTR_ANDROID_NAME = "android:name";

    private boolean mHasLeanbackFeature;
    private boolean mHasLeanbackLauncher;
    private Element mApplicationElement;

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(NODE_USES_FEATURE, NODE_APPLICATION);
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mHasLeanbackFeature = false;
        mHasLeanbackLauncher = false;
        mApplicationElement = null;
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (mHasLeanbackFeature && !mHasLeanbackLauncher && mApplicationElement != null) {
            XmlContext xmlContext = (XmlContext) context;
            xmlContext.report(
                    MISSING_LEANBACK_LAUNCHER,
                    mApplicationElement,
                    xmlContext.getLocation(mApplicationElement),
                    "Manifest should contain a `<activity>` tag with a `LEANBACK_LAUNCHER` "
                            + "intent filter for TV.");
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();

        if (NODE_USES_FEATURE.equals(tagName)) {
            String name = element.getAttribute(ATTR_ANDROID_NAME);
            if (LEANBACK_FEATURE.equals(name)) {
                mHasLeanbackFeature = true;
            }
        } else if (NODE_APPLICATION.equals(tagName)) {
            mApplicationElement = element;
            mHasLeanbackLauncher = hasLeanbackLauncherActivity(element);
        }
    }

    private boolean hasLeanbackLauncherActivity(@NonNull Element application) {
        NodeList children = application.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element childElement = (Element) child;
            if (NODE_ACTIVITY.equals(childElement.getTagName())) {
                if (hasLeanbackLauncherIntentFilter(childElement)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasLeanbackLauncherIntentFilter(@NonNull Element activity) {
        NodeList children = activity.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element childElement = (Element) child;
            if (NODE_INTENT_FILTER.equals(childElement.getTagName())) {
                if (intentFilterHasLeanbackCategory(childElement)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean intentFilterHasLeanbackCategory(@NonNull Element intentFilter) {
        NodeList children = intentFilter.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element childElement = (Element) child;
            if (NODE_CATEGORY.equals(childElement.getTagName())) {
                String name = childElement.getAttribute(ATTR_ANDROID_NAME);
                if (LEANBACK_LAUNCHER_CATEGORY.equals(name)) {
                    return true;
                }
            }
        }
        return false;
    }
}