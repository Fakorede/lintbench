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

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String TV_FEATURE = "android.hardware.type.television";
    private static final String LEANBACK_CATEGORY = "android.intent.category.LEANBACK_LAUNCHER";
    private static final String ATTR_NAME = "name";
    private static final String TAG_MANIFEST = "manifest";
    private static final String TAG_USES_FEATURE = "uses-feature";
    private static final String TAG_INTENT_FILTER = "intent-filter";
    private static final String TAG_CATEGORY = "category";
    private static final String TAG_ACTIVITY = "activity";
    private static final String TAG_ACTIVITY_ALIAS = "activity-alias";

    private static final Implementation IMPLEMENTATION =
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "MissingLeanbackLauncher",
                    "Missing Leanback Launcher Intent Filter",
                    "An application that is intended to run on TV devices must declare a launcher "
                            + "activity for TV in its manifest. Add an `<intent-filter>` to the TV "
                            + "launcher `<activity>` that includes the "
                            + "`android.intent.category.LEANBACK_LAUNCHER` category.",
                    Category.CORRECTNESS,
                    8,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private boolean mHasTvFeature;
    private boolean mHasLeanbackLauncher;
    private Element mManifestElement;

    @Override
    @NonNull
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_MANIFEST, TAG_USES_FEATURE, TAG_INTENT_FILTER);
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mHasTvFeature = false;
        mHasLeanbackLauncher = false;
        mManifestElement = null;
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (mHasTvFeature && !mHasLeanbackLauncher && mManifestElement != null) {
            XmlContext xmlContext = (XmlContext) context;
            xmlContext.report(
                    ISSUE,
                    mManifestElement,
                    xmlContext.getElementLocation(mManifestElement),
                    "Apps that run on TV devices must declare a TV launcher activity with an "
                            + "intent filter containing android.intent.category.LEANBACK_LAUNCHER");
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();
        if (TAG_MANIFEST.equals(tag)) {
            mManifestElement = element;
        } else if (TAG_USES_FEATURE.equals(tag)) {
            String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
            if (TV_FEATURE.equals(name)) {
                mHasTvFeature = true;
            }
        } else if (TAG_INTENT_FILTER.equals(tag)) {
            Node parent = element.getParentNode();
            if (parent instanceof Element) {
                String parentTag = ((Element) parent).getTagName();
                if (TAG_ACTIVITY.equals(parentTag) || TAG_ACTIVITY_ALIAS.equals(parentTag)) {
                    Node child = element.getFirstChild();
                    while (child != null) {
                        if (child.getNodeType() == Node.ELEMENT_NODE
                                && TAG_CATEGORY.equals(child.getNodeName())) {
                            Element category = (Element) child;
                            String categoryName = category.getAttributeNS(ANDROID_URI, ATTR_NAME);
                            if (LEANBACK_CATEGORY.equals(categoryName)) {
                                mHasLeanbackLauncher = true;
                                break;
                            }
                        }
                        child = child.getNextSibling();
                    }
                }
            }
        }
    }
}