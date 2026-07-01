package com.android.tools.lint.checks;

import com.android.SdkConstants;
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
import java.util.Arrays;
import java.util.Collection;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class AndroidTvDetector extends Detector implements XmlScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "MissingTvBanner",
                    "TV Missing Banner",
                    "A TV application must provide a home screen banner for each localization "
                            + "when it includes a Leanback launcher intent filter. The banner is "
                            + "the app launch point that appears on the home screen. Declare it "
                            + "with the `android:banner` attribute on the `<application>` element.",
                    Category.CORRECTNESS,
                    7,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private boolean mIsManifest;
    private Element mApplicationElement;
    private boolean mApplicationHasBanner;
    private boolean mHasLeanbackLauncher;

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(SdkConstants.TAG_APPLICATION, SdkConstants.TAG_INTENT_FILTER);
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mIsManifest = SdkConstants.ANDROID_MANIFEST_XML.equals(context.file.getName());
        if (!mIsManifest) {
            return;
        }

        mApplicationElement = null;
        mApplicationHasBanner = false;
        mHasLeanbackLauncher = false;
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (!mIsManifest || !mHasLeanbackLauncher || mApplicationHasBanner) {
            return;
        }

        if (mApplicationElement != null) {
            XmlContext xmlContext = (XmlContext) context;
            xmlContext.report(
                    ISSUE,
                    mApplicationElement,
                    xmlContext.getLocation(mApplicationElement),
                    "You must provide a TV banner for the application; add the "
                            + "`android:banner` attribute to the `<application>` element.");
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (!mIsManifest) {
            return;
        }

        String tag = element.getTagName();
        if (SdkConstants.TAG_APPLICATION.equals(tag)) {
            mApplicationElement = element;
            String banner = getAttributeValue(element, SdkConstants.ATTR_BANNER);
            if (!banner.isEmpty()) {
                mApplicationHasBanner = true;
            }
        } else if (SdkConstants.TAG_INTENT_FILTER.equals(tag)) {
            boolean hasMain = false;
            boolean hasLeanback = false;

            NodeList children = element.getChildNodes();
            for (int i = 0, n = children.getLength(); i < n; i++) {
                Node child = children.item(i);
                if (child.getNodeType() != Node.ELEMENT_NODE) {
                    continue;
                }

                Element childElement = (Element) child;
                String childTag = childElement.getTagName();
                String name = getAttributeValue(childElement, SdkConstants.ATTR_NAME);

                if (SdkConstants.TAG_ACTION.equals(childTag)
                        && SdkConstants.ACTION_MAIN.equals(name)) {
                    hasMain = true;
                } else if (SdkConstants.TAG_CATEGORY.equals(childTag)
                        && SdkConstants.CATEGORY_LEANBACK_LAUNCHER.equals(name)) {
                    hasLeanback = true;
                }
            }

            if (hasMain && hasLeanback) {
                mHasLeanbackLauncher = true;
            }
        }
    }

    private static String getAttributeValue(@NonNull Element element, @NonNull String localName) {
        String value = element.getAttributeNS(SdkConstants.ANDROID_URI, localName);
        if (value.isEmpty()) {
            value = element.getAttribute(SdkConstants.PREFIX_ANDROID + localName);
        }
        return value;
    }
}