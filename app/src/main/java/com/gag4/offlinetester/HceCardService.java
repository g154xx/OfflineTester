package com.gag4.offlinetester;

import android.content.Context;
import android.nfc.cardemulation.HostApduService;
import android.os.Bundle;

import java.util.HashMap;
import java.util.Map;

/**
 * HCE Card Service - Emulates a contactless card that supports offline mode.
 *
 * Handles:
 * - SELECT (AID selection)
 * - GET PROCESSING OPTIONS (GPO)
 * - GENERATE AC (with P1=0x80 for TC offline)
 * - READ RECORD (for card data)
 */
public class HceCardService extends HostApduService {

    private static final String TAG = "HceCardService";

    // Response map: APDU command (hex) -> Response (hex)
    private static final Map<String, byte[]> RESPONSE_MAP = new HashMap<>();

    private ApduAnalyzer analyzer;
    private ApduLogger logger;

    static {
        buildResponseMap();
    }

    private static void buildResponseMap() {
        // ===== SELECT PSE (Payment System Environment) =====
        RESPONSE_MAP.put(
                "00A404000E325041592E5359532E444446303031",
                concat(
                        HexUtils.fromHex("6F2A840E325041592E5359532E4444463031A518BF0C1561134F07A00000000410108701019F0A04000101049F38069F1A029F0206"),
                        new byte[]{(byte) 0x90, (byte) 0x00}
                )
        );

        // ===== SELECT Mastercard =====
        RESPONSE_MAP.put(
                "00A4040007A000000004101000",
                concat(
                        buildSelectResponse("A0000000041010", "Mastercard"),
                        new byte[]{(byte) 0x90, (byte) 0x00}
                )
        );

        // ===== SELECT Maestro =====
        RESPONSE_MAP.put(
                "00A4040007A000000043060000",
                concat(
                        buildSelectResponse("A0000000043060", "Maestro"),
                        new byte[]{(byte) 0x90, (byte) 0x00}
                )
        );

        // ===== SELECT Visa =====
        RESPONSE_MAP.put(
                "00A4040007A000000003101000",
                concat(
                        buildSelectResponse("A0000000031010", "Visa"),
                        new byte[]{(byte) 0x90, (byte) 0x00}
                )
        );

        // ===== SELECT Visa Electron =====
        RESPONSE_MAP.put(
                "00A4040007A000000032010000",
                concat(
                        buildSelectResponse("A0000000032010", "Visa Electron"),
                        new byte[]{(byte) 0x90, (byte) 0x00}
                )
        );

        // ===== SELECT American Express =====
        RESPONSE_MAP.put(
                "00A4040007A000000025010000",
                concat(
                        buildSelectResponse("A0000000025010", "AmEx"),
                        new byte[]{(byte) 0x90, (byte) 0x00}
                )
        );

        // ===== SELECT Diners Club =====
        RESPONSE_MAP.put(
                "00A4040007A000000036000000",
                concat(
                        buildSelectResponse("A0000000036000", "Diners"),
                        new byte[]{(byte) 0x90, (byte) 0x00}
                )
        );

        // ===== SELECT JCB =====
        RESPONSE_MAP.put(
                "00A4040007A000000065101000",
                concat(
                        buildSelectResponse("A0000000651010", "JCB"),
                        new byte[]{(byte) 0x90, (byte) 0x00}
                )
        );

        // ===== GET PROCESSING OPTIONS (GPO) =====
        RESPONSE_MAP.put(
                "80A8000013",  // GPO with various PDOL values (prefix)
                concat(
                        TlvBuilder.buildGpoResponse(),
                        new byte[]{(byte) 0x90, (byte) 0x00}
                )
        );

        // ===== GENERATE AC P1=0x80 (TC - Transaction Certificate, offline) =====
        RESPONSE_MAP.put(
                "80AE80",  // GENERATE AC with P1=0x80 (TC request)
                concat(
                        TlvBuilder.buildGenerateAcResponse_TC(),
                        new byte[]{(byte) 0x90, (byte) 0x00}
                )
        );

        // ===== GENERATE AC P1=0x00 (ARQC - online request) =====
        RESPONSE_MAP.put(
                "80AE00",  // GENERATE AC with P1=0x00 (ARQC request)
                concat(
                        TlvBuilder.buildGenerateAcResponse_ARQC(),
                        new byte[]{(byte) 0x90, (byte) 0x00}
                )
        );

        // ===== GENERATE AC P1=0x40 (AAC - declined) =====
        RESPONSE_MAP.put(
                "80AE40",  // GENERATE AC with P1=0x40
                concat(
                        HexUtils.fromHex("771A9F270809876543210ABCDEF9F10060600000000"),
                        new byte[]{(byte) 0x90, (byte) 0x00}
                )
        );

        // ===== READ RECORD SFI=2, record=1 =====
        RESPONSE_MAP.put(
                "00B2011400",
                concat(
                        HexUtils.fromHex("70819E9F420209465F24033203315A0853998207019456015F3401009F0702FFC09F080200028C279F02069F03069F1A0295055F2A029A039C019F37049F35019F45029F4C08"),
                        new byte[]{(byte) 0x90, (byte) 0x00}
                )
        );
    }

    @Override
    public void onCreate() {
        super.onCreate();
        analyzer = new ApduAnalyzer();
        logger = ApduLogger.getInstance();
        logger.init(this);
    }

    @Override
    public byte[] processCommandApdu(byte[] commandApdu, Bundle extras) {
        if (commandApdu == null) {
            return TlvBuilder.sw((byte) 0x69, (byte) 0x85);
        }

        String apduHex = HexUtils.toHex(commandApdu).toUpperCase();

        // Log RX
        logger.log(this, LogEntry.Direction.RX, apduHex, "POS trimite APDU");

        // Analyze
        analyzer.analyzeTx(commandApdu);

        // Look for exact match first
        if (RESPONSE_MAP.containsKey(apduHex)) {
            byte[] response = RESPONSE_MAP.get(apduHex);
            logger.log(this, LogEntry.Direction.TX, HexUtils.toHex(response), "Răspuns gasit");
            return response;
        }

        // Try prefix match for GPO and GENERATE AC (variable data)
        for (String key : RESPONSE_MAP.keySet()) {
            if (apduHex.startsWith(key)) {
                byte[] response = RESPONSE_MAP.get(key);
                logger.log(this, LogEntry.Direction.TX, HexUtils.toHex(response), "Răspuns prefix match");
                return response;
            }
        }

        // Default: conditions not satisfied (0x6985)
        byte[] defaultResp = new byte[]{(byte) 0x69, (byte) 0x85};
        logger.log(this, LogEntry.Direction.TX, HexUtils.toHex(defaultResp), "APDU nerecunoscut");
        return defaultResp;
    }

    @Override
    public void onDeactivated(int reason) {
        analyzer.reset();
    }

    // ===== Helper methods =====

    private static byte[] buildSelectResponse(String aidHex, String cardName) {
        // FCI Template with proprietary data
        byte[] proprietary = TlvBuilder.buildFciProprietary();
        byte[] fci = TlvBuilder.buildFciTemplate(aidHex, proprietary);
        return fci;
    }

    private static byte[] concat(byte[] a, byte[] b) {
        if (a == null) return b;
        if (b == null) return a;
        byte[] result = new byte[a.length + b.length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }
}
