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

public class AndroidTvDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "MissingLeanbackLauncher",
                    "Missing Leanback Launcher Intent Filter",
                    "An application intended to run on TV devices must declare a launcher activity"
                            + " for TV in its manifest using an intent filter that includes"
                            + " `android.intent.category.LEANBACK_LAUNCHER`.",
                    Category.CORRECTNESS,
                    5,
                    Severity.ERROR,
                    new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE));

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_NAME = "name";
    private static final String ATTR_REQUIRED = "required";

    private static final String TAG_APPLICATION = "application";
    private static final String TAG_ACTIVITY = "activity";
    private static final String TAG_ACTIVITY_ALIAS = "activity-alias";
    private static final String TAG_USES_FEATURE = "uses-feature";
    private static final String TAG_INTENT_FILTER = "intent-filter";
    private static final String TAG_ACTION = "action";
    private static final String TAG_CATEGORY = "category";

    private static final String TV_FEATURE = "android.hardware.type.television";
    private static final String ACTION_MAIN = "android.intent.action.MAIN";
    private static final String CATEGORY_LEANBACK_LAUNCHER = "android.intent.category.LEANBACK_LAUNCHER";

    private boolean mHasTvFeature;
    private boolean mHasLeanbackLauncher;
    private Element mApplicationElement;

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                TAG_APPLICATION, TAG_ACTIVITY, TAG_ACTIVITY_ALIAS, TAG_USES_FEATURE);
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mHasTvFeature = false;
        mHasLeanbackLauncher = false;
        mApplicationElement = null;
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (!mHasTvFeature || mHasLeanbackLauncher) {
            return;
        }
        if (!(context instanceof XmlContext)) {
            return;
        }
        XmlContext xmlContext = (XmlContext) context;
        Element target =
                mApplicationElement != null
                        ? mApplicationElement
                        : xmlContext.document.getDocumentElement();
        if (target == null) {
            return;
        }
        xmlContext.report(
                ISSUE,
                target,
                xmlContext.getLocation(target),
                "Add a TV launcher activity with an intent filter containing"
                        + " `android.intent.action.MAIN` and"
                        + " `android.intent.category.LEANBACK_LAUNCHER`");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();
        if (TAG_APPLICATION.equals(tag)) {
            mApplicationElement = element;
        } else if (TAG_ACTIVITY.equals(tag) || TAG_ACTIVITY_ALIAS.equals(tag)) {
            if (hasLeanbackLauncher(element)) {
                mHasLeanbackLauncher = true;
            }
        } else if (TAG_USES_FEATURE.equals(tag)) {
            if (isTvFeature(element)) {
                mHasTvFeature = true;
            }
        }
    }

    private boolean isTvFeature(Element element) {
        if (!TV_FEATURE.equals(getAttributeName(element))) {
            return false;
        }
        String required = element.getAttributeNS(ANDROID_URI, ATTR_REQUIRED);
        return required.isEmpty() || Boolean.parseBoolean(required);
    }

    private boolean hasLeanbackLauncher(Element activity) {
        for (int i = 0; i < activity.getChildNodes().getLength(); i++) {
            Node node = activity.getChildNodes().item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element child = (Element) node;
            if (!TAG_INTENT_FILTER.equals(child.getTagName())) {
                continue;
            }
            boolean hasMain = false;
            boolean hasLeanback = false;
            for (int j = 0; j < child.getChildNodes().getLength(); j++) {
                Node inner = child.getChildNodes().item(j);
                if (inner.getNodeType() != Node.ELEMENT_NODE) {
                    continue;
                }
                Element innerElement = (Element) inner;
                String innerTag = innerElement.getTagName();
                if (TAG_ACTION.equals(innerTag)
                        && ACTION_MAIN.equals(getAttributeName(innerElement))) {
                    hasMain = true;
                } else if (TAG_CATEGORY.equals(innerTag)
                        && CATEGORY_LEANBACK_LAUNCHER.equals(getAttributeName(innerElement))) {
                    hasLeanback = true;
                }
            }
            if (hasMain && hasLeanback) {
                return true;
            }
        }
        return false;
    }

    private String getAttributeName(Element element) {
        String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
        if (name.isEmpty()) {
            name = element.getAttribute(ATTR_NAME);
        }
        return name;
    }
}