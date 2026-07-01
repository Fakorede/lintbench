package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class ManifestTypoDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "ManifestTypo",
            "Typos in manifest tags",
            "This check looks through the manifest, and if it finds any tags that look like " +
            "likely misspellings, they are flagged.",
            Category.CORRECTNESS,
            5,
            Severity.FATAL,
            new Implementation(
                    ManifestTypoDetector.class,
                    Scope.MANIFEST_SCOPE
            )
    );

    /** Known valid manifest tag names */
    private static final String[] VALID_TAGS = {
            "manifest",
            "application",
            "activity",
            "activity-alias",
            "service",
            "receiver",
            "provider",
            "uses-permission",
            "uses-permission-sdk-23",
            "permission",
            "permission-group",
            "permission-tree",
            "uses-feature",
            "uses-library",
            "uses-sdk",
            "uses-configuration",
            "instrumentation",
            "supports-screens",
            "compatible-screens",
            "supports-gl-texture",
            "grant-uri-permission",
            "meta-data",
            "intent-filter",
            "action",
            "category",
            "data",
            "queries",
            "package",
            "profileable",
            "overlay",
            "attribution",
            "adopt-permissions",
            "original-package",
            "protected-broadcast",
            "path-permission",
            "restrict-update",
            "eat-comment",
            "locale-config",
            "property",
            "receiver",
            "static-library",
            "uses-static-library",
            "uses-native-library",
            "library",
            "apex-system-service",
    };

    /**
     * Map from common typos to their correct spelling.
     */
    private static final Map<String, String> TYPO_MAP = new HashMap<>();

    static {
        // activity typos
        TYPO_MAP.put("activty", "activity");
        TYPO_MAP.put("actvity", "activity");
        TYPO_MAP.put("activiy", "activity");
        TYPO_MAP.put("activiti", "activity");
        TYPO_MAP.put("acivity", "activity");
        TYPO_MAP.put("ativity", "activity");
        TYPO_MAP.put("activitiy", "activity");
        TYPO_MAP.put("activitty", "activity");
        TYPO_MAP.put("activiity", "activity");

        // activity-alias typos
        TYPO_MAP.put("activity-alais", "activity-alias");
        TYPO_MAP.put("activity-alis", "activity-alias");
        TYPO_MAP.put("activty-alias", "activity-alias");

        // service typos
        TYPO_MAP.put("sevice", "service");
        TYPO_MAP.put("serivce", "service");
        TYPO_MAP.put("servce", "service");
        TYPO_MAP.put("srevice", "service");
        TYPO_MAP.put("servcie", "service");
        TYPO_MAP.put("serice", "service");

        // receiver typos
        TYPO_MAP.put("reciver", "receiver");
        TYPO_MAP.put("reciever", "receiver");
        TYPO_MAP.put("recever", "receiver");
        TYPO_MAP.put("recevier", "receiver");
        TYPO_MAP.put("receiever", "receiver");
        TYPO_MAP.put("recieiver", "receiver");

        // provider typos
        TYPO_MAP.put("provder", "provider");
        TYPO_MAP.put("providor", "provider");
        TYPO_MAP.put("proivder", "provider");
        TYPO_MAP.put("provdier", "provider");
        TYPO_MAP.put("provicer", "provider");

        // uses-permission typos
        TYPO_MAP.put("uses-permision", "uses-permission");
        TYPO_MAP.put("uses-permisson", "uses-permission");
        TYPO_MAP.put("uses-persmission", "uses-permission");
        TYPO_MAP.put("use-permission", "uses-permission");
        TYPO_MAP.put("uses-premission", "uses-permission");
        TYPO_MAP.put("uses-permisison", "uses-permission");
        TYPO_MAP.put("uses-permssion", "uses-permission");
        TYPO_MAP.put("uses-permision", "uses-permission");

        // uses-feature typos
        TYPO_MAP.put("uses-feture", "uses-feature");
        TYPO_MAP.put("uses-featrue", "uses-feature");
        TYPO_MAP.put("use-feature", "uses-feature");
        TYPO_MAP.put("uses-feaure", "uses-feature");

        // uses-library typos
        TYPO_MAP.put("uses-libary", "uses-library");
        TYPO_MAP.put("uses-libarary", "uses-library");
        TYPO_MAP.put("use-library", "uses-library");

        // uses-sdk typos
        TYPO_MAP.put("uses-skd", "uses-sdk");
        TYPO_MAP.put("use-sdk", "uses-sdk");
        TYPO_MAP.put("uses-dsk", "uses-sdk");

        // application typos
        TYPO_MAP.put("applicaton", "application");
        TYPO_MAP.put("applcation", "application");
        TYPO_MAP.put("aplication", "application");
        TYPO_MAP.put("applicaion", "application");
        TYPO_MAP.put("appliation", "application");
        TYPO_MAP.put("applicaiton", "application");
        TYPO_MAP.put("appication", "application");

        // manifest typos
        TYPO_MAP.put("mainifest", "manifest");
        TYPO_MAP.put("manifets", "manifest");
        TYPO_MAP.put("manifset", "manifest");
        TYPO_MAP.put("mainfest", "manifest");
        TYPO_MAP.put("manifiest", "manifest");

        // intent-filter typos
        TYPO_MAP.put("intent-fliter", "intent-filter");
        TYPO_MAP.put("intent-fileter", "intent-filter");
        TYPO_MAP.put("intent-filer", "intent-filter");
        TYPO_MAP.put("intet-filter", "intent-filter");
        TYPO_MAP.put("intent-filtter", "intent-filter");

        // meta-data typos
        TYPO_MAP.put("meta-dat", "meta-data");
        TYPO_MAP.put("meta-dta", "meta-data");
        TYPO_MAP.put("meta-dtaa", "meta-data");
        TYPO_MAP.put("meta-datat", "meta-data");
        TYPO_MAP.put("meata-data", "meta-data");
        TYPO_MAP.put("meta-data", "meta-data"); // correct, but just in case

        // instrumentation typos
        TYPO_MAP.put("instrumenation", "instrumentation");
        TYPO_MAP.put("instrumentaion", "instrumentation");
        TYPO_MAP.put("instrumantation", "instrumentation");

        // supports-screens typos
        TYPO_MAP.put("supports-screen", "supports-screens");
        TYPO_MAP.put("support-screens", "supports-screens");

        // permission typos
        TYPO_MAP.put("permision", "permission");
        TYPO_MAP.put("permisson", "permission");
        TYPO_MAP.put("persmission", "permission");
        TYPO_MAP.put("premission", "permission");
        TYPO_MAP.put("permisison", "permission");
        TYPO_MAP.put("permssion", "permission");

        // grant-uri-permission typos
        TYPO_MAP.put("grant-uri-permision", "grant-uri-permission");
        TYPO_MAP.put("grant-uri-permisson", "grant-uri-permission");
        TYPO_MAP.put("grant-uri-premission", "grant-uri-permission");

        // uses-configuration typos
        TYPO_MAP.put("uses-configuraton", "uses-configuration");
        TYPO_MAP.put("uses-configuraion", "uses-configuration");
        TYPO_MAP.put("use-configuration", "uses-configuration");

        // compatible-screens typos
        TYPO_MAP.put("compatible-screen", "compatible-screens");
        TYPO_MAP.put("compatable-screens", "compatible-screens");

        // supports-gl-texture typos
        TYPO_MAP.put("supports-gl-texure", "supports-gl-texture");
        TYPO_MAP.put("support-gl-texture", "supports-gl-texture");

        // queries typos
        TYPO_MAP.put("quaries", "queries");
        TYPO_MAP.put("quereis", "queries");
        TYPO_MAP.put("queres", "queries");

        // profileable typos
        TYPO_MAP.put("profileble", "profileable");
        TYPO_MAP.put("profilable", "profileable");
        TYPO_MAP.put("profileabel", "profileable");

        // attribution typos
        TYPO_MAP.put("atribution", "attribution");
        TYPO_MAP.put("attibution", "attribution");
        TYPO_MAP.put("attributon", "attribution");
        TYPO_MAP.put("attriubtion", "attribution");

        // property typos
        TYPO_MAP.put("proprty", "property");
        TYPO_MAP.put("propety", "property");
        TYPO_MAP.put("proerty", "property");

        // library typos
        TYPO_MAP.put("libary", "library");
        TYPO_MAP.put("libarary", "library");
        TYPO_MAP.put("librray", "library");

        // data typos
        TYPO_MAP.put("dta", "data");
        TYPO_MAP.put("dat", "data");

        // action typos
        TYPO_MAP.put("acton", "action");
        TYPO_MAP.put("acction", "action");
        TYPO_MAP.put("acion", "action");

        // category typos
        TYPO_MAP.put("catagory", "category");
        TYPO_MAP.put("categroy", "category");
        TYPO_MAP.put("categoy", "category");
        TYPO_MAP.put("catgory", "category");
        TYPO_MAP.put("caegory", "category");
        TYPO_MAP.put("cateogry", "category");

        // package typos
        TYPO_MAP.put("pakage", "package");
        TYPO_MAP.put("pacakge", "package");
        TYPO_MAP.put("packge", "package");
        TYPO_MAP.put("pakge", "package");

        // overlay typos
        TYPO_MAP.put("overaly", "overlay");
        TYPO_MAP.put("ovrelay", "overlay");
        TYPO_MAP.put("overlya", "overlay");

        // path-permission typos
        TYPO_MAP.put("path-permision", "path-permission");
        TYPO_MAP.put("path-permisson", "path-permission");
        TYPO_MAP.put("path-premission", "path-permission");

        // permission-group typos
        TYPO_MAP.put("permission-grp", "permission-group");
        TYPO_MAP.put("permision-group", "permission-group");
        TYPO_MAP.put("permisson-group", "permission-group");

        // permission-tree typos
        TYPO_MAP.put("permision-tree", "permission-tree");
        TYPO_MAP.put("permisson-tree", "permission-tree");

        // uses-native-library typos
        TYPO_MAP.put("uses-native-libary", "uses-native-library");
        TYPO_MAP.put("use-native-library", "uses-native-library");

        // uses-static-library typos
        TYPO_MAP.put("uses-static-libary", "uses-static-library");
        TYPO_MAP.put("use-static-library", "uses-static-library");

        // static-library typos
        TYPO_MAP.put("static-libary", "static-library");
        TYPO_MAP.put("statc-library", "static-library");

        // locale-config typos
        TYPO_MAP.put("locale-confg", "locale-config");
        TYPO_MAP.put("locale-conifg", "locale-config");
        TYPO_MAP.put("locle-config", "locale-config");

        // restrict-update typos
        TYPO_MAP.put("restrict-upate", "restrict-update");
        TYPO_MAP.put("restirct-update", "restrict-update");
        TYPO_MAP.put("restrct-update", "restrict-update");

        // adopt-permissions typos
        TYPO_MAP.put("adopt-permisions", "adopt-permissions");
        TYPO_MAP.put("adopt-permissons", "adopt-permissions");
        TYPO_MAP.put("adpot-permissions", "adopt-permissions");

        // original-package typos
        TYPO_MAP.put("original-pakage", "original-package");
        TYPO_MAP.put("orignal-package", "original-package");
        TYPO_MAP.put("orignial-package", "original-package");

        // protected-broadcast typos
        TYPO_MAP.put("protected-brodcast", "protected-broadcast");
        TYPO_MAP.put("protcted-broadcast", "protected-broadcast");
        TYPO_MAP.put("protected-braodcast", "protected-broadcast");
    }

    @Override
    public Collection<String> getApplicableElements() {
        return XmlScanner.ALL;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String tag = element.getLocalName();
        if (tag == null) {
            tag = element.getTagName();
        }
        if (tag == null) {
            return;
        }

        String corrected = TYPO_MAP.get(tag);
        if (corrected != null && !corrected.equals(tag)) {
            context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    String.format("Misspelled tag `<%1$s>`: Did you mean `<%2$s>`?", tag, corrected)
            );
            return;
        }

        // Also check using edit distance for tags that are close to known valid tags
        // but not in our explicit typo map
        if (!isKnownTag(tag)) {
            String suggestion = findClosestTag(tag);
            if (suggestion != null) {
                context.report(
                        ISSUE,
                        element,
                        context.getNameLocation(element),
                        String.format("Misspelled tag `<%1$s>`: Did you mean `<%2$s>`?", tag, suggestion)
                );
            }
        }
    }

    private static boolean isKnownTag(String tag) {
        for (String validTag : VALID_TAGS) {
            if (validTag.equals(tag)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Finds the closest known valid tag using edit distance.
     * Returns null if no close match is found (to avoid false positives).
     */
    private static String findClosestTag(String tag) {
        int minDistance = Integer.MAX_VALUE;
        String closest = null;

        for (String validTag : VALID_TAGS) {
            int distance = editDistance(tag, validTag);
            if (distance < minDistance) {
                minDistance = distance;
                closest = validTag;
            }
        }

        // Only suggest if the edit distance is small enough relative to tag length
        // to avoid false positives on completely unrelated tags
        if (closest != null && minDistance <= 2 && minDistance < tag.length() / 2) {
            return closest;
        }

        return null;
    }

    /**
     * Computes the Levenshtein edit distance between two strings.
     */
    private static int editDistance(String s, String t) {
        int m = s.length();
        int n = t.length();

        int[][] dp = new int[m + 1][n + 1];

        for (int i = 0; i <= m; i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= n; j++) {
            dp[0][j] = j;
        }

        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                if (s.charAt(i - 1) == t.charAt(j - 1)) {
                    dp[i][j] = dp[i - 1][j - 1];
                } else {
                    dp[i][j] = 1 + Math.min(dp[i - 1][j - 1],
                            Math.min(dp[i - 1][j], dp[i][j - 1]));
                }
            }
        }

        return dp[m][n];
    }
}