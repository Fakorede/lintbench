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
    private static final String TAG_MANIFEST = "manifest";
    private static final String TAG_USES_FEATURE = "uses-feature";
    private static final String TAG_CATEGORY = "category";
    private static final String TAG_ACTIVITY = "activity";
    private static final String TAG_ACTIVITY_ALIAS = "activity-alias";
    private static final String TV_FEATURE = "android.hardware.type.television";
    private static final String LEANBACK_CATEGORY = "android.intent.category.LEANBACK_LAUNCHER";

    private static final Implementation IMPLEMENTATION =
            new Implementation(AndroidTvDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "MissingLeanbackLauncher",
                    "Missing Leanback Launcher Intent Filter",
                    "Apps that run on Android TV devices must provide an activity that declares "
                            + "an intent filter with the `android.intent.category.LEANBACK_LAUNCHER` "
                            + "category. This category identifies the activity as a TV launcher and "
                            + "makes it visible on the TV home screen. If your manifest declares "
                            + "`android.hardware.type.television` as a required feature, you must "
                            + "also declare a leanback launcher activity.",
                    Category.CORRECTNESS,
                    8,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private boolean mHasTvFeature;
    private boolean mHasLeanbackLauncher;

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_MANIFEST, TAG_USES_FEATURE, TAG_CATEGORY);
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mHasTvFeature = false;
        mHasLeanbackLauncher = false;
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        XmlContext xmlContext = (XmlContext) context;
        Element root = xmlContext.getDocument().getDocumentElement();
        if (root == null || !TAG_MANIFEST.equals(root.getTagName())) {
            return;
        }
        if (mHasTvFeature && !mHasLeanbackLauncher) {
            xmlContext.report(
                    ISSUE,
                    root,
                    xmlContext.getLocation(root),
                    "Missing Leanback Launcher Intent Filter");
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();
        if (TAG_USES_FEATURE.equals(tag)) {
            String name = getAndroidAttribute(element, "name");
            String required = getAndroidAttribute(element, "required");
            if (TV_FEATURE.equals(name) && !"false".equals(required)) {
                mHasTvFeature = true;
            }
        } else if (TAG_CATEGORY.equals(tag)) {
            String name = getAndroidAttribute(element, "name");
            if (LEANBACK_CATEGORY.equals(name) && isInsideActivity(element)) {
                mHasLeanbackLauncher = true;
            }
        }
    }

    private static String getAndroidAttribute(Element element, String localName) {
        String value = element.getAttributeNS(ANDROID_URI, localName);
        if (value != null && !value.isEmpty()) {
            return value;
        }
        return element.getAttribute(localName);
    }

    private static boolean isInsideActivity(Element element) {
        Node node = element.getParentNode();
        while (node != null && node.getNodeType() == Node.ELEMENT_NODE) {
            Element parent = (Element) node;
            String tag = parent.getTagName();
            if (TAG_ACTIVITY.equals(tag) || TAG_ACTIVITY_ALIAS.equals(tag)) {
                return true;
            }
            if (TAG_MANIFEST.equals(tag)) {
                return false;
            }
            node = parent.getParentNode();
        }
        return false;
    }
}