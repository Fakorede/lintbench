package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_BANNER;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.xml.AndroidManifest.NODE_ACTION;
import static com.android.xml.AndroidManifest.NODE_APPLICATION;
import static com.android.xml.AndroidManifest.NODE_CATEGORY;
import static com.android.xml.AndroidManifest.NODE_INTENT_FILTER;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
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

    public static final Issue ISSUE =
            Issue.create(
                    "MissingTvBanner",
                    "TV App Missing Banner",
                    "A TV application must provide a home screen banner for each localization if"
                            + " it includes a Leanback launcher intent filter. The banner is the"
                            + " app launch point that appears on the home screen in the apps and"
                            + " games rows. Add the `android:banner` attribute to the"
                            + " `<application>` element.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE));

    private boolean mHasLeanbackLauncher;
    private boolean mHasBanner;
    @Nullable private Element mApplicationElement;

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(NODE_APPLICATION, NODE_INTENT_FILTER);
    }

    @Override
    public void beforeCheckFile(@NonNull XmlContext context) {
        mHasLeanbackLauncher = false;
        mHasBanner = false;
        mApplicationElement = null;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();
        if (NODE_APPLICATION.equals(tag)) {
            mApplicationElement = element;
            String banner = element.getAttributeNS(ANDROID_URI, ATTR_BANNER);
            if (banner != null && !banner.isEmpty()) {
                mHasBanner = true;
            }
        } else if (NODE_INTENT_FILTER.equals(tag)) {
            if (isLeanbackLauncherIntentFilter(element)) {
                mHasLeanbackLauncher = true;
            }
        }
    }

    @Override
    public void afterCheckFile(@NonNull XmlContext context) {
        if (mHasLeanbackLauncher && !mHasBanner && mApplicationElement != null) {
            context.report(
                    ISSUE,
                    mApplicationElement,
                    context.getLocation(mApplicationElement),
                    "Missing home screen banner for a TV application with a Leanback launcher"
                            + " intent filter");
        }
    }

    private static boolean isLeanbackLauncherIntentFilter(@NonNull Element intentFilter) {
        boolean hasMainAction = false;
        boolean hasLeanbackLauncher = false;
        NodeList children = intentFilter.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element childElement = (Element) child;
            String tag = childElement.getTagName();
            String name = childElement.getAttributeNS(ANDROID_URI, ATTR_NAME);
            if (NODE_ACTION.equals(tag) && "android.intent.action.MAIN".equals(name)) {
                hasMainAction = true;
            } else if (NODE_CATEGORY.equals(tag)
                    && "android.intent.category.LEANBACK_LAUNCHER".equals(name)) {
                hasLeanbackLauncher = true;
            }
        }
        return hasMainAction && hasLeanbackLauncher;
    }
}