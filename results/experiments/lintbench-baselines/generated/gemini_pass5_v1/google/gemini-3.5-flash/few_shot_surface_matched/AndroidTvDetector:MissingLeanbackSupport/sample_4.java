package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.w3c.dom.Element;
import java.util.Arrays;
import java.util.Collection;

public class AndroidTvDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "MissingLeanbackSupport",
                    "Missing Leanback Support",
                    "The manifest should declare the use of the Leanback user interface "
                            + "required by Android TV. To fix this, add "
                            + "`<uses-feature android:name=\"android.software.leanback\" "
                            + "android:required=\"false\" />` to your manifest.",
                    Category.CORRECTNESS,
                    5,
                    Severity.ERROR,
                    new Implementation(
                            AndroidTvDetector.class, Scope.MANIFEST_SCOPE));

    private boolean mHasLeanbackFeature = false;
    private boolean mHasLeanbackLauncher = false;
    private Element mLauncherElement = null;

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("uses-feature", "category");
    }

    @Override
    public void beforeCheckFile(Context context) {
        mHasLeanbackFeature = false;
        mHasLeanbackLauncher = false;
        mLauncherElement = null;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String tagName = element.getTagName();
        if ("uses-feature".equals(tagName)) {
            String name = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
            if ("android.software.leanback".equals(name)) {
                mHasLeanbackFeature = true;
            }
        } else if ("category".equals(tagName)) {
            String name = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
            if ("android.intent.category.LEANBACK_LAUNCHER".equals(name)) {
                mHasLeanbackLauncher = true;
                mLauncherElement = element;
            }
        }
    }

    @Override
    public void afterCheckFile(Context context) {
        if (mHasLeanbackLauncher && !mHasLeanbackFeature && context instanceof XmlContext) {
            XmlContext xmlContext = (XmlContext) context;
            xmlContext.report(
                    ISSUE,
                    mLauncherElement,
                    xmlContext.getLocation(mLauncherElement),
                    "The manifest should declare the use of the Leanback user interface "
                            + "required by Android TV. To fix this, add "
                            + "`<uses-feature android:name=\"android.software.leanback\" "
                            + "android:required=\"false\" />` to your manifest.");
        }
    }
}