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
import java.util.Arrays;
import java.util.Collection;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class AndroidTvDetector extends Detector implements Detector.XmlScanner {

    private static final String TV_FEATURE = "android.hardware.type.television";
    private static final String ACTION_MAIN = "android.intent.action.MAIN";
    private static final String CATEGORY_LEANBACK_LAUNCHER =
            "android.intent.category.LEANBACK_LAUNCHER";
    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";

    private static final Implementation IMPLEMENTATION =
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "MissingLeanbackLauncher",
                    "Missing Leanback Launcher Intent Filter",
                    "An application intended to run on TV devices must declare a launcher "
                            + "activity with an intent filter that includes both "
                            + "`android.intent.action.MAIN` and "
                            + "`android.intent.category.LEANBACK_LAUNCHER`.",
                    Category.TV,
                    5,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private boolean mRequiresTv;
    private boolean mHasLeanbackLauncher;
    private Element mApplicationElement;

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("application", "uses-feature");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();
        if ("uses-feature".equals(tag)) {
            String name = element.getAttributeNS(ANDROID_NS, "name");
            if (TV_FEATURE.equals(name)) {
                String required = element.getAttributeNS(ANDROID_NS, "required");
                boolean isRequired =
                        required == null || required.isEmpty() || Boolean.parseBoolean(required);
                if (isRequired) {
                    mRequiresTv = true;
                }
            }
        } else if ("application".equals(tag)) {
            mApplicationElement = element;
            NodeList children = element.getChildNodes();
            for (int i = 0, n = children.getLength(); i < n; i++) {
                Node child = children.item(i);
                if (child.getNodeType() == Node.ELEMENT_NODE) {
                    String childTag = child.getNodeName();
                    if ("activity".equals(childTag) || "activity-alias".equals(childTag)) {
                        if (hasLeanbackLauncher((Element) child)) {
                            mHasLeanbackLauncher = true;
                            break;
                        }
                    }
                }
            }
        }
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (mRequiresTv && !mHasLeanbackLauncher && mApplicationElement != null) {
            XmlContext xmlContext = (XmlContext) context;
            xmlContext.report(
                    ISSUE,
                    mApplicationElement,
                    xmlContext.getLocation(mApplicationElement),
                    "TV apps must declare a launcher activity with the MAIN/LEANBACK_LAUNCHER intent filter");
        }
        reset();
    }

    private void reset() {
        mRequiresTv = false;
        mHasLeanbackLauncher = false;
        mApplicationElement = null;
    }

    private static boolean hasLeanbackLauncher(@NonNull Element activity) {
        NodeList filters = activity.getElementsByTagName("intent-filter");
        for (int i = 0, n = filters.getLength(); i < n; i++) {
            Element filter = (Element) filters.item(i);
            if (hasMainAction(filter) && hasLeanbackCategory(filter)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasMainAction(@NonNull Element filter) {
        NodeList actions = filter.getElementsByTagName("action");
        for (int i = 0, n = actions.getLength(); i < n; i++) {
            Element action = (Element) actions.item(i);
            if (ACTION_MAIN.equals(action.getAttributeNS(ANDROID_NS, "name"))) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasLeanbackCategory(@NonNull Element filter) {
        NodeList categories = filter.getElementsByTagName("category");
        for (int i = 0, n = categories.getLength(); i < n; i++) {
            Element category = (Element) categories.item(i);
            if (CATEGORY_LEANBACK_LAUNCHER.equals(category.getAttributeNS(ANDROID_NS, "name"))) {
                return true;
            }
        }
        return false;
    }
}