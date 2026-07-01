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

    private static final String ANDROID_MANIFEST_XML = "AndroidManifest.xml";

    private static final String TAG_APPLICATION = "application";
    private static final String TAG_ACTIVITY = "activity";
    private static final String TAG_INTENT_FILTER = "intent-filter";
    private static final String TAG_ACTION = "action";
    private static final String TAG_CATEGORY = "category";

    private static final String ATTR_NAME = "android:name";

    private static final String ACTION_MAIN = "android.intent.action.MAIN";
    private static final String CATEGORY_LEANBACK_LAUNCHER = "android.intent.category.LEANBACK_LAUNCHER";

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

    /** Whether we are in a manifest file */
    private boolean mIsManifest;

    /** Whether we found a leanback launcher activity */
    private boolean mHasLeanbackLauncher;

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_ACTIVITY);
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mIsManifest = ANDROID_MANIFEST_XML.equals(context.file.getName());
        mHasLeanbackLauncher = false;
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (!mIsManifest) {
            return;
        }

        if (!mHasLeanbackLauncher) {
            // Report the issue on the manifest file itself
            context.report(
                    ISSUE,
                    context.getLocation(context.document.getDocumentElement()),
                    "Expecting an `<activity>` with `<intent-filter>` for "
                            + "`android.intent.category.LEANBACK_LAUNCHER`");
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (!mIsManifest) {
            return;
        }

        if (mHasLeanbackLauncher) {
            return;
        }

        if (TAG_ACTIVITY.equals(element.getTagName())) {
            if (hasLeanbackLauncherIntentFilter(element)) {
                mHasLeanbackLauncher = true;
            }
        }
    }

    /**
     * Checks whether the given activity element has an intent-filter that declares
     * both the MAIN action and the LEANBACK_LAUNCHER category.
     */
    private static boolean hasLeanbackLauncherIntentFilter(@NonNull Element activityElement) {
        NodeList children = activityElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE
                    && TAG_INTENT_FILTER.equals(child.getNodeName())) {
                Element intentFilter = (Element) child;
                if (hasAction(intentFilter, ACTION_MAIN)
                        && hasCategory(intentFilter, CATEGORY_LEANBACK_LAUNCHER)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Checks whether the given intent-filter element has an action with the specified name.
     */
    private static boolean hasAction(@NonNull Element intentFilter, @NonNull String actionName) {
        NodeList children = intentFilter.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE
                    && TAG_ACTION.equals(child.getNodeName())) {
                Element action = (Element) child;
                String name = action.getAttribute(ATTR_NAME);
                if (actionName.equals(name)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Checks whether the given intent-filter element has a category with the specified name.
     */
    private static boolean hasCategory(@NonNull Element intentFilter, @NonNull String categoryName) {
        NodeList children = intentFilter.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE
                    && TAG_CATEGORY.equals(child.getNodeName())) {
                Element category = (Element) child;
                String name = category.getAttribute(ATTR_NAME);
                if (categoryName.equals(name)) {
                    return true;
                }
            }
        }
        return false;
    }
}