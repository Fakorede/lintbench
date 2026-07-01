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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
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
                    "A TV application must provide a home screen banner for each localization if it includes a Leanback launcher intent filter. The banner is the app launch point that appears on the home screen in the apps and games rows.",
                    Category.CORRECTNESS,
                    7,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private String mAppBanner = null;
    private final List<Element> mLeanbackLaunchers = new ArrayList<>();

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("application", "activity");
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mAppBanner = null;
        mLeanbackLaunchers.clear();
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (context instanceof XmlContext && !mLeanbackLaunchers.isEmpty()) {
            XmlContext xmlContext = (XmlContext) context;
            boolean hasAppBanner = mAppBanner != null && !mAppBanner.isEmpty();
            for (Element activity : mLeanbackLaunchers) {
                boolean hasActivityBanner = activity.hasAttributeNS("http://schemas.android.com/apk/res/android", "banner")
                        || activity.hasAttribute("android:banner");
                if (!hasAppBanner && !hasActivityBanner) {
                    xmlContext.report(
                            ISSUE,
                            activity,
                            xmlContext.getLocation(activity),
                            "A TV application must provide a home screen banner for each localization if it includes a Leanback launcher intent filter."
                    );
                }
            }
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();
        if (tagName.equals("application")) {
            mAppBanner = element.getAttributeNS("http://schemas.android.com/apk/res/android", "banner");
            if (mAppBanner.isEmpty()) {
                mAppBanner = element.getAttribute("android:banner");
                if (mAppBanner.isEmpty()) {
                    mAppBanner = null;
                }
            }
        } else if (tagName.equals("activity")) {
            if (hasLeanbackLauncher(element)) {
                mLeanbackLaunchers.add(element);
            }
        }
    }

    private boolean hasLeanbackLauncher(Element activity) {
        NodeList childNodes = activity.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node child = childNodes.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && child.getNodeName().equals("intent-filter")) {
                Element intentFilter = (Element) child;
                NodeList filterChildren = intentFilter.getChildNodes();
                for (int j = 0; j < filterChildren.getLength(); j++) {
                    Node filterChild = filterChildren.item(j);
                    if (filterChild.getNodeType() == Node.ELEMENT_NODE && filterChild.getNodeName().equals("category")) {
                        Element category = (Element) filterChild;
                        String name = category.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
                        if (name.isEmpty()) {
                            name = category.getAttribute("android:name");
                        }
                        if ("android.intent.category.LEANBACK_LAUNCHER".equals(name)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }
}