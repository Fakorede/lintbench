package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Detector.XmlScanner;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class AndroidTvDetector extends Detector implements XmlScanner {

    private static final String ANDROID_MANIFEST_FILE = "AndroidManifest.xml";
    private static final String TAG_APPLICATION = "application";
    private static final String TAG_ACTIVITY = "activity";
    private static final String TAG_INTENT_FILTER = "intent-filter";
    private static final String TAG_CATEGORY = "category";
    private static final String ATTR_NAME = "name";
    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";
    private static final String LEANBACK_LAUNCHER =
            "android.intent.category.LEANBACK_LAUNCHER";

    private static final Implementation IMPLEMENTATION =
            new Implementation(AndroidTvDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "MissingLeanbackLauncher",
                    "Missing Leanback Launcher Intent Filter",
                    "Apps that run on Android TV devices must declare a TV launcher activity "
                            + "in the manifest with an `<intent-filter>` that includes "
                            + "`android.intent.action.MAIN` and "
                            + "`android.intent.category.LEANBACK_LAUNCHER`.",
                    Category.CORRECTNESS,
                    8,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private boolean mHasApplication;
    private boolean mHasLeanbackLauncher;
    private Element mApplicationElement;

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_APPLICATION, TAG_ACTIVITY);
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mHasApplication = false;
        mHasLeanbackLauncher = false;
        mApplicationElement = null;
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (!(context instanceof XmlContext)) {
            return;
        }

        XmlContext xmlContext = (XmlContext) context;
        if (!xmlContext.getFile().getName().equals(ANDROID_MANIFEST_FILE)) {
            return;
        }

        if (mHasApplication && !mHasLeanbackLauncher && mApplicationElement != null) {
            xmlContext.report(
                    ISSUE,
                    mApplicationElement,
                    xmlContext.getLocation(mApplicationElement),
                    "Missing Leanback Launcher Intent Filter: declare an activity with "
                            + "android.intent.category.LEANBACK_LAUNCHER for TV devices.");
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();
        if (TAG_APPLICATION.equals(tag)) {
            mHasApplication = true;
            mApplicationElement = element;
        } else if (TAG_ACTIVITY.equals(tag)) {
            if (hasLeanbackLauncher(element)) {
                mHasLeanbackLauncher = true;
            }
        }
    }

    private static boolean hasLeanbackLauncher(Element activity) {
        for (Element intentFilter : getChildrenByTag(activity, TAG_INTENT_FILTER)) {
            for (Element category : getChildrenByTag(intentFilter, TAG_CATEGORY)) {
                String name = category.getAttributeNS(ANDROID_NS, ATTR_NAME);
                if (LEANBACK_LAUNCHER.equals(name)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Collection<Element> getChildrenByTag(Element parent, String tag) {
        Collection<Element> result = new ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE
                    && tag.equals(((Element) child).getTagName())) {
                result.add((Element) child);
            }
        }
        return result;
    }
}