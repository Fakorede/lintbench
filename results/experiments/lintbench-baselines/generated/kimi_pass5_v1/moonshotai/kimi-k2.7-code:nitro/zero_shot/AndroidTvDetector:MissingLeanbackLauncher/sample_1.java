package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_REQUIRED;
import static com.android.SdkConstants.TAG_ACTIVITY;
import static com.android.SdkConstants.TAG_ACTIVITY_ALIAS;
import static com.android.SdkConstants.TAG_CATEGORY;
import static com.android.SdkConstants.TAG_INTENT_FILTER;
import static com.android.SdkConstants.TAG_USES_FEATURE;
import static com.android.SdkConstants.VALUE_FALSE;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class AndroidTvDetector extends Detector implements Detector.XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "MissingLeanbackLauncher",
            "Missing Leanback Launcher Intent Filter",
            "An application intended to run on TV devices must declare a launcher activity "
                    + "for TV in its manifest using an "
                    + "`android.intent.category.LEANBACK_LAUNCHER` intent filter.",
            Category.CORRECTNESS,
            5,
            Severity.ERROR,
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE));

    private static final String TV_FEATURE = "android.hardware.type.television";
    private static final String LEANBACK_CATEGORY = "android.intent.category.LEANBACK_LAUNCHER";

    private boolean mHasTvFeature;
    private boolean mHasLeanbackLauncher;
    private final List<Location> mTvFeatureLocations = new ArrayList<Location>();

    @Override
    public void beforeCheckProject(@NonNull Context context) {
        mHasTvFeature = false;
        mHasLeanbackLauncher = false;
        mTvFeatureLocations.clear();
    }

    @Override
    @NonNull
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_USES_FEATURE, TAG_ACTIVITY, TAG_ACTIVITY_ALIAS);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();
        if (TAG_USES_FEATURE.equals(tag)) {
            String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
            if (TV_FEATURE.equals(name)) {
                String required = element.getAttributeNS(ANDROID_URI, ATTR_REQUIRED);
                if (required.isEmpty() || !VALUE_FALSE.equals(required)) {
                    mHasTvFeature = true;
                    mTvFeatureLocations.add(context.getLocation(element));
                }
            }
        } else if (TAG_ACTIVITY.equals(tag) || TAG_ACTIVITY_ALIAS.equals(tag)) {
            if (hasLeanbackLauncher(element)) {
                mHasLeanbackLauncher = true;
            }
        }
    }

    @Override
    public void afterCheckProject(@NonNull Context context) {
        if (context.getProject().isLibrary()) {
            return;
        }
        if (mHasTvFeature && !mHasLeanbackLauncher) {
            String message =
                    "TV apps must declare a launcher activity with a LEANBACK_LAUNCHER intent filter";
            for (Location location : mTvFeatureLocations) {
                context.report(ISSUE, location, message);
            }
        }
    }

    private static boolean hasLeanbackLauncher(@NonNull Element activity) {
        for (Element child : getChildElements(activity)) {
            if (TAG_INTENT_FILTER.equals(child.getTagName())) {
                for (Element grandChild : getChildElements(child)) {
                    if (TAG_CATEGORY.equals(grandChild.getTagName())) {
                        String name = grandChild.getAttributeNS(ANDROID_URI, ATTR_NAME);
                        if (LEANBACK_CATEGORY.equals(name)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    @NonNull
    private static List<Element> getChildElements(@NonNull Element element) {
        List<Element> children = new ArrayList<Element>();
        Node child = element.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                children.add((Element) child);
            }
            child = child.getNextSibling();
        }
        return children;
    }
}