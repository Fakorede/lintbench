package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Element;

public class AndroidTvDetector extends Detector implements Detector.XmlScanner {

    private static final String TAG_USES_FEATURE = "uses-feature";
    private static final String ATTR_NAME = "name";
    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String LEANBACK_FEATURE = "android.software.leanback";

    private static final Implementation IMPLEMENTATION =
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "MissingLeanbackSupport",
                    "Missing Leanback Support",
                    "The manifest should declare the use of the Leanback user interface required by Android TV. "
                            + "To fix this, add `<uses-feature android:name=\"android.software.leanback\" "
                            + "android:required=\"false\" />` to the manifest.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private boolean mHasLeanbackFeature;

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_USES_FEATURE);
    }

    @Override
    public void beforeCheckFile(Context context) {
        mHasLeanbackFeature = false;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (LEANBACK_FEATURE.equals(element.getAttributeNS(ANDROID_URI, ATTR_NAME))) {
            mHasLeanbackFeature = true;
        }
    }

    @Override
    public void afterCheckFile(Context context) {
        if (!mHasLeanbackFeature && context instanceof XmlContext) {
            XmlContext xmlContext = (XmlContext) context;
            Element root = xmlContext.document.getDocumentElement();
            Location location =
                    root != null
                            ? xmlContext.getElementLocation(root)
                            : Location.create(xmlContext.file);
            xmlContext.report(
                    ISSUE,
                    location,
                    "Missing Leanback Support: Add `<uses-feature android:name=\"android.software.leanback\" "
                            + "android:required=\"false\" />` to the manifest.");
        }
    }
}