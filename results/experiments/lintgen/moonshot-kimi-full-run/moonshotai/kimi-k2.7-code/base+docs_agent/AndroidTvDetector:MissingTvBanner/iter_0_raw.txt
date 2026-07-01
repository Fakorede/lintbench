package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_INTENT_CATEGORY_LEANBACK_LAUNCHER;
import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_BANNER;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.TAG_APPLICATION;
import static com.android.SdkConstants.TAG_CATEGORY;
import static com.android.SdkConstants.TAG_INTENT_FILTER;

import com.android.annotations.NonNull;
import com.android.resources.FolderConfiguration;
import com.android.resources.LocaleQualifier;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceFolder;
import com.android.tools.lint.detector.api.ResourceFolderContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AndroidTvDetector extends Detector implements Detector.XmlScanner, Detector.ResourceFolderScanner {

    public static final Issue MISSING_BANNER = Issue.create(
            "MissingTvBanner",
            "Missing TV Banner",
            "A TV application that includes a Leanback launcher intent filter must provide a home screen banner for each localization. The banner is the app launch point that appears on the home screen in the apps and games rows.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(AndroidTvDetector.class, EnumSet.of(Scope.MANIFEST_SCOPE, Scope.RESOURCE_FOLDER_SCOPE))
    ).addMoreInfo("https://developer.android.com/training/tv/start/start.html#banner");

    private boolean mHasLeanbackLauncher;
    private Element mApplicationElement;
    private XmlContext mManifestContext;
    private String mBannerType;
    private String mBannerName;

    private final List<FolderInfo> mResourceFolders = new ArrayList<>();

    private static class FolderInfo {
        final ResourceFolderType type;
        final FolderConfiguration configuration;
        final Set<String> baseNames;

        FolderInfo(ResourceFolderType type, FolderConfiguration configuration, Set<String> baseNames) {
            this.type = type;
            this.configuration = configuration;
            this.baseNames = baseNames;
        }
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mHasLeanbackLauncher = false;
        mApplicationElement = null;
        mManifestContext = null;
        mBannerType = null;
        mBannerName = null;
        mResourceFolders.clear();
    }

    @Override
    @NonNull
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_APPLICATION, TAG_INTENT_FILTER);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();
        if (TAG_APPLICATION.equals(tag)) {
            mApplicationElement = element;
            mManifestContext = context;

            String banner = element.getAttributeNS(ANDROID_URI, ATTR_BANNER);
            if (banner != null && !banner.isEmpty()) {
                int slash = banner.indexOf('/');
                if (slash != -1 && banner.startsWith("@")) {
                    mBannerType = banner.substring(1, slash);
                    mBannerName = banner.substring(slash + 1);
                }
            }
        } else if (TAG_INTENT_FILTER.equals(tag)) {
            NodeList children = element.getChildNodes();
            for (int i = 0, n = children.getLength(); i < n; i++) {
                Node child = children.item(i);
                if (child.getNodeType() == Node.ELEMENT_NODE && TAG_CATEGORY.equals(child.getNodeName())) {
                    String name = ((Element) child).getAttributeNS(ANDROID_URI, ATTR_NAME);
                    if (ANDROID_INTENT_CATEGORY_LEANBACK_LAUNCHER.equals(name)) {
                        mHasLeanbackLauncher = true;
                    }
                }
            }
        }
    }

    @Override
    public void checkResourceFolder(@NonNull ResourceFolderContext context) {
        ResourceFolder folder = context.getResourceFolder();
        ResourceFolderType type = folder.getType();
        if (type != ResourceFolderType.DRAWABLE && type != ResourceFolderType.MIPMAP) {
            return;
        }

        FolderConfiguration configuration = folder.getConfiguration();
        Set<String> baseNames = new HashSet<>();
        for (File file : folder.getFiles()) {
            String fileName = file.getName();
            int dot = fileName.lastIndexOf('.');
            String baseName = dot == -1 ? fileName : fileName.substring(0, dot);
            baseNames.add(baseName);
        }

        mResourceFolders.add(new FolderInfo(type, configuration, baseNames));
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (!mHasLeanbackLauncher || mApplicationElement == null || mManifestContext == null) {
            return;
        }

        if (mBannerName == null || mBannerType == null) {
            String message = "TV application is missing the required home screen banner; add android:banner to the application element";
            mManifestContext.report(MISSING_BANNER, mApplicationElement,
                    mManifestContext.getLocation(mApplicationElement), message);
            return;
        }

        boolean defaultBannerFound = false;
        Set<String> locales = new HashSet<>();
        Map<String, Boolean> localeBanners = new HashMap<>();

        for (FolderInfo info : mResourceFolders) {
            if (!mBannerType.equalsIgnoreCase(info.type.getName())) {
                continue;
            }

            String locale = null;
            if (info.configuration != null) {
                LocaleQualifier localeQualifier = info.configuration.getLocaleQualifier();
                if (localeQualifier != null) {
                    locale = localeQualifier.toString();
                }
            }

            boolean found = info.baseNames.contains(mBannerName);

            if (locale == null) {
                if (found) {
                    defaultBannerFound = true;
                }
            } else {
                locales.add(locale);
                if (found) {
                    localeBanners.put(locale, Boolean.TRUE);
                }
            }
        }

        if (!defaultBannerFound) {
            Attr bannerAttr = mApplicationElement.getAttributeNodeNS(ANDROID_URI, ATTR_BANNER);
            Location location = bannerAttr != null
                    ? mManifestContext.getLocation(bannerAttr)
                    : mManifestContext.getLocation(mApplicationElement);
            String message = String.format(
                    "TV banner resource `%1$s/%2$s` was not found in the default drawable/mipmap folder",
                    mBannerType, mBannerName);
            mManifestContext.report(MISSING_BANNER, bannerAttr != null ? bannerAttr : mApplicationElement,
                    location, message);
        }

        for (String locale : locales) {
            if (!localeBanners.containsKey(locale)) {
                String message = String.format(
                        "TV banner resource `%1$s/%2$s` is missing for localization `%3$s`",
                        mBannerType, mBannerName, locale);
                mManifestContext.report(MISSING_BANNER, mApplicationElement,
                        mManifestContext.getLocation(mApplicationElement), message);
            }
        }
    }
}