package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_NAME;
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
import com.android.tools.lint.detector.api.ResourceFolderScanner;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class AndroidTvDetector extends Detector implements XmlScanner, ResourceFolderScanner {

    private static final String ATTR_BANNER = "banner";
    private static final String CATEGORY_LEANBACK_LAUNCHER =
            "android.intent.category.LEANBACK_LAUNCHER";
    private static final String SHORT_CATEGORY_LEANBACK_LAUNCHER = "LEANBACK_LAUNCHER";

    public static final Issue MISSING_TV_BANNER = Issue.create(
            "MissingTvBanner",
            "Missing TV Banner",
            "A TV application that includes a Leanback launcher intent filter must provide a home screen banner for every supported localization. The banner is specified with the `android:banner` attribute on the `<application>` tag and must have a corresponding drawable or mipmap resource for each locale.",
            Category.CORRECTNESS,
            9,
            Severity.ERROR,
            new Implementation(
                    AndroidTvDetector.class,
                    EnumSet.of(Scope.MANIFEST, Scope.RESOURCE_FOLDER)));

    private boolean mHasLeanbackLauncher;
    private boolean mHasBannerAttribute;
    private String mBannerType;
    private String mBannerName;
    private Location mApplicationLocation;
    private Location mBannerLocation;

    private final Set<String> mLocales = new HashSet<>();
    private final Map<String, Set<String>> mDrawableNamesByLocale = new HashMap<>();
    private final Map<String, Set<String>> mMipmapNamesByLocale = new HashMap<>();

    @Override
    public void beforeCheckEachProject(@NonNull Context context) {
        mHasLeanbackLauncher = false;
        mHasBannerAttribute = false;
        mBannerType = null;
        mBannerName = null;
        mApplicationLocation = null;
        mBannerLocation = null;
        mLocales.clear();
        mDrawableNamesByLocale.clear();
        mMipmapNamesByLocale.clear();
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
                mHasBannerAttribute = true;
                mBannerLocation = context.getValueLocation(banner);
                String[] ref = parseResourceReference(banner.getValue());
                if (ref != null) {
                    mBannerType = ref[0];
                    mBannerName = ref[1];
                }
            }
        } else if (TAG_CATEGORY.equals(tag)) {
            if (element.getParentNode() != null
                    && TAG_INTENT_FILTER.equals(element.getParentNode().getLocalName())) {
                String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
                if (CATEGORY_LEANBACK_LAUNCHER.equals(name)
                        || SHORT_CATEGORY_LEANBACK_LAUNCHER.equals(name)) {
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
    public void checkFolder(@NonNull ResourceContext context, @NonNull String folderName) {
        ResourceFolderType folderType = ResourceFolderType.getFolderType(folderName);
        if (folderType == null) {
            return;
        }

        FolderConfiguration configuration = FolderConfiguration.getConfigForFolder(folderName);
        String localeKey = getLocaleKey(configuration);
        if (localeKey != null) {
            mLocales.add(localeKey);
        }
        String key = localeKey != null ? localeKey : "";

        Map<String, Set<String>> targetMap = null;
        if (folderType == ResourceFolderType.DRAWABLE) {
            targetMap = mDrawableNamesByLocale;
        } else if (folderType == ResourceFolderType.MIPMAP) {
            targetMap = mMipmapNamesByLocale;
        }

        if (targetMap == null) {
            return;
        }

        Set<String> names = targetMap.get(key);
        if (names == null) {
            names = new HashSet<>();
            targetMap.put(key, names);
        }

        File[] files = context.getResourceFolder().listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (!file.isFile()) {
                continue;
            }
            names.add(getBaseName(file.getName()));
        }
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        if (!mHasLeanbackLauncher) {
            return;
        }

        if (!mHasBannerAttribute) {
            if (mApplicationLocation != null) {
                context.report(
                        MISSING_TV_BANNER,
                        mApplicationLocation,
                        "TV apps with a Leanback launcher intent filter must specify an "
                                + "android:banner attribute on the application element");
            }
            return;
        }

        if (mBannerName == null || mBannerType == null) {
            return;
        }

        Map<String, Set<String>> namesByLocale;
        if ("mipmap".equals(mBannerType)) {
            namesByLocale = mMipmapNamesByLocale;
        } else {
            namesByLocale = mDrawableNamesByLocale;
        }

        Set<String> defaultNames = namesByLocale.get("");
        if (defaultNames == null || !defaultNames.contains(mBannerName)) {
            context.report(
                    MISSING_TV_BANNER,
                    mBannerLocation != null ? mBannerLocation : mApplicationLocation,
                    "The TV banner resource `" + mBannerName + "` was not found in the default "
                            + mBannerType + " folder");
            return;
        }

        for (String locale : mLocales) {
            Set<String> names = namesByLocale.get(locale);
            if (names == null || !names.contains(mBannerName)) {
                context.report(
                        MISSING_TV_BANNER,
                        mBannerLocation != null ? mBannerLocation : mApplicationLocation,
                        "Missing TV banner for localization `" + locale + "`; add `"
                                + mBannerType + "-" + locale + "/" + mBannerName + "`");
            }
        }
    }

    @Nullable
    private static String[] parseResourceReference(@Nullable String value) {
        if (value == null) {
            return null;
        }
        if (value.startsWith("@drawable/")) {
            return new String[] {"drawable", value.substring("@drawable/".length())};
        }
        if (value.startsWith("@mipmap/")) {
            return new String[] {"mipmap", value.substring("@mipmap/".length())};
        }
        return null;
    }

    @Nullable
    private static String getLocaleKey(@Nullable FolderConfiguration configuration) {
        if (configuration == null) {
            return null;
        }
        LocaleQualifier locale = configuration.getLocaleQualifier();
        if (locale == null || !locale.hasLanguage()) {
            return null;
        }
        StringBuilder sb = new StringBuilder(locale.getLanguage());
        if (locale.hasRegion()) {
            sb.append("-r").append(locale.getRegion());
        }
        return sb.toString();
    }

    @NonNull
    private static String getBaseName(@NonNull String fileName) {
        if (fileName.endsWith(".9.png")) {
            return fileName.substring(0, fileName.length() - ".9.png".length());
        }
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }
}