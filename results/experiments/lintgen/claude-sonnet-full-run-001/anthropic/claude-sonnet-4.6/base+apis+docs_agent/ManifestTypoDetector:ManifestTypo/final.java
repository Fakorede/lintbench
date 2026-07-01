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
            "intent-filter",
            "action",
            "category",
            "data",
            "meta-data",
            "grant-uri-permission",
            "path-permission",
            "queries",
            "package",
            "profileable",
            "overlay",
            "attribution",
            "adopt-permissions",
            "original-package",
            "protected-broadcast",
            "restrict-update",
            "eat-comment",
            "locale-config",
            "property"
    };

    /**
     * Map from common typos to their correct spellings.
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
        TYPO_MAP.put("activty-alias", "activity-alias");
        TYPO_MAP.put("actvity-alias", "activity-alias");

        // service typos
        TYPO_MAP.put("sevice", "service");
        TYPO_MAP.put("serivce", "service");
        TYPO_MAP.put("servce", "service");
        TYPO_MAP.put("srevice", "service");
        TYPO_MAP.put("servcie", "service");

        // receiver typos
        TYPO_MAP.put("reciver", "receiver");
        TYPO_MAP.put("reciever", "receiver");
        TYPO_MAP.put("receiever", "receiver");
        TYPO_MAP.put("recevier", "receiver");
        TYPO_MAP.put("recever", "receiver");

        // provider typos
        TYPO_MAP.put("provder", "provider");
        TYPO_MAP.put("providor", "provider");
        TYPO_MAP.put("proivder", "provider");
        TYPO_MAP.put("provdier", "provider");

        // uses-permission typos
        TYPO_MAP.put("uses-premission", "uses-permission");
        TYPO_MAP.put("uses-permision", "uses-permission");
        TYPO_MAP.put("uses-permisson", "uses-permission");
        TYPO_MAP.put("use-permission", "uses-permission");
        TYPO_MAP.put("uses-permmission", "uses-permission");
        TYPO_MAP.put("uses-permisison", "uses-permission");

        // uses-feature typos
        TYPO_MAP.put("uses-feture", "uses-feature");
        TYPO_MAP.put("uses-featrue", "uses-feature");
        TYPO_MAP.put("use-feature", "uses-feature");
        TYPO_MAP.put("uses-feautre", "uses-feature");

        // uses-sdk typos
        TYPO_MAP.put("use-sdk", "uses-sdk");
        TYPO_MAP.put("uses-skd", "uses-sdk");
        TYPO_MAP.put("uses-dsk", "uses-sdk");

        // uses-library typos
        TYPO_MAP.put("use-library", "uses-library");
        TYPO_MAP.put("uses-libary", "uses-library");
        TYPO_MAP.put("uses-libarary", "uses-library");

        // application typos
        TYPO_MAP.put("applicaton", "application");
        TYPO_MAP.put("applcation", "application");
        TYPO_MAP.put("aplication", "application");
        TYPO_MAP.put("applicaiton", "application");
        TYPO_MAP.put("appliation", "application");

        // manifest typos
        TYPO_MAP.put("manifset", "manifest");
        TYPO_MAP.put("mainifest", "manifest");
        TYPO_MAP.put("manifiest", "manifest");
        TYPO_MAP.put("mainfest", "manifest");

        // intent-filter typos
        TYPO_MAP.put("intent-fliter", "intent-filter");
        TYPO_MAP.put("intent-fileter", "intent-filter");
        TYPO_MAP.put("intent-filer", "intent-filter");
        TYPO_MAP.put("intnet-filter", "intent-filter");
        TYPO_MAP.put("inten-filter", "intent-filter");

        // meta-data typos
        TYPO_MAP.put("meta-deta", "meta-data");
        TYPO_MAP.put("meta-date", "meta-data");
        TYPO_MAP.put("meta-dat", "meta-data");
        TYPO_MAP.put("mete-data", "meta-data");

        // permission typos
        TYPO_MAP.put("permision", "permission");
        TYPO_MAP.put("premission", "permission");
        TYPO_MAP.put("permisson", "permission");
        TYPO_MAP.put("permmission", "permission");

        // instrumentation typos
        TYPO_MAP.put("instrumenation", "instrumentation");
        TYPO_MAP.put("instrumentaion", "instrumentation");
        TYPO_MAP.put("instrumantation", "instrumentation");

        // supports-screens typos
        TYPO_MAP.put("supports-sceens", "supports-screens");
        TYPO_MAP.put("support-screens", "supports-screens");
        TYPO_MAP.put("supports-screen", "supports-screens");

        // category typos
        TYPO_MAP.put("catagory", "category");
        TYPO_MAP.put("categroy", "category");
        TYPO_MAP.put("categori", "category");
        TYPO_MAP.put("catetory", "category");

        // action typos
        TYPO_MAP.put("acton", "action");
        TYPO_MAP.put("actoin", "action");
        TYPO_MAP.put("aciton", "action");

        // data typos
        TYPO_MAP.put("deta", "data");
        TYPO_MAP.put("daat", "data");
        TYPO_MAP.put("dat", "data");

        // grant-uri-permission typos
        TYPO_MAP.put("grant-uri-premission", "grant-uri-permission");
        TYPO_MAP.put("grant-uri-permision", "grant-uri-permission");
        TYPO_MAP.put("grant-url-permission", "grant-uri-permission");

        // profileable typos
        TYPO_MAP.put("profileble", "profileable");
        TYPO_MAP.put("profilable", "profileable");
        TYPO_MAP.put("profileabel", "profileable");

        // queries typos
        TYPO_MAP.put("quereis", "queries");
        TYPO_MAP.put("quereies", "queries");
        TYPO_MAP.put("quiries", "queries");

        // package typos
        TYPO_MAP.put("pakage", "package");
        TYPO_MAP.put("packge", "package");
        TYPO_MAP.put("pacakge", "package");

        // attribution typos
        TYPO_MAP.put("atribution", "attribution");
        TYPO_MAP.put("attibution", "attribution");
        TYPO_MAP.put("attributon", "attribution");

        // property typos
        TYPO_MAP.put("proprety", "property");
        TYPO_MAP.put("proerty", "property");
        TYPO_MAP.put("propety", "property");
    }

    public ManifestTypoDetector() {
    }

    @Override
    public Collection<String> getApplicableElements() {
        return XmlScanner.ALL;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String tag = element.getTagName();
        // Strip namespace prefix if present
        int colonIndex = tag.indexOf(':');
        String localTag = colonIndex >= 0 ? tag.substring(colonIndex + 1) : tag;

        String lowerTag = localTag.toLowerCase();

        // Check if it's a known typo
        String correction = TYPO_MAP.get(lowerTag);
        if (correction != null) {
            context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    String.format("Misspelled tag `<%1$s>`: Did you mean `<%2$s>`?", tag, correction)
            );
            return;
        }

        // Check edit distance against known valid tags for close matches
        // (only for tags that aren't already known valid tags)
        if (!isKnownValidTag(lowerTag)) {
            String closest = findClosestTag(lowerTag);
            if (closest != null) {
                context.report(
                        ISSUE,
                        element,
                        context.getNameLocation(element),
                        String.format("Misspelled tag `<%1$s>`: Did you mean `<%2$s>`?", tag, closest)
                );
            }
        }
    }

    private static boolean isKnownValidTag(String tag) {
        for (String valid : VALID_TAGS) {
            if (valid.equals(tag)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Finds the closest valid tag to the given tag using edit distance,
     * returning it only if it's close enough to be a likely typo.
     */
    private static String findClosestTag(String tag) {
        // Only consider tags of reasonable length
        if (tag.length() < 3) {
            return null;
        }

        String closest = null;
        int minDistance = Integer.MAX_VALUE;

        for (String valid : VALID_TAGS) {
            int distance = editDistance(tag, valid);
            if (distance < minDistance) {
                minDistance = distance;
                closest = valid;
            }
        }

        // Only report if the edit distance is small enough to be a likely typo
        // Use a threshold based on tag length
        int threshold;
        if (tag.length() <= 4) {
            threshold = 1;
        } else if (tag.length() <= 8) {
            threshold = 2;
        } else {
            threshold = 3;
        }

        if (minDistance <= threshold) {
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