package com.gag4.offlinetester;

public class ApduAnalyzer {

    public enum Verdict {
        UNKNOWN("Necunoscut"),
        SUPPORTS_OFFLINE("✓ SUPORTĂ OFFLINE (P1=0x80 TC detectat)"),
        REQUIRES_ONLINE("✗ CERE ONLINE (P1=0x00 ARQC)")  ,
        DECLINED("✗ DECLINĂ (P1=0x40 AAC)"),
        INCOMPLETE("? INCOMPLET (GPO văzut, fără GENERATE AC)");

        private final String displayText;

        Verdict(String displayText) {
            this.displayText = displayText;
        }

        public String getDisplayText() {
            return displayText;
        }
    }

    private Verdict currentVerdict = Verdict.UNKNOWN;
    private boolean seenSelect = false;
    private boolean seenGpo = false;
    private boolean seenGenerateAc = false;
    private String selectedAid = "";
    private byte lastP1 = 0;

    public void reset() {
        currentVerdict = Verdict.UNKNOWN;
        seenSelect = false;
        seenGpo = false;
        seenGenerateAc = false;
        selectedAid = "";
        lastP1 = 0;
    }

    /**
     * Analyze APDU sent by POS (direction TX = transmitted by device, received by POS)
     */
    public Verdict analyzeTx(byte[] apdu) {
        if (apdu == null || apdu.length < 4) return null;

        int ins = apdu[1] & 0xFF;
        int p1 = apdu[2] & 0xFF;
        int p2 = apdu[3] & 0xFF;

        // SELECT (0xA4)
        if (ins == 0xA4) {
            seenSelect = true;
            // Extract AID from APDU if possible (usually at offset 5+)
            if (apdu.length > 5) {
                int len = apdu[4] & 0xFF;
                if (len > 0 && len <= 16) {
                    selectedAid = HexUtils.toHex(apdu).substring(10, 10 + len * 2);
                }
            }
            return null;
        }

        // GET PROCESSING OPTIONS (0xA8) = GPO
        if (ins == 0xA8) {
            seenGpo = true;
            return null;
        }

        // GENERATE AC (0xAE)
        if (ins == 0xAE) {
            seenGenerateAc = true;
            lastP1 = (byte) p1;

            // P1 = 0x80 → Request TC (Transaction Certificate - offline approved)
            if (p1 == 0x80) {
                currentVerdict = Verdict.SUPPORTS_OFFLINE;
                return currentVerdict;
            }
            // P1 = 0x00 → Request ARQC (Authorization Request Cryptogram - online)
            else if (p1 == 0x00) {
                currentVerdict = Verdict.REQUIRES_ONLINE;
                return currentVerdict;
            }
            // P1 = 0x40 → Request AAC (Application Authentication Cryptogram - declined)
            else if (p1 == 0x40) {
                currentVerdict = Verdict.DECLINED;
                return currentVerdict;
            }
        }

        return null;
    }

    /**
     * Finalize analysis - if no verdict yet but GPO seen, mark as incomplete
     */
    public Verdict finalizeAnalysis() {
        if (currentVerdict != Verdict.UNKNOWN) {
            return currentVerdict;
        }
        if (seenGpo && !seenGenerateAc) {
            return Verdict.INCOMPLETE;
        }
        return Verdict.UNKNOWN;
    }

    public Verdict getCurrentVerdict() {
        return currentVerdict;
    }

    public String getSelectedAid() {
        return selectedAid;
    }

    public boolean hasSeenSelect() {
        return seenSelect;
    }

    public boolean hasSeenGpo() {
        return seenGpo;
    }

    public boolean hasSeenGenerateAc() {
        return seenGenerateAc;
    }

    public byte getLastP1() {
        return lastP1;
    }

    public String getTransactionSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("Transaction Summary:\n");
        sb.append("  SELECT: ").append(seenSelect ? "✓" : "✗").append("\n");
        sb.append("  AID: ").append(selectedAid.isEmpty() ? "N/A" : selectedAid).append("\n");
        sb.append("  GPO: ").append(seenGpo ? "✓" : "✗").append("\n");
        sb.append("  GENERATE AC: ").append(seenGenerateAc ? "✓" : "✗").append("\n");
        if (seenGenerateAc) {
            sb.append("  P1 Value: 0x").append(String.format("%02X", lastP1 & 0xFF)).append("\n");
        }
        return sb.toString();
    }
}
