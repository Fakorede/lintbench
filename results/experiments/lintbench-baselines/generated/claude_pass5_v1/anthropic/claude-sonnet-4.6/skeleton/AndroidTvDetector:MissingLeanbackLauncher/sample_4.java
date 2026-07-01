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
    private static final String TAG_CATEGORY = "category";
    private static final String TAG_ACTION = "action";
    private static final String ATTR_NAME = "android:name";
    private static final String CATEGORY_LEANBACK_LAUNCHER =
            "android.intent.category.LEANBACK_LAUNCHER";
    private static final String ACTION_MAIN = "android.intent.action.MAIN";

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

    /** Whether the current file is a manifest file */
    private boolean mIsManifest;

    /** Whether we found a leanback launcher intent filter */
    private boolean mHasLeanbackLauncher;

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_APPLICATION);
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mIsManifest = context.file.getName().equals(ANDROID_MANIFEST_XML);
        mHasLeanbackLauncher = false;
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (mIsManifest && !mHasLeanbackLauncher) {
            context.report(
                    ISSUE,
                    context.getLocation(context.file),
                    "Expecting an activity to have `android.intent.category.LEANBACK_LAUNCHER` "
                            + "intent filter.");
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (!mIsManifest) {
            return;
        }

        // Look through all activity elements inside the application element
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                if (TAG_ACTIVITY.equals(childElement.getTagName())) {
                    if (activityHasLeanbackLauncher(childElement)) {
                        mHasLeanbackLauncher = true;
                        return;
                    }
                }
            }
        }
    }

    /**
     * Checks whether the given activity element has an intent filter with
     * LEANBACK_LAUNCHER category and MAIN action.
     */
    private boolean activityHasLeanbackLauncher(@NonNull Element activity) {
        NodeList children = activity.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                if (TAG_INTENT_FILTER.equals(childElement.getTagName())) {
                    if (intentFilterHasLeanbackLauncher(childElement)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Checks whether the given intent-filter element has both MAIN action and
     * LEANBACK_LAUNCHER category.
     */
    private boolean intentFilterHasLeanbackLauncher(@NonNull Element intentFilter) {
        boolean hasMainAction = false;
        boolean hasLeanbackCategory = false;

        NodeList children = intentFilter.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                String tagName = childElement.getTagName();
                String name = childElement.getAttribute(ATTR_NAME);

                if (TAG_ACTION.equals(tagName) && ACTION_MAIN.equals(name)) {
                    hasMainAction = true;
                } else if (TAG_CATEGORY.equals(tagName)
                        && CATEGORY_LEANBACK_LAUNCHER.equals(name)) {
                    hasLeanbackCategory = true;
                }
            }
        }

        return hasMainAction && hasLeanbackCategory;
    }
}