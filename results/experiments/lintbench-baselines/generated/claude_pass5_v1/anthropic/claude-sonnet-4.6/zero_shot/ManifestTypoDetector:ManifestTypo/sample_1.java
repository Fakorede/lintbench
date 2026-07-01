package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Checks for typos in AndroidManifest.xml tags.
 */
public class ManifestTypoDetector extends Detector implements XmlScanner {

    /** The main issue discovered by this detector */
    public static final Issue ISSUE = Issue.create(
            "ManifestTypo",
            "Typos in manifest tags",
            "This check looks through the manifest, and if it finds any tags " +
            "that look like likely misspellings, they are flagged.",
            Category.CORRECTNESS,
            5,
            Severity.FATAL,
            new Implementation(
                    ManifestTypoDetector.class,
                    Scope.MANIFEST_SCOPE));

    /**
     * Set of valid manifest tag names.
     */
    private static final Set<String> VALID_TAGS = new HashSet<>(Arrays.asList(
            "manifest",
            "application",
            "activity",
            "activity-alias",
            "service",
            "receiver",
            "provider",
            "uses-library",
            "uses-permission",
            "uses-permission-sdk-23",
            "permission",
            "permission-group",
            "permission-tree",
            "uses-feature",
            "uses-sdk",
            "uses-configuration",
            "compatible-screens",
            "screen",
            "supports-screens",
            "supports-gl-texture",
            "intent-filter",
            "action",
            "category",
            "data",
            "meta-data",
            "grant-uri-permission",
            "path-permission",
            "instrumentation",
            "queries",
            "package",
            "profileable",
            "attribution",
            "overlay",
            "protected-broadcast",
            "adopt-permissions",
            "original-package",
            "eat-comment",
            "install-constraints",
            "required-feature",
            "required-not-feature"
    ));

    /**
     * Map from common typos to their correct spellings.
     */
    private static final Map<String, String> TYPO_MAP = new HashMap<>();

    static {
        // Common typos for "manifest"
        TYPO_MAP.put("Manifest", "manifest");
        TYPO_MAP.put("mainifest", "manifest");
        TYPO_MAP.put("manifets", "manifest");
        TYPO_MAP.put("manifset", "manifest");
        TYPO_MAP.put("manifiest", "manifest");
        TYPO_MAP.put("manfiest", "manifest");
        TYPO_MAP.put("manfest", "manifest");

        // Common typos for "application"
        TYPO_MAP.put("Application", "application");
        TYPO_MAP.put("aplication", "application");
        TYPO_MAP.put("applicaiton", "application");
        TYPO_MAP.put("applcation", "application");
        TYPO_MAP.put("appication", "application");
        TYPO_MAP.put("applicaton", "application");
        TYPO_MAP.put("appliation", "application");

        // Common typos for "activity"
        TYPO_MAP.put("Activity", "activity");
        TYPO_MAP.put("activty", "activity");
        TYPO_MAP.put("activiy", "activity");
        TYPO_MAP.put("actvity", "activity");
        TYPO_MAP.put("activiti", "activity");
        TYPO_MAP.put("actviity", "activity");
        TYPO_MAP.put("acivity", "activity");

        // Common typos for "activity-alias"
        TYPO_MAP.put("activity-alais", "activity-alias");
        TYPO_MAP.put("activty-alias", "activity-alias");
        TYPO_MAP.put("activity-aliase", "activity-alias");

        // Common typos for "service"
        TYPO_MAP.put("Service", "service");
        TYPO_MAP.put("sevice", "service");
        TYPO_MAP.put("serivce", "service");
        TYPO_MAP.put("servce", "service");
        TYPO_MAP.put("srvice", "service");
        TYPO_MAP.put("servcie", "service");

        // Common typos for "receiver"
        TYPO_MAP.put("Receiver", "receiver");
        TYPO_MAP.put("reciever", "receiver");
        TYPO_MAP.put("reciver", "receiver");
        TYPO_MAP.put("recevier", "receiver");
        TYPO_MAP.put("receiever", "receiver");
        TYPO_MAP.put("recever", "receiver");

        // Common typos for "provider"
        TYPO_MAP.put("Provider", "provider");
        TYPO_MAP.put("provder", "provider");
        TYPO_MAP.put("providor", "provider");
        TYPO_MAP.put("proivder", "provider");
        TYPO_MAP.put("provdier", "provider");

        // Common typos for "uses-library"
        TYPO_MAP.put("uses-libary", "uses-library");
        TYPO_MAP.put("use-library", "uses-library");
        TYPO_MAP.put("uses-libarary", "uses-library");
        TYPO_MAP.put("uses-librray", "uses-library");
        TYPO_MAP.put("uses-lirary", "uses-library");

        // Common typos for "uses-permission"
        TYPO_MAP.put("uses-premission", "uses-permission");
        TYPO_MAP.put("use-permission", "uses-permission");
        TYPO_MAP.put("uses-permision", "uses-permission");
        TYPO_MAP.put("uses-permssion", "uses-permission");
        TYPO_MAP.put("uses-persmission", "uses-permission");
        TYPO_MAP.put("uses-permision", "uses-permission");
        TYPO_MAP.put("user-permission", "uses-permission");
        TYPO_MAP.put("uses-permisions", "uses-permission");

        // Common typos for "permission"
        TYPO_MAP.put("Permission", "permission");
        TYPO_MAP.put("premission", "permission");
        TYPO_MAP.put("permision", "permission");
        TYPO_MAP.put("permssion", "permission");
        TYPO_MAP.put("persmission", "permission");

        // Common typos for "permission-group"
        TYPO_MAP.put("permission-grp", "permission-group");
        TYPO_MAP.put("permision-group", "permission-group");
        TYPO_MAP.put("permission-groupe", "permission-group");

        // Common typos for "uses-feature"
        TYPO_MAP.put("use-feature", "uses-feature");
        TYPO_MAP.put("uses-featre", "uses-feature");
        TYPO_MAP.put("uses-feture", "uses-feature");
        TYPO_MAP.put("uses-freature", "uses-feature");

        // Common typos for "uses-sdk"
        TYPO_MAP.put("use-sdk", "uses-sdk");
        TYPO_MAP.put("uses-skd", "uses-sdk");
        TYPO_MAP.put("user-sdk", "uses-sdk");

        // Common typos for "supports-screens"
        TYPO_MAP.put("support-screens", "supports-screens");
        TYPO_MAP.put("supports-screen", "supports-screens");
        TYPO_MAP.put("suports-screens", "supports-screens");

        // Common typos for "intent-filter"
        TYPO_MAP.put("intent-fliter", "intent-filter");
        TYPO_MAP.put("intent-filter", "intent-filter"); // already correct but catches case
        TYPO_MAP.put("intnet-filter", "intent-filter");
        TYPO_MAP.put("intent-filtre", "intent-filter");
        TYPO_MAP.put("inten-filter", "intent-filter");
        TYPO_MAP.put("intent-flter", "intent-filter");

        // Common typos for "meta-data"
        TYPO_MAP.put("meta-date", "meta-data");
        TYPO_MAP.put("meta-dta", "meta-data");
        TYPO_MAP.put("meta-dat", "meta-data");
        TYPO_MAP.put("metedata", "meta-data");
        TYPO_MAP.put("metadata", "meta-data");

        // Common typos for "action"
        TYPO_MAP.put("Action", "action");
        TYPO_MAP.put("acton", "action");
        TYPO_MAP.put("actoin", "action");

        // Common typos for "category"
        TYPO_MAP.put("Category", "category");
        TYPO_MAP.put("categroy", "category");
        TYPO_MAP.put("catagory", "category");
        TYPO_MAP.put("categry", "category");
        TYPO_MAP.put("catgory", "category");

        // Common typos for "instrumentation"
        TYPO_MAP.put("Instrumentation", "instrumentation");
        TYPO_MAP.put("instrumentaion", "instrumentation");
        TYPO_MAP.put("instrumenation", "instrumentation");
        TYPO_MAP.put("instrumantation", "instrumentation");

        // Common typos for "grant-uri-permission"
        TYPO_MAP.put("grant-uri-premission", "grant-uri-permission");
        TYPO_MAP.put("grant-url-permission", "grant-uri-permission");
        TYPO_MAP.put("grant-uri-permision", "grant-uri-permission");

        // Common typos for "compatible-screens"
        TYPO_MAP.put("compatable-screens", "compatible-screens");
        TYPO_MAP.put("compatible-screen", "compatible-screens");
        TYPO_MAP.put("compatble-screens", "compatible-screens");

        // Common typos for "queries"
        TYPO_MAP.put("query", "queries");
        TYPO_MAP.put("querie", "queries");
        TYPO_MAP.put("querys", "queries");

        // Common typos for "profileable"
        TYPO_MAP.put("profileble", "profileable");
        TYPO_MAP.put("profilable", "profileable");
    }

    /** Constructs a new {@link ManifestTypoDetector} */
    public ManifestTypoDetector() {
    }

    // ---- Implements XmlScanner ----

    @Override
    public Collection<String> getApplicableElements() {
        return XmlScanner.ALL;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String tag = element.getTagName();

        // Check if this is a known typo
        if (TYPO_MAP.containsKey(tag)) {
            String correctTag = TYPO_MAP.get(tag);
            // Only report if the tag is not already valid (avoid reporting correct tags
            // that were accidentally added to the typo map)
            if (!tag.equals(correctTag)) {
                context.report(
                        ISSUE,
                        element,
                        context.getLocation(element),
                        String.format("Misspelled tag `<%1$s>`: Did you mean `<%2$s>`?",
                                tag, correctTag));
                return;
            }
        }

        // Check using edit distance for tags that look similar to known valid tags
        // but are not in the valid set and not already in the typo map
        if (!VALID_TAGS.contains(tag) && !tag.contains(":")) {
            String bestMatch = findBestMatch(tag);
            if (bestMatch != null) {
                context.report(
                        ISSUE,
                        element,
                        context.getLocation(element),
                        String.format("Misspelled tag `<%1$s>`: Did you mean `<%2$s>`?",
                                tag, bestMatch));
            }
        }
    }

    /**
     * Finds the best matching valid tag for a given tag name using edit distance,
     * or returns null if no close match is found.
     */
    private static String findBestMatch(String tag) {
        if (tag == null || tag.isEmpty()) {
            return null;
        }

        String bestMatch = null;
        int bestDistance = Integer.MAX_VALUE;

        for (String validTag : VALID_TAGS) {
            int distance = editDistance(tag, validTag);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestMatch = validTag;
            }
        }

        // Only suggest if the edit distance is small enough relative to the tag length
        // to be a plausible typo
        if (bestMatch != null) {
            int threshold = Math.max(1, Math.min(tag.length(), bestMatch.length()) / 4 + 1);
            if (bestDistance <= threshold && bestDistance > 0) {
                return bestMatch;
            }
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