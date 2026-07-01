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
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class WearStandaloneAppDetector extends Detector implements Detector.XmlScanner {

    private static final String ANDROID_MANIFEST_XML = "AndroidManifest.xml";
    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String TAG_APPLICATION = "application";
    private static final String TAG_USES_FEATURE = "uses-feature";
    private static final String TAG_META_DATA = "meta-data";
    private static final String ATTR_NAME = "name";
    private static final String ATTR_VALUE = "value";
    private static final String STANDALONE_FLAG = "com.google.android.wearable.standalone";
    private static final String WATCH_FEATURE = "android.hardware.type.watch";

    private static final Implementation IMPLEMENTATION =
            new Implementation(WearStandaloneAppDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "WearStandaloneAppFlag",
                    "Invalid or missing Wear standalone app flag",
                    "Wearable apps must declare whether they can work standalone by adding a "
                            + "<meta-data> element with android:name=\""
                            + STANDALONE_FLAG
                            + "\" and android:value=\"true\" or \"false\" to the "
                            + "<application> element.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private boolean mIsWearableApp;
    private boolean mSeenApplication;
    private Element mApplicationElement;
    private boolean mHasStandaloneFlag;
    private boolean mValidStandaloneFlag;

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mIsWearableApp = false;
        mSeenApplication = false;
        mApplicationElement = null;
        mHasStandaloneFlag = false;
        mValidStandaloneFlag = false;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_APPLICATION, TAG_USES_FEATURE);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (!ANDROID_MANIFEST_XML.equals(context.file.getName())) {
            return;
        }

        String tag = element.getTagName();
        if (TAG_USES_FEATURE.equals(tag)) {
            String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
            if (WATCH_FEATURE.equals(name)) {
                mIsWearableApp = true;
            }
        } else if (TAG_APPLICATION.equals(tag)) {
            mSeenApplication = true;
            mApplicationElement = element;

            NodeList children = element.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node child = children.item(i);
                if (child.getNodeType() == Node.ELEMENT_NODE
                        && TAG_META_DATA.equals(child.getLocalName())) {
                    Element metaData = (Element) child;
                    String name = metaData.getAttributeNS(ANDROID_URI, ATTR_NAME);
                    if (STANDALONE_FLAG.equals(name)) {
                        mHasStandaloneFlag = true;
                        String value = metaData.getAttributeNS(ANDROID_URI, ATTR_VALUE);
                        if ("true".equals(value) || "false".equals(value)) {
                            mValidStandaloneFlag = true;
                        }
                        break;
                    }
                }
            }
        }
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (!mIsWearableApp || !mSeenApplication || mValidStandaloneFlag) {
            return;
        }

        XmlContext xmlContext = (XmlContext) context;
        String message =
                mHasStandaloneFlag
                        ? "The <meta-data> element for "
                                + STANDALONE_FLAG
                                + " must have a value of \"true\" or \"false\"."
                        : "Wearable apps must declare a <meta-data> element for "
                                + STANDALONE_FLAG
                                + " with a value of \"true\" or \"false\" on the "
                                + "<application> element.";

        xmlContext.report(
                ISSUE,
                mApplicationElement,
                xmlContext.getLocation(mApplicationElement),
                message);
    }
}