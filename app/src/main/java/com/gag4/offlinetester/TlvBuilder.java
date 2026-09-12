package com.gag4.offlinetester;

import java.util.ArrayList;
import java.util.List;

public class TlvBuilder {

    private List<byte[]> elements = new ArrayList<>();

    public TlvBuilder add(String tag, String hexData) {
        return add(HexUtils.fromHex(tag), HexUtils.fromHex(hexData));
    }

    public TlvBuilder add(String tag, byte[] data) {
        return add(HexUtils.fromHex(tag), data);
    }

    public TlvBuilder add(byte[] tag, byte[] data) {
        if (tag == null || data == null) return this;

        byte[] tlv = new byte[tag.length + 1 + data.length];
        System.arraycopy(tag, 0, tlv, 0, tag.length);
        tlv[tag.length] = (byte) data.length;
        System.arraycopy(data, 0, tlv, tag.length + 1, data.length);

        elements.add(tlv);
        return this;
    }

    public byte[] build() {
        int totalLen = 0;
        for (byte[] elem : elements) {
            totalLen += elem.length;
        }

        byte[] result = new byte[totalLen];
        int offset = 0;
        for (byte[] elem : elements) {
            System.arraycopy(elem, 0, result, offset, elem.length);
            offset += elem.length;
        }
        return result;
    }

    // Helper: build FCI Template (6F) with contents
    public static byte[] buildFciTemplate(String aidHex, byte[] proprietary) {
        TlvBuilder fci = new TlvBuilder();

        // Tag 84 - AID
        fci.add("84", aidHex);

        // Tag A5 - FCI Proprietary Template (contains proprietary data)
        if (proprietary != null) {
            byte[] a5tlv = new byte[2 + proprietary.length];
            a5tlv[0] = (byte) 0xA5;
            a5tlv[1] = (byte) proprietary.length;
            System.arraycopy(proprietary, 0, a5tlv, 2, proprietary.length);
            fci.elements.add(a5tlv);
        }

        return fci.build();
    }

    // Helper: build FCI Proprietary contents (for A5)
    public static byte[] buildFciProprietary() {
        TlvBuilder prop = new TlvBuilder();

        // 9F38 - PDOL (Processing Data Object List)
        // Specifies: Terminal Country Code (2 bytes) + Amount (6 bytes)
        prop.add("9F38", "9F1A029F0206");

        // 9F07 - AUC (Application Usage Control) - allows offline
        prop.add("9F07", "FFFF");

        // 9F12 - App Preferred Name (optional)
        prop.add("9F12", "48435 46A4D554C41544F52");  // "HCE EMULATOR"

        return prop.build();
    }

    // Helper: build GPO Response with AIP
    public static byte[] buildGpoResponse() {
        TlvBuilder resp = new TlvBuilder();

        // AIP 0x3C00 - Bit 6 set (CVM offline possible)
        // Format: Tag 80 (response template 1)
        byte[] aip = new byte[]{(byte) 0x3C, (byte) 0x00};

        // AFL - Application File Locator
        byte[] afl = new byte[]{(byte) 0x10, (byte) 0x02, (byte) 0x01, (byte) 0x00};

        // Build as Tag 80 + Length + Data
        byte[] data = new byte[aip.length + afl.length];
        System.arraycopy(aip, 0, data, 0, aip.length);
        System.arraycopy(afl, 0, data, aip.length, afl.length);

        byte[] result = new byte[2 + data.length];
        result[0] = (byte) 0x80;
        result[1] = (byte) data.length;
        System.arraycopy(data, 0, result, 2, data.length);

        return result;
    }

    // Helper: build GENERATE AC Response with TC (offline cryptogram)
    public static byte[] buildGenerateAcResponse_TC() {
        TlvBuilder resp = new TlvBuilder();

        // 9F27 - Cryptogram (ICC) - TC (Transaction Certificate)
        resp.add("9F27", "9876543210ABCDEF");

        // 9F10 - IAD (Issuer Application Data)
        resp.add("9F10", "0600000000");

        // 9F37 - Unpredictable Number (ATC)
        resp.add("9F37", "12345678");

        // 9F34 - CVM Result
        resp.add("9F34", "1F0002");

        byte[] data = resp.build();

        // Wrap in Tag 77 (Response Message Template Format 2)
        byte[] result = new byte[2 + data.length];
        result[0] = (byte) 0x77;
        result[1] = (byte) data.length;
        System.arraycopy(data, 0, result, 2, data.length);

        return result;
    }

    // Helper: build GENERATE AC Response with ARQC (online request)
    public static byte[] buildGenerateAcResponse_ARQC() {
        TlvBuilder resp = new TlvBuilder();

        // 9F27 - Cryptogram (ICC) - ARQC (Authorization Request Cryptogram)
        resp.add("9F27", "FEDCBA0987654321");

        // 9F10 - IAD (Issuer Application Data)
        resp.add("9F10", "0601000000");

        // 9F37 - Unpredictable Number
        resp.add("9F37", "87654321");

        // 9F34 - CVM Result
        resp.add("9F34", "1F0002");

        byte[] data = resp.build();

        // Wrap in Tag 77
        byte[] result = new byte[2 + data.length];
        result[0] = (byte) 0x77;
        result[1] = (byte) data.length;
        System.arraycopy(data, 0, result, 2, data.length);

        return result;
    }

    // Helper: build status word (9000 = OK, 6985 = conditions not satisfied, etc.)
    public static byte[] sw(byte sw1, byte sw2) {
        return new byte[]{sw1, sw2};
    }

    public static byte[] sw_OK() {
        return sw((byte) 0x90, (byte) 0x00);
    }

    public static byte[] sw_CONDITIONS_NOT_SAT() {
        return sw((byte) 0x69, (byte) 0x85);
    }

    public static byte[] sw_FUNCTION_NOT_SUPPORTED() {
        return sw((byte) 0x6A, (byte) 0x81);
    }

    public static byte[] sw_WRONG_PARAMS() {
        return sw((byte) 0x6A, (byte) 0x80);
    }
}
