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
import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Collection;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.xml.AndroidManifest.NODE_USES_FEATURE;

public class AndroidTvDetector extends Detector implements XmlScanner {

    private static final String LEANBACK_FEATURE = "android.software.leanback";
    private static final String LEANBACK_LAUNCHER_CATEGORY = "android.intent.category.LEANBACK_LAUNCHER";
    private static final String NODE_CATEGORY = "category";
    private static final String NODE_MANIFEST = "manifest";

    public static final Issue MISSING_LEANBACK_SUPPORT =
            Issue.create(
                    "MissingLeanbackSupport",
                    "Missing Leanback Support",
                    "The manifest should declare the use of the Leanback user interface "
                            + "required by Android TV.\n\n"
                            + "To fix this, add\n"
                            + "```xml\n"
                            + "`<uses-feature android:name=\"android.software.leanback\"\n"
                            + "               android:required=\"false\" />`\n"
                            + "```\n"
                            + "to your manifest.",
                    Category.CORRECTNESS,
                    5,
                    Severity.ERROR,
                    new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE));

    private boolean mHasLeanbackFeature;
    private boolean mHasLeanbackLauncherCategory;
    private Element mManifestElement;

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(NODE_USES_FEATURE, NODE_CATEGORY, NODE_MANIFEST);
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mHasLeanbackFeature = false;
        mHasLeanbackLauncherCategory = false;
        mManifestElement = null;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();
        if (NODE_USES_FEATURE.equals(tagName)) {
            String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
            if (LEANBACK_FEATURE.equals(name)) {
                mHasLeanbackFeature = true;
            }
        } else if (NODE_CATEGORY.equals(tagName)) {
            String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
            if (LEANBACK_LAUNCHER_CATEGORY.equals(name)) {
                mHasLeanbackLauncherCategory = true;
            }
        } else if (NODE_MANIFEST.equals(tagName)) {
            mManifestElement = element;
        }
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (mHasLeanbackLauncherCategory && !mHasLeanbackFeature) {
            XmlContext xmlContext = (XmlContext) context;
            com.android.tools.lint.detector.api.Location location;
            if (mManifestElement != null) {
                location = xmlContext.getLocation(mManifestElement);
            } else {
                location = com.android.tools.lint.detector.api.Location.create(context.file);
            }
            xmlContext.report(
                    MISSING_LEANBACK_SUPPORT,
                    location,
                    "Manifest should declare a `<uses-feature android:name=\"android.software.leanback\" android:required=\"false\" />` entry");
        }
    }
}