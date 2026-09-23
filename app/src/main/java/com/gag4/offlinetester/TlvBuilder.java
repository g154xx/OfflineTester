package com.gag4.offlinetester;

import java.util.ArrayList;
import java.util.List;

public class TlvBuilder {

    private final List<byte[]> elements = new ArrayList<>();

    public TlvBuilder add(String tag, String hexData) {
        if (hexData == null) return this;
        String clean = hexData.replaceAll("\\s+", "");
        return add(HexUtils.fromHex(tag), HexUtils.fromHex(clean));
    }

    public TlvBuilder add(String tag, byte[] data) {
        return add(HexUtils.fromHex(tag), data);
    }

    public TlvBuilder add(byte[] tag, byte[] data) {
        if (tag == null || data == null) return this;
        byte[] lenBytes = encodeLength(data.length);
        byte[] tlv = new byte[tag.length + lenBytes.length + data.length];
        System.arraycopy(tag, 0, tlv, 0, tag.length);
        System.arraycopy(lenBytes, 0, tlv, tag.length, lenBytes.length);
        System.arraycopy(data, 0, tlv, tag.length + lenBytes.length, data.length);
        elements.add(tlv);
        return this;
    }

    public static byte[] encodeLength(int len) {
        if (len < 0x80) {
            return new byte[]{(byte) len};
        } else if (len < 0x100) {
            return new byte[]{(byte) 0x81, (byte) len};
        } else {
            return new byte[]{(byte) 0x82, (byte) ((len >> 8) & 0xFF), (byte) (len & 0xFF)};
        }
    }

    public byte[] build() {
        int totalLen = 0;
        for (byte[] elem : elements) totalLen += elem.length;
        byte[] result = new byte[totalLen];
        int offset = 0;
        for (byte[] elem : elements) {
            System.arraycopy(elem, 0, result, offset, elem.length);
            offset += elem.length;
        }
        return result;
    }

    public static byte[] wrap(String tagHex, byte[] data) {
        if (data == null) return new byte[0];
        byte[] tag = HexUtils.fromHex(tagHex);
        byte[] lenBytes = encodeLength(data.length);
        byte[] result = new byte[tag.length + lenBytes.length + data.length];
        System.arraycopy(tag, 0, result, 0, tag.length);
        System.arraycopy(lenBytes, 0, result, tag.length, lenBytes.length);
        System.arraycopy(data, 0, result, tag.length + lenBytes.length, data.length);
        return result;
    }

    /**
     * FCI Template pentru SELECT AID.
     */
    public static byte[] buildFciTemplate(String aidHex, byte[] proprietary) {
        TlvBuilder inner = new TlvBuilder();
        inner.add("84", aidHex);
        if (proprietary != null && proprietary.length > 0) {
            inner.add("A5", proprietary);
        }
        return wrap("6F", inner.build());
    }

    /**
     * Conținut FCI Proprietary Template (pentru tag A5).
     * Include BF 0C cu 9F 4D + 9F 6E + 9F 0A - esențiale pentru POS-uri pretențioase.
     * AUC (9F 07) a fost eliminat - nu aparține în FCI.
     */
    public static byte[] buildFciProprietary() {
        TlvBuilder prop = new TlvBuilder();

        // 88 - SFI Directory
        prop.add("88", "01");

        // 50 - Application Label
        prop.add("50", "48434520454D554C41544F52");  // "HCE EMULATOR"

        // 87 - Application Priority Indicator
        prop.add("87", "01");

        // 5F 2D - Language Preference
        prop.add("5F2D", "726F656E");  // "roen"

        // 9F 11 - Issuer Code Table Index
        prop.add("9F11", "01");

        // 9F 12 - Application Preferred Name
        prop.add("9F12", "48434520454D554C41544F52");

        // 9F 38 - PDOL (Country Code 2B + Amount 6B)
        prop.add("9F38", "9F1A029F0206");

        // BF 0C - FCI Issuer Discretionary Data (CRITIC!)
        // Conține 9F 4D, 9F 6E, 9F 0A - identice cu cele de pe cardul tău real
        TlvBuilder bf0c = new TlvBuilder();
        bf0c.add("9F4D", "0B0A");                    // Log Entry (2 bytes)
        bf0c.add("9F6E", "064200 00303000");         // Form Factor Indicator
        bf0c.add("9F0A", "00010104");                // Application Selection Registered Proprietary Data
        prop.add("BF0C", bf0c.build());

        return prop.build();
    }

    /**
     * Răspuns complet FCI pentru SELECT PSE (2PAY.SYS.DDF01 / 1PAY.SYS.DDF01).
     */
    public static byte[] buildPseFci(String pseNameHex) {
        TlvBuilder entries = new TlvBuilder();

        // Mastercard entry
        TlvBuilder mc = new TlvBuilder();
        mc.add("4F", "A0000000041010");
        mc.add("50", "4D617374657263617264");  // "Mastercard"
        mc.add("87", "01");
        entries.add("61", mc.build());

        // Visa entry
        TlvBuilder visa = new TlvBuilder();
        visa.add("4F", "A0000000031010");
        visa.add("50", "56697361");              // "Visa"
        visa.add("87", "02");
        entries.add("61", visa.build());

        byte[] bf0c = wrap("BF0C", entries.build());

        TlvBuilder inner = new TlvBuilder();
        inner.add("84", pseNameHex);
        inner.add("A5", bf0c);
        return wrap("6F", inner.build());
    }

    /**
     * GPO Response (Format 1 - tag 80, AIP + AFL).
     */
    public static byte[] buildGpoResponse() {
        // AIP: bit 6 = CVM offline, bit 5 = CVM performed
        byte[] aip = new byte[]{(byte) 0x3C, (byte) 0x00};
        // AFL: SFI=2, record 1-1
        byte[] afl = new byte[]{(byte) 0x10, (byte) 0x01, (byte) 0x01, (byte) 0x00};

        byte[] data = new byte[aip.length + afl.length];
        System.arraycopy(aip, 0, data, 0, aip.length);
        System.arraycopy(afl, 0, data, aip.length, afl.length);

        return wrap("80", data);
    }

    /**
     * GENERATE AC cu TC (offline approved).
     */
    public static byte[] buildGenerateAcResponse_TC() {
        TlvBuilder resp = new TlvBuilder();
        resp.add("9F27", "80");           // TC
        resp.add("9F36", "0004");         // ATC
        resp.add("9F10", "0600000000");   // IAD
        resp.add("9F37", "12345678");     // UN
        resp.add("9F34", "1F0002");       // CVM Results
        return wrap("77", resp.build());
    }

    /**
     * GENERATE AC cu ARQC (online request).
     */
    public static byte[] buildGenerateAcResponse_ARQC() {
        TlvBuilder resp = new TlvBuilder();
        resp.add("9F27", "00");           // ARQC
        resp.add("9F36", "0004");         // ATC
        resp.add("9F10", "0601000000");   // IAD
        resp.add("9F37", "87654321");     // UN
        resp.add("9F34", "1F0002");       // CVM Results
        return wrap("77", resp.build());
    }

    public static byte[] sw_OK() {
        return new byte[]{(byte) 0x90, (byte) 0x00};
    }
}