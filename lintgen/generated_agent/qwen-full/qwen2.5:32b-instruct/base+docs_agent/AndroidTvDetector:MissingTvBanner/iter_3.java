package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceType;
import com.android.utils.Pair;
import com.android.utils.XmlUtils;
import com.android.utils.PathUtils;
import com.android.utils.StringHelper;
import com.android.utils.XmlPullAttributesImpl;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import com.android.SdkConstants;
import com.android.resources.ResourceType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

public class AndroidTvDetector extends Detector implements Detector.XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "MissingHomeScreenBanner",
            "A TV application must provide a home screen banner for each localization if it includes a Leanback launcher intent filter.",
            "The banner is the app launch point that appears on the home screen in the apps and games rows. " +
                    "If your app does not include this, users will not be able to find your app easily.",
            Category.USABILITY,
            6,
            Severity.ERROR,
            new Implementation(
                    AndroidTvDetector.class,
                    Scope.MANIFEST_SCOPE
            )
    );

    @NonNull
    @Override
    public List<String> getApplicableElements() {
        return XmlScanner.ALL_ELEMENTS;
    }

    @Nullable
    @Override
    public List<XmlIssue> checkTag(@NonNull JavaContext context, @NonNull Location location, @NonNull String tag,
                                   @NonNull XmlPullAttributes attributes) {

        if ("activity".equals(tag)) {
            boolean hasLeanbackLauncher = false;

            for (int i = 0; i < attributes.getLength(); i++) {
                String name = attributes.getNamespace(i);
                if (name != null && name.equals(SdkConstants.ANDROID_URI)) {
                    name = attributes.getName(i);

                    if ("intent-filter".equals(name)) {
                        hasLeanbackLauncher |= checkIntentFilter(context, location, attributes);
                    }
                }
            }

            if (hasLeanbackLauncher) {
                boolean bannerExists = checkForBanner(context.getDriver().getProject(), context.getProject());
                if (!bannerExists) {
                    return List.of(new XmlIssue(ISSUE,
                            "A TV application must provide a home screen banner for each localization",
                            location));
                }
            }
        }

        return null;
    }

    private boolean checkIntentFilter(@NonNull JavaContext context, @NonNull Location location, @NonNull XmlPullAttributes attributes) {
        for (int i = 0; i < attributes.getLength(); i++) {
            String name = attributes.getNamespace(i);
            if (name != null && name.equals(SdkConstants.ANDROID_URI)) {
                name = attributes.getName(i);

                if ("action".equals(name)) {
                    String actionName = attributes.getAttributeValue(i);
                    if (SdkConstants.ACTIVITY_ACTION_MAIN.equals(actionName) ||
                            SdkConstants.CATEGORY_LEANBACK_LAUNCHER.equals(actionName)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private boolean checkForBanner(@NonNull com.android.tools.lint.detector.api.Project project, @NonNull JavaContext context) {
        try {
            for (String density : new String[]{"mdpi", "hdpi", "xhdpi", "xxhdpi"}) {
                if (!project.getResourceManager().getResValues(ResourceType.DRAWABLE).containsKey("ic_homescreen_" + density)) {
                    return false;
                }
            }
        } catch (IOException e) {
            context.getDriver().reportError(e);
            return false;
        }

        return true;
    }
}