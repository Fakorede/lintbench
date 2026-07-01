package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Element;

import java.util.Collections;
import java.util.List;

public class AndroidTvDetector extends Detector implements Detector.XmlScanner {

    public static final Issue MISSING_LEANBACK_SUPPORT = Issue.create(
            "MissingLeanbackSupport",
            "Missing Leanback Support",
            "Android TV apps should declare the Leanback user interface feature in the manifest. "
                    + "Add `<uses-feature android:name=\"android.software.leanback\" "
                    + "android:required=\"false\" />` to your AndroidManifest.xml.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE)
    );

    private static final String FEATURE_LEANBACK = "android.software.leanback";

    private boolean mHasLeanbackFeature;

    @Override
    public List<String> getApplicableElements() {
        return Collections.singletonList(SdkConstants.TAG_USES_FEATURE);
    }

    @Override
    public void beforeCheckFile(Context context) {
        mHasLeanbackFeature = false;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String name = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
        if (FEATURE_LEANBACK.equals(name)) {
            mHasLeanbackFeature = true;
        }
    }

    @Override
    public void afterCheckFile(Context context) {
        if (!mHasLeanbackFeature
                && context.getFile().getName().equals(SdkConstants.ANDROID_MANIFEST_XML)) {
            context.report(
                    MISSING_LEANBACK_SUPPORT,
                    Location.create(context.getFile()),
                    "Missing `android.software.leanback` uses-feature declaration"
            );
        }
    }
}