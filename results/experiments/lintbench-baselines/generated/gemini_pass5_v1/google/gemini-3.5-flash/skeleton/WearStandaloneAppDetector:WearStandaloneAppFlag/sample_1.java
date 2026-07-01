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
import java.util.Collection;
import org.w3c.dom.Element;

public class WearStandaloneAppDetector extends Detector implements XmlScanner {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    private static final Implementation IMPLEMENTATION =
            new Implementation(WearStandaloneAppDetector.class, Scope.MANIFEST_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "WearStandaloneAppFlag",
                    "Invalid or missing Wear standalone app flag",
                    "Wearable apps should specify whether they can work standalone, without a phone app. "
                            + "Add a valid meta-data entry for `com.google.android.wearable.standalone` "
                            + "to your application element and set the value to `true` or `false`.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private boolean mHasWatchFeature;
    private Element mApplicationElement;
    private Element mStandaloneMetadata;

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mHasWatchFeature = false;
        mApplicationElement = null;
        mStandaloneMetadata = null;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return java.util.Arrays.asList("uses-feature", "application", "meta-data");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();
        if ("uses-feature".equals(tagName)) {
            String name = getAttribute(element, "name");
            if ("android.hardware.type.watch".equals(name)) {
                mHasWatchFeature = true;
            }
        } else if ("application".equals(tagName)) {
            mApplicationElement = element;
        } else if ("meta-data".equals(tagName)) {
            String name = getAttribute(element, "name");
            if ("com.google.android.wearable.standalone".equals(name)) {
                org.w3c.dom.Node parent = element.getParentNode();
                if (parent instanceof Element && "application".equals(((Element) parent).getTagName())) {
                    mStandaloneMetadata = element;
                }
            }
        }
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (mHasWatchFeature && context instanceof XmlContext) {
            XmlContext xmlContext = (XmlContext) context;
            if (mStandaloneMetadata == null) {
                if (mApplicationElement != null) {
                    xmlContext.report(
                            ISSUE,
                            mApplicationElement,
                            xmlContext.getLocation(mApplicationElement),
                            "Missing Wear standalone app flag");
                }
            } else {
                String value = getAttribute(mStandaloneMetadata, "value");
                if (!"true".equals(value) && !"false".equals(value)) {
                    xmlContext.report(
                            ISSUE,
                            mStandaloneMetadata,
                            xmlContext.getLocation(mStandaloneMetadata),
                            "Expect `true` or `false` for standalone attribute");
                }
            }
        }
    }

    private static String getAttribute(Element element, String localName) {
        if (element.hasAttributeNS(ANDROID_URI, localName)) {
            return element.getAttributeNS(ANDROID_URI, localName);
        }
        return element.getAttribute("android:" + localName);
    }
}