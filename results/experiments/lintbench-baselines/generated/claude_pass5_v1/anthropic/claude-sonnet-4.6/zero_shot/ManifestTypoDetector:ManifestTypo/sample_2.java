package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.*;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.*;

import static com.android.SdkConstants.ANDROID_MANIFEST_XML;

public class ManifestTypoDetector extends Detector implements Detector.XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "ManifestTypo",
            "Typos in manifest tags",
            "This check looks through the manifest, and if it finds any tags " +
            "that look like likely misspellings, they are flagged.",
            Category.CORRECTNESS,
            5,
            Severity.ERROR,
            new Implementation(
                    ManifestTypoDetector.class,
                    Scope.MANIFEST_SCOPE
            )
    );

    // Known correct manifest tag names
    private static final Set<String> VALID_TAGS = new HashSet<>(Arrays.asList(
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
            "supports-screens",
            "compatible-screens",
            "supports-gl-texture",
            "intent-filter",
            "action",
            "category",
            "data",
            "meta-data",
            "instrumentation",
            "grant-uri-permission",
            "path-permission",
            "queries",
            "package",
            "profileable",
            "overlay",
            "attribution"
    ));

    // Common typos mapped to their corrections
    private static final Map<String, String> COMMON_TYPOS = new LinkedHashMap<>();

    static {
        // activity typos
        COMMON_TYPOS.put("activty", "activity");
        COMMON_TYPOS.put("actvity", "activity");
        COMMON_TYPOS.put("activiy", "activity");
        COMMON_TYPOS.put("activiti", "activity");
        COMMON_TYPOS.put("actviity", "activity");
        COMMON_TYPOS.put("acivity", "activity");

        // activity-alias typos
        COMMON_TYPOS.put("activity-alais", "activity-alias");
        COMMON_TYPOS.put("activty-alias", "activity-alias");

        // service typos
        COMMON_TYPOS.put("sevice", "service");
        COMMON_TYPOS.put("srevice", "service");
        COMMON_TYPOS.put("serivce", "service");
        COMMON_TYPOS.put("servce", "service");
        COMMON_TYPOS.put("servcie", "service");

        // receiver typos
        COMMON_TYPOS.put("reciver", "receiver");
        COMMON_TYPOS.put("reciever", "receiver");
        COMMON_TYPOS.put("receiever", "receiver");
        COMMON_TYPOS.put("recevier", "receiver");
        COMMON_TYPOS.put("recever", "receiver");

        // provider typos
        COMMON_TYPOS.put("provder", "provider");
        COMMON_TYPOS.put("proivder", "provider");
        COMMON_TYPOS.put("providor", "provider");
        COMMON_TYPOS.put("provdier", "provider");

        // uses-permission typos
        COMMON_TYPOS.put("uses-permision", "uses-permission");
        COMMON_TYPOS.put("uses-permisson", "uses-permission");
        COMMON_TYPOS.put("uses-permssion", "uses-permission");
        COMMON_TYPOS.put("use-permission", "uses-permission");
        COMMON_TYPOS.put("uses-permsision", "uses-permission");
        COMMON_TYPOS.put("uses-permision", "uses-permission");

        // permission typos
        COMMON_TYPOS.put("permision", "permission");
        COMMON_TYPOS.put("permisson", "permission");
        COMMON_TYPOS.put("permssion", "permission");

        // uses-feature typos
        COMMON_TYPOS.put("uses-feture", "uses-feature");
        COMMON_TYPOS.put("uses-featrue", "uses-feature");
        COMMON_TYPOS.put("use-feature", "uses-feature");
        COMMON_TYPOS.put("uses-feaure", "uses-feature");

        // uses-library typos
        COMMON_TYPOS.put("uses-libary", "uses-library");
        COMMON_TYPOS.put("uses-libaray", "uses-library");
        COMMON_TYPOS.put("use-library", "uses-library");

        // uses-sdk typos
        COMMON_TYPOS.put("uses-ssk", "uses-sdk");
        COMMON_TYPOS.put("use-sdk", "uses-sdk");
        COMMON_TYPOS.put("uses-skd", "uses-sdk");

        // application typos
        COMMON_TYPOS.put("applicaton", "application");
        COMMON_TYPOS.put("applcation", "application");
        COMMON_TYPOS.put("applicaiton", "application");
        COMMON_TYPOS.put("appication", "application");
        COMMON_TYPOS.put("aplication", "application");

        // intent-filter typos
        COMMON_TYPOS.put("intent-fliter", "intent-filter");
        COMMON_TYPOS.put("intent-filer", "intent-filter");
        COMMON_TYPOS.put("intent-filte", "intent-filter");
        COMMON_TYPOS.put("itent-filter", "intent-filter");
        COMMON_TYPOS.put("intet-filter", "intent-filter");

        // meta-data typos
        COMMON_TYPOS.put("meta-dat", "meta-data");
        COMMON_TYPOS.put("meta-dta", "meta-data");
        COMMON_TYPOS.put("mete-data", "meta-data");
        COMMON_TYPOS.put("mtea-data", "meta-data");

        // instrumentation typos
        COMMON_TYPOS.put("instrumenation", "instrumentation");
        COMMON_TYPOS.put("instrumentaion", "instrumentation");
        COMMON_TYPOS.put("instrumntation", "instrumentation");

        // supports-screens typos
        COMMON_TYPOS.put("supports-sceens", "supports-screens");
        COMMON_TYPOS.put("suports-screens", "supports-screens");
        COMMON_TYPOS.put("support-screens", "supports-screens");

        // manifest typos
        COMMON_TYPOS.put("manifets", "manifest");
        COMMON_TYPOS.put("mainifest", "manifest");
        COMMON_TYPOS.put("manifes", "manifest");
        COMMON_TYPOS.put("manfiest", "manifest");
        COMMON_TYPOS.put("mainifest", "manifest");

        // category typos
        COMMON_TYPOS.put("catagory", "category");
        COMMON_TYPOS.put("categroy", "category");
        COMMON_TYPOS.put("categori", "category");
        COMMON_TYPOS.put("catergory", "category");

        // action typos
        COMMON_TYPOS.put("acton", "action");
        COMMON_TYPOS.put("actiion", "action");
        COMMON_TYPOS.put("actoin", "action");

        // data typos
        COMMON_TYPOS.put("daat", "data");
        COMMON_TYPOS.put("dta", "data");

        // queries typos
        COMMON_TYPOS.put("quereies", "queries");
        COMMON_TYPOS.put("quereis", "queries");
        COMMON_TYPOS.put("quieries", "queries");
    }

    public ManifestTypoDetector() {
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

        // Check if it's a known typo
        String correction = COMMON_TYPOS.get(tag);
        if (correction != null) {
            context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    String.format("Misspelled tag `<%1$s>`: Did you mean `<%2$s>`?", tag, correction)
            );
            return;
        }

        // Check using edit distance for unknown tags that look similar to known tags
        if (!VALID_TAGS.contains(tag) && !tag.contains(":")) {
            String closest = findClosestTag(tag);
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

    /**
     * Finds the closest valid tag name to the given tag, if it's close enough
     * to be considered a likely typo.
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

        // Only report if edit distance is small relative to the tag length
        // to avoid false positives
        if (closest != null) {
            int threshold = Math.max(1, closest.length() / 4);
            if (minDistance <= threshold && minDistance > 0) {
                return closest;
            }
        }

        return null;
    }

    /**
     * Computes the Levenshtein edit distance between two strings.
     */
    static int editDistance(String s, String t) {
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