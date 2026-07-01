package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Arrays;
import java.util.Collection;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class AndroidTvDetector extends Detector implements Detector.XmlScanner {

    private static final String TAG_USES_FEATURE = "uses-feature";
    private static final String TAG_ACTIVITY = "activity";
    private static final String TAG_ACTIVITY_ALIAS = "activity-alias";
    private static final String ATTR_NAME = "name";
    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";
    private static final String LEANBACK_FEATURE = "android.software.leanback";
    private static final String LEANBACK_LAUNCHER = "android.intent.category.LEANBACK_LAUNCHER";

    private static final Implementation IMPLEMENTATION =
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "MissingLeanbackSupport",
                    "Missing Leanback Support",
                    "The manifest should declare the use of the Leanback user interface required by Android TV. "
                            + "To fix this, add `<uses-feature android:name=\"android.software.leanback\" "
                            + "android:required=\"false\" />` to your manifest.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private boolean mHasLeanbackLauncher;
    private boolean mHasLeanbackFeature;

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_USES_FEATURE, TAG_ACTIVITY, TAG_ACTIVITY_ALIAS);
    }

    @Override
    public void beforeCheckFile(Context context) {
        mHasLeanbackLauncher = false;
        mHasLeanbackFeature = false;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String tag = element.getTagName();
        if (TAG_USES_FEATURE.equals(tag)) {
            String name = element.getAttributeNS(ANDROID_NS, ATTR_NAME);
            if (LEANBACK_FEATURE.equals(name)) {
                mHasLeanbackFeature = true;
            }
        } else if (TAG_ACTIVITY.equals(tag) || TAG_ACTIVITY_ALIAS.equals(tag)) {
            if (hasLeanbackLauncherCategory(element)) {
                mHasLeanbackLauncher = true;
            }
        }
    }

    private boolean hasLeanbackLauncherCategory(Element element) {
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && "intent-filter".equals(child.getNodeName())) {
                NodeList categories = ((Element) child).getElementsByTagName("category");
                for (int j = 0; j < categories.getLength(); j++) {
                    Element category = (Element) categories.item(j);
                    String name = category.getAttributeNS(ANDROID_NS, ATTR_NAME);
                    if (LEANBACK_LAUNCHER.equals(name)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public void afterCheckFile(Context context) {
        if (mHasLeanbackLauncher && !mHasLeanbackFeature) {
            XmlContext xmlContext = (XmlContext) context;
            xmlContext.report(
                    ISSUE,
                    xmlContext.document.getDocumentElement(),
                    "Missing `<uses-feature android:name=\"android.software.leanback\" "
                            + "android:required=\"false\" />` declaration required for Android TV");
        }
    }
}