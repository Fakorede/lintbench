package com.android.tools.lint.checks;

import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.NS_RESOURCES;
import static com.android.xml.AndroidManifest.NODE_USES_FEATURE;

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
import java.util.Collections;
import org.w3c.dom.Element;

public class AndroidTvDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "MissingLeanbackSupport",
                    "Missing Leanback Support",
                    "Apps that run on Android TV must declare the Leanback feature in the "
                            + "manifest. Add `<uses-feature "
                            + "android:name=\"android.software.leanback\" "
                            + "android:required=\"false\" />` to the manifest so the app can be "
                            + "distributed to Android TV devices.",
                    Category.CORRECTNESS,
                    5,
                    Severity.ERROR,
                    new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE));

    private boolean mHasLeanbackFeature;

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(NODE_USES_FEATURE);
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mHasLeanbackFeature = false;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttributeNS(NS_RESOURCES, ATTR_NAME);
        if ("android.software.leanback".equals(name)) {
            mHasLeanbackFeature = true;
        }
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (!mHasLeanbackFeature) {
            XmlContext xmlContext = (XmlContext) context;
            Element root = xmlContext.getDocument().getDocumentElement();
            xmlContext.report(
                    ISSUE,
                    root,
                    xmlContext.getLocation(root),
                    "The manifest is missing the required Leanback feature declaration");
        }
    }
}