package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_BANNER;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.CATEGORY_LEANBACK_LAUNCHER;
import static com.android.SdkConstants.TAG_APPLICATION;
import static com.android.SdkConstants.TAG_CATEGORY;
import static com.android.SdkConstants.TAG_INTENT_FILTER;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.ide.common.resources.configuration.LocaleQualifier;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.ResourceFile;
import com.android.tools.lint.detector.api.ResourceFolder;
import com.android.tools.lint.detector.api.ResourceFolderScanner;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AndroidTvDetector extends Detector implements XmlScanner, ResourceFolderScanner {

    public static final Issue MISSING_TV_BANNER = Issue.create(
            "MissingTvBanner",
            "Missing TV Banner",
            "A TV application that includes a Leanback launcher intent filter must provide a home screen banner for every supported localization. The banner is specified with the `android:banner` attribute on the `<application>` tag and must have a corresponding drawable resource for each locale.",
            Category.TV,
            9,
            Severity.ERROR,
            new Implementation(
                    AndroidTvDetector.class,
                    EnumSet.of(Scope.MANIFEST_SCOPE, Scope.RESOURCE_FOLDER_SCOPE)),
            "https://developer.android.com/training/tv/start/start.html#banner");

    private boolean mHasLeanbackLauncher;
    private String mBannerName;
    private Location mApplicationLocation;
    private Location mBannerLocation;

    private final Set<String> mAllLocales = new HashSet<>();
    private final Map<String, Set<String>> mDrawableNamesByLocale = new HashMap<>();

    @Override
    public void beforeCheckEachProject(@NonNull Context context) {
        mHasLeanbackLauncher = false;
        mBannerName = null;
        mApplicationLocation = null;
        mBannerLocation = null;
        mAllLocales.clear();
        mDrawableNamesByLocale.clear();
    }

    @Override
    @NonNull
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_APPLICATION, TAG_CATEGORY);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();
        if (TAG_APPLICATION.equals(tag)) {
            mApplicationLocation = context.getElementLocation(element);
            Attr banner = element.getAttributeNodeNS(ANDROID_URI, ATTR_BANNER);
            if (banner != null) {
                String name = getDrawableResourceName(banner.getValue());
                if (name != null) {
                    mBannerName = name;
                    mBannerLocation = context.getValueLocation(banner);
                }
            }
        } else if (TAG_CATEGORY.equals(tag)) {
            if (element.getParentNode() != null
                    && TAG_INTENT_FILTER.equals(element.getParentNode().getNodeName())) {
                String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
                if (CATEGORY_LEANBACK_LAUNCHER.equals(name)) {
                    mHasLeanbackLauncher = true;
                }
            }
        }
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return true;
    }

    @Override
    public void checkFolder(@NonNull ResourceContext context, @NonNull ResourceFolder folder) {
        FolderConfiguration configuration = folder.getConfiguration();
        String locale = getLocaleString(configuration);
        if (locale != null) {
            mAllLocales.add(locale);
        }

        if (folder.getType() == ResourceFolderType.DRAWABLE) {
            String key = locale != null ? locale : "";
            Set<String> names = mDrawableNamesByLocale.get(key);
            if (names == null) {
                names = new HashSet<>();
                mDrawableNamesByLocale.put(key, names);
            }

            List<ResourceFile> files = folder.getFiles();
            for (ResourceFile file : files) {
                String fileName = file.getName();
                int dot = fileName.lastIndexOf('.');
                String baseName = dot > 0 ? fileName.substring(0, dot) : fileName;
                names.add(baseName);
            }
        }
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        if (!mHasLeanbackLauncher) {
            return;
        }

        if (mBannerName == null) {
            if (mApplicationLocation != null) {
                context.report(
                        MISSING_TV_BANNER,
                        mApplicationLocation,
                        "TV apps with a Leanback launcher intent filter must specify an "
                                + "android:banner on the application element");
            }
            return;
        }

        if (mAllLocales.isEmpty()) {
            Set<String> defaultNames = mDrawableNamesByLocale.get("");
            if (defaultNames == null || !defaultNames.contains(mBannerName)) {
                context.report(
                        MISSING_TV_BANNER,
                        mBannerLocation,
                        "The TV banner resource `" + mBannerName + "` was not found in the "
                                + "default drawable folder");
            }
            return;
        }

        for (String locale : mAllLocales) {
            Set<String> names = mDrawableNamesByLocale.get(locale);
            if (names == null || !names.contains(mBannerName)) {
                context.report(
                        MISSING_TV_BANNER,
                        mBannerLocation,
                        "Missing TV banner for localization `" + locale + "`; add `drawable-"
                                + locale + "/" + mBannerName + "`");
            }
        }
    }

    @Nullable
    private static String getDrawableResourceName(@Nullable String value) {
        if (value == null) {
            return null;
        }
        if (value.startsWith("@drawable/")) {
            return value.substring("@drawable/".length());
        }
        if (value.startsWith("@android:drawable/")) {
            return value.substring("@android:drawable/".length());
        }
        return null;
    }

    @Nullable
    private static String getLocaleString(@Nullable FolderConfiguration configuration) {
        if (configuration == null) {
            return null;
        }
        LocaleQualifier locale = configuration.getLocaleQualifier();
        if (locale == null || (!locale.hasLanguage() && !locale.hasRegion())) {
            return null;
        }
        return locale.getValue();
    }
}