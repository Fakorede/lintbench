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

public class AndroidTvDetector extends Detector implements Detector.XmlScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE);

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String LEANBACK_FEATURE = "android.software.leanback";

    public static final Issue ISSUE =
            Issue.create(
                    "MissingLeanbackSupport",
                    "Missing Leanback Support",
                    "The manifest should declare the use of the Leanback user interface required by Android TV.\n\n"
                    + "To fix this, add\n"
                    + "`<uses-feature android:name=\"android.software.leanback\"\n"
                    + "                android:required=\"false\" />`\n"
                    + "to your manifest.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private boolean mHasLeanback;

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("uses-feature");
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mHasLeanback = false;
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (!mHasLeanback) {
            XmlContext xmlContext = (XmlContext) context;
            Element root = xmlContext.document.getDocumentElement();
            if (root != null) {
                xmlContext.report(ISSUE, root, xmlContext.getLocation(root),
                        "The manifest should declare the use of the Leanback user interface required by Android TV.");
            }
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttributeNS(ANDROID_URI, "name");
        if (LEANBACK_FEATURE.equals(name)) {
            mHasLeanback = true;
        }
    }
}