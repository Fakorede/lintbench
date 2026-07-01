package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Arrays;
import java.util.Collection;

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
    private static final String LEANBACK_LAUNCHER = "android.intent.category.LEANBACK_LAUNCHER";

    private boolean mHasLeanbackFeature;
    private boolean mHasLeanbackLauncher;

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(SdkConstants.TAG_USES_FEATURE, SdkConstants.TAG_INTENT_FILTER);
    }

    @Override
    public void beforeCheckFile(Context context) {
        mHasLeanbackFeature = false;
        mHasLeanbackLauncher = false;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String tag = element.getTagName();
        if (SdkConstants.TAG_USES_FEATURE.equals(tag)) {
            String name = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
            if (FEATURE_LEANBACK.equals(name)) {
                mHasLeanbackFeature = true;
            }
        } else if (SdkConstants.TAG_INTENT_FILTER.equals(tag)) {
            NodeList categories = element.getElementsByTagName(SdkConstants.TAG_CATEGORY);
            for (int i = 0; i < categories.getLength(); i++) {
                Element category = (Element) categories.item(i);
                String name = category.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
                if (LEANBACK_LAUNCHER.equals(name)) {
                    mHasLeanbackLauncher = true;
                    break;
                }
            }
        }
    }

    @Override
    public void afterCheckFile(Context context) {
        if (mHasLeanbackLauncher && !mHasLeanbackFeature) {
            XmlContext xmlContext = (XmlContext) context;
            context.report(
                    MISSING_LEANBACK_SUPPORT,
                    Location.create(xmlContext.file),
                    "Missing `android.software.leanback` uses-feature declaration"
            );
        }
    }
}