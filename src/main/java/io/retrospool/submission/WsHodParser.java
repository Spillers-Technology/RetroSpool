package io.retrospool.submission;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Parses an uploaded terminal-emulator session file into a normalized
 * {@link ParsedDraft} for the low-trust submission surface (D-007). Two shapes are
 * recognized, both tolerantly:
 *
 * <ul>
 *   <li><b>IBM Personal Communications {@code .ws}</b> — INI-style: {@code [Section]}
 *       headers with {@code Key=Value} lines. The IBM i host, telnet port, LU/device,
 *       and TLS posture live in the {@code [Telnet5250]}/{@code [Telnet3270]} section.</li>
 *   <li><b>Host On-Demand</b> — the exported HTML/bookmark carries the same settings as
 *       {@code <PARAM NAME="host" VALUE="…">} applet parameters; some deployments export
 *       flat {@code key=value} properties instead. Both are handled.</li>
 * </ul>
 *
 * <p>The parser is deliberately forgiving: an unrecognized or partial file still yields a
 * draft (with {@link ParsedDraft#warnings()} explaining what was missing) rather than
 * throwing, because the submitter reviews and completes the draft before it becomes a
 * pending submission. Session files never contain the IBM i password (D-010), so this
 * class never looks for one.
 */
@Component
public class WsHodParser {

    // <PARAM NAME="x" VALUE="y"> in either attribute order, quotes optional.
    private static final Pattern PARAM_TAG =
            Pattern.compile("<param\\s+([^>]*?)/?>", Pattern.CASE_INSENSITIVE);
    private static final Pattern PARAM_NAME =
            Pattern.compile("name\\s*=\\s*\"?([^\"\\s>]+)\"?", Pattern.CASE_INSENSITIVE);
    private static final Pattern PARAM_VALUE =
            Pattern.compile("value\\s*=\\s*\"([^\"]*)\"|value\\s*=\\s*([^\\s>]+)", Pattern.CASE_INSENSITIVE);

    private static final List<String> HOST_KEYS =
            List.of("host", "hostname", "sessionhost", "destination", "destaddr", "ipaddress");
    private static final List<String> PORT_KEYS =
            List.of("port", "hostport", "destport", "destinationport");
    private static final List<String> USER_KEYS =
            List.of("userid", "user", "username", "logonid", "signonuser", "userid1");
    private static final List<String> NAME_KEYS =
            List.of("description", "sessionname", "name", "title", "id");
    private static final List<String> DEVICE_KEYS =
            List.of("luname", "lu", "devicename", "associatedprinter", "printerassocdevice",
                    "associateddevice", "printer");
    private static final List<String> CCSID_KEYS =
            List.of("ccsid", "codepage", "hostcodepage", "hostcp");
    private static final List<String> SESSION_TYPE_KEYS =
            List.of("sessiontype", "session", "type");
    // Keys whose presence/value indicates the TLS posture of the session.
    private static final List<String> SSL_KEYS =
            List.of("ssl", "security", "securityprotocol", "sslserverauthentication",
                    "ssltelnetnegotiation", "enablesecurity");

    /**
     * @param content  raw file bytes decoded as text (ISO-8859-1 is safe for these
     *                 configuration files and never throws on stray bytes).
     * @param fileName original upload name, used as the session-name fallback.
     */
    public ParsedDraft parse(String content, String fileName) {
        String text = content == null ? "" : content;
        boolean hod = looksLikeHod(text);
        Map<String, String> fields = hod ? extractHod(text) : extractIni(text);

        List<String> warnings = new ArrayList<>();

        String host = first(fields, HOST_KEYS).orElse(null);
        if (host == null || host.isBlank()) {
            warnings.add("No host found in the file — enter the IBM i host name before submitting.");
            host = null;
        }

        Integer port = first(fields, PORT_KEYS).map(WsHodParser::parseIntOrNull).orElse(null);

        String username = first(fields, USER_KEYS).orElse(null);
        if (username == null) {
            warnings.add("No user id in the file (printer sessions rarely carry one) — add the IBM i user.");
        }

        String device = first(fields, DEVICE_KEYS).orElse(null);
        Integer ccsid = first(fields, CCSID_KEYS).map(WsHodParser::parseIntOrNull).orElse(null);

        Boolean explicitSsl = resolveSsl(fields);
        boolean useSsl;
        if (explicitSsl != null) {
            useSsl = explicitSsl;
        } else if (port != null && (port == 992 || port == 9476)) {
            useSsl = true;
        } else if (port != null && port == 23) {
            useSsl = false;
        } else {
            useSsl = true;
            warnings.add("TLS setting not found in the file — defaulted to enabled; confirm before submitting.");
        }

        String sessionType = resolveSessionType(fields, text, hod);

        String name = first(fields, NAME_KEYS)
                .filter(s -> !s.isBlank())
                .orElseGet(() -> stripExtension(fileName));

        return new ParsedDraft(
                host, port, useSsl, username, name, device, ccsid,
                sessionType, hod ? "HOD" : "PComm .ws", warnings);
    }

    private static boolean looksLikeHod(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("<param")) {
            return true;
        }
        // HOD bookmarks/applets reference the Host On-Demand applet or class; .ws files
        // are INI and always open with a bracketed section header.
        boolean iniShaped = text.stripLeading().startsWith("[");
        return !iniShaped && (lower.contains("hostondemand") || lower.contains("com.ibm.eNetwork"
                .toLowerCase(Locale.ROOT)) || lower.contains("hoddisplay") || lower.contains("<html"));
    }

    /** IBM PComm .ws: [Section] headers + Key=Value lines; ';' comments. First value wins. */
    private static Map<String, String> extractIni(String text) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (String raw : text.split("\\R")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith(";") || line.startsWith("#") || line.startsWith("[")) {
                continue;
            }
            int eq = line.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = line.substring(0, eq).trim().toLowerCase(Locale.ROOT);
            String value = unquote(line.substring(eq + 1).trim());
            fields.putIfAbsent(key, value);
        }
        return fields;
    }

    /** HOD: <PARAM NAME=".." VALUE=".."> applet params, plus any flat key=value lines. */
    private static Map<String, String> extractHod(String text) {
        Map<String, String> fields = new LinkedHashMap<>();
        Matcher tags = PARAM_TAG.matcher(text);
        while (tags.find()) {
            String attrs = tags.group(1);
            Matcher nameM = PARAM_NAME.matcher(attrs);
            Matcher valM = PARAM_VALUE.matcher(attrs);
            if (nameM.find() && valM.find()) {
                String key = nameM.group(1).trim().toLowerCase(Locale.ROOT);
                String value = unquote(Optional.ofNullable(valM.group(1)).orElse(valM.group(2)).trim());
                fields.putIfAbsent(key, value);
            }
        }
        // Fall back to flat property lines for property-style HOD exports.
        for (String raw : text.split("\\R")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("<")) {
                continue;
            }
            int eq = line.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = line.substring(0, eq).trim().toLowerCase(Locale.ROOT);
            fields.putIfAbsent(key, unquote(line.substring(eq + 1).trim()));
        }
        return fields;
    }

    /** @return TRUE/FALSE when the file states a TLS posture, null when it is silent. */
    private static Boolean resolveSsl(Map<String, String> fields) {
        for (String key : SSL_KEYS) {
            String value = fields.get(key);
            if (value == null) {
                continue;
            }
            String v = value.trim().toLowerCase(Locale.ROOT);
            if (v.isEmpty()) {
                continue;
            }
            if (v.equals("0") || v.equals("n") || v.equals("no") || v.equals("false")
                    || v.equals("off") || v.equals("none")) {
                return Boolean.FALSE;
            }
            // Y / YES / 1 / TRUE / SSL / TLS / TLS1.2 / ON — any non-negative assertion.
            return Boolean.TRUE;
        }
        return null;
    }

    private static String resolveSessionType(Map<String, String> fields, String text, boolean hod) {
        String declared = first(fields, SESSION_TYPE_KEYS).map(String::trim).orElse(null);
        if (declared != null) {
            // HOD encodes session type numerically in some exports.
            switch (declared) {
                case "1" -> { return "3270 Display"; }
                case "2" -> { return "3270 Printer"; }
                case "3" -> { return "5250 Display"; }
                case "4" -> { return "5250 Printer"; }
                case "5" -> { return "VT"; }
                default -> { }
            }
        }
        String lower = text.toLowerCase(Locale.ROOT);
        String dl = declared == null ? "" : declared.toLowerCase(Locale.ROOT);

        String family = null;
        if (dl.contains("5250") || lower.contains("telnet5250") || lower.contains("[5250]")) {
            family = "5250";
        } else if (dl.contains("3270") || lower.contains("telnet3270") || lower.contains("[3270]")) {
            family = "3270";
        } else if (dl.contains("vt")) {
            return "VT";
        }

        boolean printer = dl.contains("print")
                || lower.contains("[printer]")
                || fields.containsKey("printerassocdevice")
                || fields.containsKey("associatedprinter");

        if (family != null) {
            return family + (printer ? " Printer" : " Display");
        }
        if (!dl.isEmpty()) {
            return declared;
        }
        return hod ? "Host On-Demand session" : null;
    }

    private static Optional<String> first(Map<String, String> fields, List<String> keys) {
        for (String key : keys) {
            String value = fields.get(key);
            if (value != null && !value.isBlank()) {
                return Optional.of(value.trim());
            }
        }
        return Optional.empty();
    }

    private static Integer parseIntOrNull(String value) {
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String unquote(String value) {
        String v = value.trim();
        if (v.length() >= 2 && ((v.startsWith("\"") && v.endsWith("\""))
                || (v.startsWith("'") && v.endsWith("'")))) {
            return v.substring(1, v.length() - 1);
        }
        return v;
    }

    private static String stripExtension(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "Imported session";
        }
        String base = fileName;
        int slash = Math.max(base.lastIndexOf('/'), base.lastIndexOf('\\'));
        if (slash >= 0) {
            base = base.substring(slash + 1);
        }
        int dot = base.lastIndexOf('.');
        if (dot > 0) {
            base = base.substring(0, dot);
        }
        return base.isBlank() ? "Imported session" : base;
    }
}
