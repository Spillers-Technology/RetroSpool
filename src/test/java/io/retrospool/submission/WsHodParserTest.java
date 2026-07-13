package io.retrospool.submission;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WsHodParserTest {

    private final WsHodParser parser = new WsHodParser();

    // A representative IBM Personal Communications .ws printer session over TLS.
    private static final String PCOMM_5250_PRINTER = """
            [Profile]
            ID=WS
            Description=Payroll Reports PRT01

            [Communication]
            Link=telnet
            Session=5250

            [Telnet5250]
            Host=ibmi.example.com
            Port=992
            LUName=PRT01
            SSL=Y
            SessionType=Printer
            CodePage=037

            [Printer]
            PrinterAssocDevice=PRT01
            """;

    @Test
    void parsesPcommPrinterSession() {
        ParsedDraft draft = parser.parse(PCOMM_5250_PRINTER, "payroll.ws");

        assertThat(draft.host()).isEqualTo("ibmi.example.com");
        assertThat(draft.port()).isEqualTo(992);
        assertThat(draft.useSsl()).isTrue();
        assertThat(draft.deviceName()).isEqualTo("PRT01");
        assertThat(draft.ccsid()).isEqualTo(37);
        assertThat(draft.sessionType()).isEqualTo("5250 Printer");
        assertThat(draft.name()).isEqualTo("Payroll Reports PRT01");
        assertThat(draft.sourceFormat()).isEqualTo("PComm .ws");
    }

    @Test
    void nonSslTelnetIsReportedAsPlaintext() {
        String ws = """
                [Telnet5250]
                Host=legacy.example.com
                Port=23
                Security=0
                """;
        ParsedDraft draft = parser.parse(ws, "legacy.ws");

        assertThat(draft.host()).isEqualTo("legacy.example.com");
        assertThat(draft.port()).isEqualTo(23);
        assertThat(draft.useSsl()).isFalse();
    }

    @Test
    void infersSslFromPortWhenNotStated() {
        String ws = """
                [Telnet5250]
                Host=ibmi.example.com
                Port=992
                """;
        ParsedDraft draft = parser.parse(ws, "s.ws");
        assertThat(draft.useSsl()).isTrue();
        // No explicit SSL key and an SSL port: inferred, so no "defaulted" warning.
        assertThat(draft.warnings()).noneMatch(w -> w.contains("defaulted"));
    }

    @Test
    void defaultsSslOnAndWarnsWhenSilent() {
        String ws = """
                [Telnet5250]
                Host=ibmi.example.com
                """;
        ParsedDraft draft = parser.parse(ws, "s.ws");
        assertThat(draft.useSsl()).isTrue();
        assertThat(draft.warnings()).anyMatch(w -> w.toLowerCase().contains("tls"));
    }

    @Test
    void missingHostProducesWarningAndNullHost() {
        String ws = """
                [Telnet5250]
                LUName=PRT01
                Port=992
                """;
        ParsedDraft draft = parser.parse(ws, "nohost.ws");
        assertThat(draft.host()).isNull();
        assertThat(draft.warnings()).anyMatch(w -> w.toLowerCase().contains("host"));
    }

    @Test
    void fallsBackToFileNameForSessionName() {
        String ws = """
                [Telnet5250]
                Host=ibmi.example.com
                """;
        ParsedDraft draft = parser.parse(ws, "C:\\sessions\\AR-Reports.ws");
        assertThat(draft.name()).isEqualTo("AR-Reports");
    }

    @Test
    void parsesHodParamStyle() {
        String hod = """
                <html><body>
                <applet code="com.ibm.eNetwork.HODDisplay">
                  <param name="host" value="hod.example.com">
                  <param name="port" value="992">
                  <param name="sessionType" value="3">
                  <param name="LUName" value="DEV05">
                  <param name="SSL" value="true">
                  <param name="userID" value="RPTUSER">
                </applet>
                </body></html>
                """;
        ParsedDraft draft = parser.parse(hod, "session.hod");

        assertThat(draft.sourceFormat()).isEqualTo("HOD");
        assertThat(draft.host()).isEqualTo("hod.example.com");
        assertThat(draft.port()).isEqualTo(992);
        assertThat(draft.useSsl()).isTrue();
        assertThat(draft.deviceName()).isEqualTo("DEV05");
        assertThat(draft.username()).isEqualTo("RPTUSER");
        assertThat(draft.sessionType()).isEqualTo("5250 Display");
    }

    @Test
    void parsesHodParamReversedAttributeOrder() {
        String hod = """
                <param value="reversed.example.com" name="host">
                <param name="hostondemand" value="x">
                """;
        ParsedDraft draft = parser.parse(hod, "r.hod");
        assertThat(draft.host()).isEqualTo("reversed.example.com");
    }

    @Test
    void toleratesEmptyFileWithoutThrowing() {
        ParsedDraft draft = parser.parse("", "empty.ws");
        assertThat(draft.host()).isNull();
        assertThat(draft.name()).isEqualTo("empty");
        assertThat(draft.warnings()).isNotEmpty();
    }

    @Test
    void quotedIniValuesAreUnquoted() {
        String ws = """
                [Telnet5250]
                Host="quoted.example.com"
                Description='Quarterly Close'
                """;
        ParsedDraft draft = parser.parse(ws, "q.ws");
        assertThat(draft.host()).isEqualTo("quoted.example.com");
        assertThat(draft.name()).isEqualTo("Quarterly Close");
    }
}
