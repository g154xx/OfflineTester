package com.gag4.offlinetester;

import java.util.HashMap;
import java.util.Map;

public class TlvParser {

    public static class TlvObject {
        public String tag;
        public byte[] value;

        public TlvObject(String tag, byte[] value) {
            this.tag = tag;
            this.value = value;
        }
    }

    // Parse TLV data and return map of tag -> value
    public static Map<String, byte[]> parse(byte[] data) {
        Map<String, byte[]> result = new HashMap<>();
        if (data == null || data.length == 0) return result;

        int i = 0;
        while (i < data.length) {
            // Read tag
            String tag = "";
            int tagLen = 1;

            // Multi-byte tag detection (0x1F indicates multi-byte)
            if ((data[i] & 0x1F) == 0x1F) {
                tagLen = 2; // Usually 2 bytes for EMV
                if (i + 1 < data.length) {
                    tag = String.format("%02X%02X", data[i], data[i + 1]);
                    i += 2;
                } else {
                    break;
                }
            } else {
                tag = String.format("%02X", data[i]);
                i++;
            }

            // Read length
            if (i >= data.length) break;
            int len = data[i] & 0xFF;
            i++;

            // Handle extended length (0x81 or 0x82)
            if (len == 0x81 && i < data.length) {
                len = data[i] & 0xFF;
                i++;
            } else if (len == 0x82 && i + 1 < data.length) {
                len = ((data[i] & 0xFF) << 8) | (data[i + 1] & 0xFF);
                i += 2;
            }

            // Read value
            if (i + len > data.length) break;
            byte[] value = new byte[len];
            System.arraycopy(data, i, value, 0, len);
            i += len;

            result.put(tag, value);
        }

        return result;
    }

    public static String interpretStatusCode(byte sw1, byte sw2) {
        int status = ((sw1 & 0xFF) << 8) | (sw2 & 0xFF);

        if (status == 0x9000) return "OK (0x9000)";
        if (status == 0x6101) return "More data available (0x6101)";
        if (status == 0x6982) return "Security error - auth method blocked (0x6982)";
        if (status == 0x6985) return "Conditions not satisfied (0x6985)";
        if (status == 0x6A80) return "Incorrect parameters (0x6A80)";
        if (status == 0x6A81) return "Function not supported (0x6A81)";
        if (status == 0x6A82) return "File not found (0x6A82)";
        if (status == 0x6D00) return "Instruction not supported (0x6D00)";
        if (status == 0x6E00) return "Class not supported (0x6E00)";

        return String.format("Error (0x%04X)", status);
    }

    public static String getTagDescription(String tag) {
        switch (tag.toUpperCase()) {
            case "4F":
                return "AID";
            case "50":
                return "Application Label";
            case "56":
                return "IAD";
            case "57":
                return "Track 2 Data";
            case "5A":
                return "PAN";
            case "5F20":
                return "PAN Sequence Number";
            case "5F34":
                return "CVM List";
            case "6F":
                return "FCI Template";
            case "70":
                return "FCI";
            case "77":
                return "Response Message Template (Format 2)";
            case "80":
                return "Response Message Template (Format 1)";
            case "84":
                return "DF Name";
            case "95":
                return "Terminal Verification Results";
            case "9A":
                return "Transaction Date";
            case "9C":
                return "Transaction Type";
            case "9F02":
                return "Amount, Authorised";
            case "9F03":
                return "Amount, Other";
            case "9F07":
                return "AUC / AIP";
            case "9F08":
                return "Application Dedicated File";
            case "9F0A":
                return "IAD";
            case "9F10":
                return "IAD";
            case "9F12":
                return "Application Preferred Name";
            case "9F1A":
                return "Terminal Country Code";
            case "9F1E":
                return "IFD Serial Number";
            case "9F26":
                return "Cryptogram";
            case "9F27":
                return "Cryptogram (ICC)";
            case "9F2D":
                return "CVC3";
            case "9F33":
                return "Terminal Capabilities";
            case "9F34":
                return "CVM Result";
            case "9F35":
                return "Terminal Type";
            case "9F37":
                return "Unpredictable Number";
            case "9F38":
                return "PDOL";
            case "9F42":
                return "Currency Code";
            case "9F5B":
                return "ISSUER SCRIPT";
            case "A5":
                return "FCI Proprietary Template";
            case "BF0C":
                return "FCI Issuer Discretionary Data";
            default:
                return "Unknown Tag";
        }
    }

    public static void printTlvDebug(byte[] data) {
        Map<String, byte[]> tlv = parse(data);
        for (String tag : tlv.keySet()) {
            byte[] value = tlv.get(tag);
            String hex = HexUtils.toHex(value);
            String desc = getTagDescription(tag);
            android.util.Log.d("TLV", "Tag " + tag + " (" + desc + "): " + hex);
        }
    }
}
