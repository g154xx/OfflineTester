package com.gag4.offlinetester;

import android.content.Context;
import android.nfc.cardemulation.HostApduService;
import android.os.Bundle;

import java.util.HashMap;
import java.util.Map;

/**
 * HCE Card Service - Emulates a contactless card that supports offline mode.
 * 
 * FIXES:
 * - Proper AID matching (no trailing zeros)
 * - Flexible APDU matching
 * - Better error handling
 */
public class HceCardService extends HostApduService {

    private static final String TAG = "HceCardService";
    private ApduAnalyzer analyzer;
    private ApduLogger logger;

    @Override
    public void onCreate() {
        super.onCreate();
        analyzer = new ApduAnalyzer();
        logger = ApduLogger.getInstance();
        logger.init(this);
        android.util.Log.d(TAG, "HceCardService created");
    }

    @Override
    public byte[] processCommandApdu(byte[] commandApdu, Bundle extras) {
        if (commandApdu == null || commandApdu.length < 4) {
            return sw((byte) 0x69, (byte) 0x85);
        }

        try {
            String apduHex = HexUtils.toHex(commandApdu).toUpperCase();
            android.util.Log.d(TAG, "Received APDU: " + apduHex);

            // Log RX
            logger.log(this, LogEntry.Direction.RX, apduHex, "POS trimite APDU");

            // Analyze for verdict
            analyzer.analyzeTx(commandApdu);

            byte[] response = handleApdu(commandApdu, apduHex);

            // Log TX
            logger.log(this, LogEntry.Direction.TX, HexUtils.toHex(response), "Răspuns trimis");

            return response;

        } catch (Exception e) {
            android.util.Log.e(TAG, "Error processing APDU", e);
            return sw((byte) 0x69, (byte) 0x85);
        }
    }

    private byte[] handleApdu(byte[] commandApdu, String apduHex) {
        int ins = commandApdu[1] & 0xFF;
        int p1 = commandApdu[2] & 0xFF;
        int p2 = commandApdu[3] & 0xFF;
        int lc = (commandApdu.length > 4) ? (commandApdu[4] & 0xFF) : 0;

        // SELECT (0xA4)
        if (ins == 0xA4 && p1 == 0x04 && p2 == 0x00) {
            if (lc > 0 && commandApdu.length > 5) {
                byte[] aid = new byte[lc];
                System.arraycopy(commandApdu, 5, aid, 0, lc);
                return handleSelect(aid);
            }
        }

        // GET PROCESSING OPTIONS (0xA8)
        if (ins == 0xA8) {
            return handleGpo(commandApdu);
        }

        // GENERATE AC (0xAE)
        if (ins == 0xAE) {
            return handleGenerateAc(p1);
        }

        // READ RECORD (0xB2)
        if (ins == 0xB2) {
            return handleReadRecord(p1, p2);
        }

        // Default: unrecognized
        android.util.Log.w(TAG, "Unrecognized APDU: INS=0x" + String.format("%02X", ins));
        return sw((byte) 0x6A, (byte) 0x82); // File not found (safer than 0x6985)
    }

    private byte[] handleSelect(byte[] aid) {
        String aidHex = HexUtils.toHex(aid).toUpperCase();
        android.util.Log.d(TAG, "SELECT AID: " + aidHex);

        // Build FCI response
        byte[] proprietary = TlvBuilder.buildFciProprietary();
        byte[] fci = TlvBuilder.buildFciTemplate(aidHex, proprietary);

        return concat(fci, sw((byte) 0x90, (byte) 0x00));
    }

    private byte[] handleGpo(byte[] commandApdu) {
        android.util.Log.d(TAG, "Received GPO");
        return concat(TlvBuilder.buildGpoResponse(), sw((byte) 0x90, (byte) 0x00));
    }

    private byte[] handleGenerateAc(int p1) {
        android.util.Log.d(TAG, "GENERATE AC with P1=0x" + String.format("%02X", p1));

        byte[] cryptogram;
        if (p1 == 0x80) {
            // Request TC (offline) - VERDICT: SUPPORTS OFFLINE
            cryptogram = TlvBuilder.buildGenerateAcResponse_TC();
        } else if (p1 == 0x00) {
            // Request ARQC (online)
            cryptogram = TlvBuilder.buildGenerateAcResponse_ARQC();
        } else if (p1 == 0x40) {
            // Request AAC (declined)
            cryptogram = HexUtils.fromHex("771A9F270809876543210ABCDEF9F10060600000000");
        } else {
            // Unknown P1
            return sw((byte) 0x6A, (byte) 0x80);
        }

        return concat(cryptogram, sw((byte) 0x90, (byte) 0x00));
    }

    private byte[] handleReadRecord(int sfi, int recordNum) {
        android.util.Log.d(TAG, "READ RECORD SFI=" + sfi + " REC=" + recordNum);
        
        // Return mock record
        byte[] record = HexUtils.fromHex("70819E9F420209465F24033203315A0853998207019456015F3401009F0702FFC09F080200028C279F02069F03069F1A0295055F2A029A039C019F37049F35019F45029F4C08");
        return concat(record, sw((byte) 0x90, (byte) 0x00));
    }

    @Override
    public void onDeactivated(int reason) {
        analyzer.reset();
        android.util.Log.d(TAG, "HCE deactivated. Reason: " + reason);
    }

    // ===== Helper methods =====

    private byte[] sw(byte sw1, byte sw2) {
        return new byte[]{sw1, sw2};
    }

    private byte[] concat(byte[] a, byte[] b) {
        if (a == null) return b;
        if (b == null) return a;
        byte[] result = new byte[a.length + b.length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }
}
