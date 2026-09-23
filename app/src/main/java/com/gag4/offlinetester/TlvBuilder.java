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
     * FCI Template pentru SELECT AID (împachetat corect în 6F).
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
     * Versiune COMPLETĂ cu tag-urile pe care POS-urile le cer:
     * 88 - SFI Directory
     * 50 - Application Label
     * 9F12 - Application Preferred Name
     * 5F2D - Language Preference
     * 87 - Application Priority Indicator
     * 9F11 - Issuer Code Table Index
     * 9F38 - PDOL
     * 9F07 - Application Usage Control
     */
    public static byte[] buildFciProprietary() {
        TlvBuilder prop = new TlvBuilder();

        prop.add("88", "01");
        prop.add("50", "48434520454D554C41544F52");
        prop.add("9F12", "48434520454D554C41544F52");
        prop.add("5F2D", "726F656E");
        prop.add("87", "01");
        prop.add("9F11", "01");
        prop.add("9F38", "9F1A029F0206");
        prop.add("9F07", "FFFF");

        return prop.build();
    }

    /**
     * Răspuns complet FCI pentru SELECT PSE.
     */
    public static byte[] buildPseFci(String pseNameHex) {
        TlvBuilder entries = new TlvBuilder();

        TlvBuilder mc = new TlvBuilder();
        mc.add("4F", "A0000000041010");
        mc.add("50", "4D617374657263617264");
        mc.add("87", "01");
        entries.add("61", mc.build());

        TlvBuilder visa = new TlvBuilder();
        visa.add("4F", "A0000000031010");
        visa.add("50", "56697361");
        visa.add("87", "02");
        entries.add("61", visa.build());

        byte[] bf0c = wrap("BF0C", entries.build());

        TlvBuilder inner = new TlvBuilder();
        inner.add("84", pseNameHex);
        inner.add("A5", bf0c);
        return wrap("6F", inner.build());
    }

    /**
     * GPO Response (Format 1 - tag 80, AIP + AFL concat).
     */
    public static byte[] buildGpoResponse() {
        byte[] aip = new byte[]{(byte) 0x3C, (byte) 0x00};
        byte[] afl = new byte[]{(byte) 0x10, (byte) 0x01, (byte) 0x01, (byte) 0x00};

        byte[] data = new byte[aip.length + afl.length];
        System.arraycopy(aip, 0, data, 0, aip.length);
        System.arraycopy(afl, 0, data, aip.length, afl.length);

        return wrap("80", data);
    }

    /**
     * GENERATE AC Response cu TC (offline approved).
     */
    public static byte[] buildGenerateAcResponse_TC() {
        TlvBuilder resp = new TlvBuilder();
        resp.add("9F27", "80");
        resp.add("9F36", "0004");
        resp.add("9F10", "0600000000");
        resp.add("9F37", "12345678");
        resp.add("9F34", "1F0002");
        return wrap("77", resp.build());
    }

    /**
     * GENERATE AC Response cu ARQC (online request).
     */
    public static byte[] buildGenerateAcResponse_ARQC() {
        TlvBuilder resp = new TlvBuilder();
        resp.add("9F27", "00");
        resp.add("9F36", "0004");
        resp.add("9F10", "0601000000");
        resp.add("9F37", "87654321");
        resp.add("9F34", "1F0002");
        return wrap("77", resp.build());
    }

    public static byte[] sw_OK() {
        return new byte[]{(byte) 0x90, (byte) 0x00};
    }
}