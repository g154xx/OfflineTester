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

    public static byte[] buildFciTemplate(String aidHex, byte[] proprietary) {
        TlvBuilder inner = new TlvBuilder();
        inner.add("84", aidHex);
        if (proprietary != null && proprietary.length > 0) {
            inner.add("A5", proprietary);
        }
        return wrap("6F", inner.build());
    }

    public static byte[] buildFciProprietary() {
        TlvBuilder prop = new TlvBuilder();
        prop.add("50", "4465626974204D617374657263617264");  // "Debit Mastercard"
        prop.add("87", "01");
        prop.add("5F2D", "726F656E");                          // "roen"

        TlvBuilder bf0c = new TlvBuilder();
        bf0c.add("9F4D", "0B0A");
        bf0c.add("9F6E", "06420000303000");
        bf0c.add("9F0A", "00010104");
        prop.add("BF0C", bf0c.build());

        return prop.build();
    }

    public static byte[] buildPseFci(String pseNameHex) {
        TlvBuilder entries = new TlvBuilder();

        TlvBuilder mc = new TlvBuilder();
        mc.add("4F", "A0000000041010");
        mc.add("87", "01");
        mc.add("9F0A", "00010104");
        entries.add("61", mc.build());

        byte[] bf0c = wrap("BF0C", entries.build());

        TlvBuilder inner = new TlvBuilder();
        inner.add("84", pseNameHex);
        inner.add("A5", bf0c);
        return wrap("6F", inner.build());
    }

    public static byte[] buildGpoResponse() {
        TlvBuilder inner = new TlvBuilder();
        inner.add("82", "1980");
        inner.add("94", "1001010120010400");
        return wrap("77", inner.build());
    }

    /**
     * GENERATE AC Response cu TC (offline approved).
     */
    public static byte[] buildGenerateAcResponse_TC() {
        TlvBuilder resp = new TlvBuilder();
        resp.add("9F27", "80");           // TC
        resp.add("9F36", "003B");         // ATC
        resp.add("9F10", "06011203A020000F0400"); // IAD
        resp.add("9F37", "12345678");     // UN
        resp.add("9F34", "1F0302");       // CVM Results
        return wrap("77", resp.build());
    }

    /**
     * GENERATE AC Response cu ARQC (online request).
     */
    public static byte[] buildGenerateAcResponse_ARQC() {
        TlvBuilder resp = new TlvBuilder();
        resp.add("9F27", "00");           // ARQC (bit 8,7,6 = 0)
        resp.add("9F36", "003B");
        resp.add("9F10", "06011203A020000F0400");
        resp.add("9F37", "87654321");
        resp.add("9F34", "1F0302");
        return wrap("77", resp.build());
    }

    /**
     * GENERATE AC Response cu AAC (declined).
     * 9F27 = 0x00 - cod AAC.
     */
    public static byte[] buildGenerateAcResponse_AAC() {
        TlvBuilder resp = new TlvBuilder();
        resp.add("9F27", "00");           // AAC
        resp.add("9F36", "003B");
        resp.add("9F10", "06011203A020000F0400");
        resp.add("9F37", "12345678");
        resp.add("9F34", "1F0302");
        return wrap("77", resp.build());
    }

    public static byte[] sw_OK() {
        return new byte[]{(byte) 0x90, (byte) 0x00};
    }
}