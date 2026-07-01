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

    private static final String TV_FEATURE = "android.hardware.type.television";
    private static final String ACTION_MAIN = "android.intent.action.MAIN";
    private static final String CATEGORY_LEANBACK_LAUNCHER =
            "android.intent.category.LEANBACK_LAUNCHER";

    private static final Implementation IMPLEMENTATION =
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "MissingLeanbackLauncher",
                    "Missing Leanback Launcher Intent Filter",
                    "Apps intended to run on TV devices must declare a launcher activity with an "
                            + "intent filter containing ACTION_MAIN and the "
                            + "CATEGORY_LEANBACK_LAUNCHER category. This allows the activity to be "
                            + "listed on the TV home screen.",
                    Category.CORRECTNESS,
                    8,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private boolean mHasTvFeature;
    private boolean mHasLeanbackLauncher;
    private Element mApplicationElement;

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("application", "activity", "activity-alias", "uses-feature");
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mHasTvFeature = false;
        mHasLeanbackLauncher = false;
        mApplicationElement = null;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();
        if ("uses-feature".equals(tag)) {
            String name = element.getAttribute("android:name");
            if (TV_FEATURE.equals(name)
                    && !"false".equals(element.getAttribute("android:required"))) {
                mHasTvFeature = true;
            }
        } else if ("activity".equals(tag) || "activity-alias".equals(tag)) {
            if (hasLeanbackLauncher(element)) {
                mHasLeanbackLauncher = true;
            }
        } else if ("application".equals(tag)) {
            mApplicationElement = element;
        }
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (mHasTvFeature && !mHasLeanbackLauncher && mApplicationElement != null) {
            XmlContext xmlContext = (XmlContext) context;
            xmlContext.report(
                    ISSUE,
                    mApplicationElement,
                    xmlContext.getLocation(mApplicationElement),
                    "When running on a TV device, the app must declare a launcher activity with "
                            + "an intent filter that includes the "
                            + "android.intent.category.LEANBACK_LAUNCHER category.");
        }
    }

    private static boolean hasLeanbackLauncher(@NonNull Element activity) {
        NodeList filters = activity.getElementsByTagName("intent-filter");
        for (int i = 0; i < filters.getLength(); i++) {
            Element filter = (Element) filters.item(i);
            boolean hasMain = false;
            boolean hasLeanback = false;
            NodeList children = filter.getChildNodes();
            for (int j = 0; j < children.getLength(); j++) {
                Node child = children.item(j);
                if (child.getNodeType() != Node.ELEMENT_NODE) {
                    continue;
                }
                String childTag = child.getNodeName();
                String name = ((Element) child).getAttribute("android:name");
                if ("action".equals(childTag) && ACTION_MAIN.equals(name)) {
                    hasMain = true;
                } else if ("category".equals(childTag)
                        && CATEGORY_LEANBACK_LAUNCHER.equals(name)) {
                    hasLeanback = true;
                }
            }
            if (hasMain && hasLeanback) {
                return true;
            }
        }
        return false;
    }
}