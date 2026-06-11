package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.SdkConstants;
import com.android.resources.Density;
import com.android.resources.ScreenSize;
import com.android.resources.ScreenOrientation;
import com.android.resources.ScreenRound;
import com.android.utils.Pair;
import com.android.utils.XmlUtils;
import com.android.utils.ILogger;
import com.android.utils.PositionXmlParser;
import com.android.utils.StringHelper;
import com.android.utils.XmlPullAttributes;
import com.google.common.collect.ImmutableList;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlSerializer;

import java.io.IOException;
import java.util.List;

public class AndroidTvDetector extends Detector implements Detector.XmlScanner {
    private static final String FEATURE_TOUCHSCREEN = "android.hardware.touchscreen";
    
    public static final Issue ISSUE = Issue.create(
            "TouchscreenNotOptional",
            "Apps require the `android.hardware.touchscreen` feature by default. If you want your app to be available on TV, you must explicitly declare that a touchscreen is not required.",
            "If your application does not require a touchscreen and should be available on devices such as Android TV, you need to explicitly set `android:required=\"false\"` for the `android.hardware.touchscreen` feature in your manifest file.",
            Category.USABILITY,
            6,
            Severity.WARNING,
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE)
    );

    @Nullable
    @Override
    public List<String> getApplicableElements() {
        return ImmutableList.of("uses-feature");
    }

    @NonNull
    @Override
    public List<XmlIssue> checkTag(@NonNull PositionXmlParser xml,
                                   @NonNull XmlContext context) {
        if (FEATURE_TOUCHSCREEN.equals(xml.getAttributeValue(null, "name"))) {
            String required = xml.getAttributeValue(null, "required");
            if (required == null || Boolean.parseBoolean(required)) {
                return ImmutableList.of(new XmlIssue(ISSUE, context.getDriver().getMainProject(), xml.getLine(),
                        xml.getColumn(), "The touchscreen feature is required by default. Set android:required=\"false\" to make your app available on TV."));
            }
        }
        return ImmutableList.of();
    }
}