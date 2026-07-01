package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.TAG_APPLICATION;
import static com.android.SdkConstants.TAG_CATEGORY;
import static com.android.SdkConstants.TAG_INTENT_FILTER;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceContext;
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
import java.util.regex.Pattern;

public class AndroidTvDetector extends Detector implements Detector.XmlScanner, Detector.ResourceFolderScanner {

    private static final String ATTR_BANNER = "banner";
    private static final String CATEGORY_LEANBACK_LAUNCHER = "android.intent.category.LEANBACK_LAUNCHER";

    private static final EnumSet<Scope> SCOPES = EnumSet.copyOf(Scope.MANIFEST_SCOPE);
    static {
        SCOPES.addAll(Scope.RESOURCE_FOLDER_SCOPE);
    }

    public static final Issue MISSING_BANNER = Issue.create(
            "MissingTvBanner",
            "Missing TV Banner",
            "A TV application that includes a Leanback launcher intent filter must provide a home screen banner for each localization. The banner is the app launch point that appears on the home screen in the apps and games rows.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(AndroidTvDetector.class, SCOPES)
    ).addMoreInfo("https://developer.android.com/training/tv/start/start.html#banner");

    private boolean mHasLeanbackLauncher;
    private Element mApplicationElement;
    private XmlContext mManifestContext;
    private final List<FolderInfo> mResourceFolders = new ArrayList<>();

    private static class FolderInfo {
        final ResourceFolderType type;
        final String locale;
        final Set<String> baseNames;

        FolderInfo(ResourceFolderType type, String locale, Set<String> baseNames) {
            this.type = type;
            this.locale = locale;
            this.baseNames = baseNames;
        }
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mHasLeanbackLauncher = false;
        mApplicationElement = null;
        mManifestContext = null;
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
        } else if (TAG_INTENT_FILTER.equals(tag)) {
            NodeList children = element.getChildNodes();
            for (int i = 0, n = children.getLength(); i < n; i++) {
                Node child = children.item(i);
                if (child.getNodeType() == Node.ELEMENT_NODE && TAG_CATEGORY.equals(child.getNodeName())) {
                    String name = ((Element) child).getAttributeNS(ANDROID_URI, ATTR_NAME);
                    if (CATEGORY_LEANBACK_LAUNCHER.equals(name) || "LEANBACK_LAUNCHER".equals(name)) {
                        mHasLeanbackLauncher = true;
                    }
                }
            }
        }
    }

    @Override
    public void checkResourceFolder(@NonNull ResourceContext context) {
        ResourceFolderType type = context.getResourceFolderType();
        if (type != ResourceFolderType.DRAWABLE && type != ResourceFolderType.MIPMAP) {
            return;
        }

        File folder = context.getFolder();
        if (folder == null) {
            return;
        }

        String folderName = folder.getName();
        String typeName = type.getName();
        String qualifiers = "";
        if (folderName.startsWith(typeName)) {
            qualifiers = folderName.substring(typeName.length());
            if (qualifiers.startsWith("-")) {
                qualifiers = qualifiers.substring(1);
            }
        }

        String locale = getLocaleQualifier(qualifiers);

        Set<String> baseNames = new HashSet<>();
        File[] files = folder.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    String fileName = file.getName();
                    int dot = fileName.lastIndexOf('.');
                    String baseName = dot == -1 ? fileName : fileName.substring(0, dot);
                    baseNames.add(baseName);
                }
            }
        }

        mResourceFolders.add(new FolderInfo(type, locale, baseNames));
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (!mHasLeanbackLauncher || mApplicationElement == null || mManifestContext == null) {
            return;
        }

        String banner = mApplicationElement.getAttributeNS(ANDROID_URI, ATTR_BANNER);
        if (banner == null || banner.isEmpty() || !banner.startsWith("@")) {
            reportMissingBanner();
            return;
        }

        String ref = banner.substring(1);
        int slash = ref.lastIndexOf('/');
        if (slash == -1 || slash == ref.length() - 1) {
            reportMissingBanner();
            return;
        }

        String name = ref.substring(slash + 1);
        String typeWithPackage = ref.substring(0, slash);
        int colon = typeWithPackage.lastIndexOf(':');
        String bannerType = colon == -1 ? typeWithPackage : typeWithPackage.substring(colon + 1);

        if (!"drawable".equals(bannerType) && !"mipmap".equals(bannerType)) {
            reportMissingBanner();
            return;
        }

        ResourceFolderType expectedType = "drawable".equals(bannerType)
                ? ResourceFolderType.DRAWABLE
                : ResourceFolderType.MIPMAP;

        boolean defaultFound = false;
        Set<String> locales = new HashSet<>();
        Map<String, Boolean> localeFound = new HashMap<>();

        for (FolderInfo info : mResourceFolders) {
            if (info.type != expectedType) {
                continue;
            }

            if (info.locale == null) {
                if (info.baseNames.contains(name)) {
                    defaultFound = true;
                }
            } else {
                locales.add(info.locale);
                if (info.baseNames.contains(name)) {
                    localeFound.put(info.locale, Boolean.TRUE);
                }
            }
        }

        Attr bannerAttr = mApplicationElement.getAttributeNodeNS(ANDROID_URI, ATTR_BANNER);
        Location bannerLocation = bannerAttr != null
                ? mManifestContext.getLocation(bannerAttr)
                : mManifestContext.getLocation(mApplicationElement);

        if (!defaultFound) {
            String message = String.format(
                    "TV banner resource `%1$s` was not found in the default %2$s folder",
                    banner, bannerType);
            mManifestContext.report(MISSING_BANNER, bannerAttr != null ? bannerAttr : mApplicationElement,
                    bannerLocation, message);
        }

        for (String locale : locales) {
            if (!localeFound.containsKey(locale)) {
                String message = String.format(
                        "TV banner resource `%1$s` is missing for localization `%2$s`",
                        banner, locale);
                mManifestContext.report(MISSING_BANNER, bannerAttr != null ? bannerAttr : mApplicationElement,
                        bannerLocation, message);
            }
        }
    }

    private void reportMissingBanner() {
        String message = "TV application is missing the required home screen banner; add android:banner to the application element";
        mManifestContext.report(MISSING_BANNER, mApplicationElement,
                mManifestContext.getLocation(mApplicationElement), message);
    }

    private static String getLocaleQualifier(String qualifiers) {
        if (qualifiers == null || qualifiers.isEmpty()) {
            return null;
        }

        String[] parts = qualifiers.split("-");
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (LOCALE_PATTERN.matcher(part).matches()) {
                if (i + 1 < parts.length && REGION_PATTERN.matcher(parts[i + 1]).matches()) {
                    return part + "-" + parts[i + 1];
                }
                return part;
            }
            if (BCP47_PATTERN.matcher(part).matches()) {
                return part;
            }
        }

        return null;
    }

    private static final Pattern LOCALE_PATTERN = Pattern.compile("[a-zA-Z]{2,3}");
    private static final Pattern REGION_PATTERN = Pattern.compile("r[A-Z]{2}");
    private static final Pattern BCP47_PATTERN = Pattern.compile("b\\+[A-Za-z][A-Za-z0-9]*(\\+[A-Za-z][A-Za-z0-9]*)*");
}