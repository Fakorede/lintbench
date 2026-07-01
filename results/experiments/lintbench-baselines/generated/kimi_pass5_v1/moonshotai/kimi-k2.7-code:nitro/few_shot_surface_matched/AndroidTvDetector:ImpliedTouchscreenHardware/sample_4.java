package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_REQUIRED;
import static com.android.xml.AndroidManifest.CATEGORY_LEANBACK_LAUNCHER;
import static com.android.xml.AndroidManifest.NODE_ACTIVITY;
import static com.android.xml.AndroidManifest.NODE_ACTIVITY_ALIAS;
import static com.android.xml.AndroidManifest.NODE_CATEGORY;
import static com.android.xml.AndroidManifest.NODE_INTENT_FILTER;
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
import java.util.Arrays;
import java.util.Collection;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class AndroidTvDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "ImpliedTouchscreenHardware",
                    "TV application must declare that a touchscreen is not required",
                    "Apps require the `android.hardware.touchscreen` feature by default. If you"
                            + " want your app to be available on TV, you must also explicitly"
                            + " declare that a touchscreen is not required by adding"
                            + " `<uses-feature android:name=\"android.hardware.touchscreen\""
                            + " android:required=\"false\"/>` to the manifest.",
                    Category.CORRECTNESS,
                    5,
                    Severity.ERROR,
                    new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE));

    private boolean mTouchscreenOptional;
    private Element mLeanbackElement;

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(NODE_USES_FEATURE, NODE_CATEGORY);
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mTouchscreenOptional = false;
        mLeanbackElement = null;
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (mLeanbackElement != null && !mTouchscreenOptional) {
            context.report(
                    ISSUE,
                    mLeanbackElement,
                    context.getLocation(mLeanbackElement),
                    "Touchscreen hardware is implicitly required for this TV application; add"
                            + " `<uses-feature android:name=\"android.hardware.touchscreen\""
                            + " android:required=\"false\"/>` to the manifest.");
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();
        if (NODE_USES_FEATURE.equals(tag)) {
            String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
            String required = element.getAttributeNS(ANDROID_URI, ATTR_REQUIRED);
            if ("android.hardware.touchscreen".equals(name) && "false".equals(required)) {
                mTouchscreenOptional = true;
            }
        } else if (NODE_CATEGORY.equals(tag)) {
            String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
            if (CATEGORY_LEANBACK_LAUNCHER.equals(name)) {
                Node parent = element.getParentNode();
                if (parent != null && NODE_INTENT_FILTER.equals(parent.getNodeName())) {
                    Node grandparent = parent.getParentNode();
                    if (grandparent != null) {
                        String grandparentName = grandparent.getNodeName();
                        if (NODE_ACTIVITY.equals(grandparentName)
                                || NODE_ACTIVITY_ALIAS.equals(grandparentName)) {
                            mLeanbackElement = (Element) grandparent;
                        }
                    }
                }
            }
        }
    }
}