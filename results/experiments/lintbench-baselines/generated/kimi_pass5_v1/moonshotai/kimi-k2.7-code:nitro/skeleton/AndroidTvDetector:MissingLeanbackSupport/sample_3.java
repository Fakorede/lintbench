package com.android.tools.lint.checks;

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
import java.util.Arrays;
import java.util.Collection;
import org.w3c.dom.Element;

public class AndroidTvDetector extends Detector implements Detector.XmlScanner {

    private static final String ANDROID_MANIFEST_XML = "AndroidManifest.xml";
    private static final String USES_FEATURE = "uses-feature";
    private static final String ATTR_NAME = "android:name";
    private static final String ATTR_REQUIRED = "android:required";
    private static final String LEANBACK = "android.software.leanback";

    private static final String EXPLANATION =
            "When an app supports Android TV, the manifest must declare the use of the "
                    + "Leanback user interface. Add "
                    + "`<uses-feature android:name=\"android.software.leanback\" "
                    + "android:required=\"false\" />` to the manifest.";

    private static final Implementation IMPLEMENTATION =
            new Implementation(AndroidTvDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "MissingLeanbackSupport",
                    "Missing Leanback Support",
                    EXPLANATION,
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private boolean mIsManifest;
    private boolean mHasLeanbackFeature;

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(USES_FEATURE);
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mIsManifest = ANDROID_MANIFEST_XML.equals(context.file.getName());
        mHasLeanbackFeature = false;
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (!mIsManifest || mHasLeanbackFeature || !(context instanceof XmlContext)) {
            return;
        }

        XmlContext xmlContext = (XmlContext) context;
        Element root = xmlContext.document.getDocumentElement();
        if (root == null) {
            return;
        }

        Location location = xmlContext.getLocation(root);
        xmlContext.report(
                ISSUE,
                location,
                "Missing Leanback support: add `<uses-feature android:name=\"android.software.leanback\" android:required=\"false\" />` to the manifest.");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (!mIsManifest) {
            return;
        }

        if (USES_FEATURE.equals(element.getTagName())
                && LEANBACK.equals(element.getAttribute(ATTR_NAME))
                && "false".equals(element.getAttribute(ATTR_REQUIRED))) {
            mHasLeanbackFeature = true;
        }
    }
}