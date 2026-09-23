package com.gag4.offlinetester;

import android.nfc.cardemulation.HostApduService;
import android.os.Bundle;

public class HceCardService extends HostApduService {

    private static final String TAG = "HceCardService";

    private static final String PSE_PPSE = "325041592E5359532E4444463031";    // 2PAY.SYS.DDF01
    private static final String PSE_CONTACT = "315041592E5359532E4444463031"; // 1PAY.SYS.DDF01

    private ApduLogger logger;

    @Override
    public void onCreate() {
        super.onCreate();
        logger = ApduLogger.getInstance();
        logger.init(this);
        android.util.Log.d(TAG, "HceCardService created");
    }

    @Override
    public byte[] processCommandApdu(byte[] commandApdu, Bundle extras) {
        if (commandApdu == null || commandApdu.length < 4) {
            return new byte[]{(byte) 0x69, (byte) 0x85};
        }

        try {
            String apduHex = HexUtils.toHex(commandApdu).toUpperCase();
            android.util.Log.d(TAG, "Received APDU: " + apduHex);

            // TX = APDU primit de la POS
            logger.log(this, LogEntry.Direction.TX, apduHex, "POS trimite APDU");

            byte[] response = handleApdu(commandApdu);

            // RX = Răspuns trimis către POS
            logger.log(this, LogEntry.Direction.RX, HexUtils.toHex(response), "Răspuns trimis");

            return response;

        } catch (Exception e) {
            android.util.Log.e(TAG, "Error processing APDU", e);
            return new byte[]{(byte) 0x69, (byte) 0x85};
        }
    }

    private byte[] handleApdu(byte[] commandApdu) {
        int ins = commandApdu[1] & 0xFF;
        int p1 = commandApdu[2] & 0xFF;
        int p2 = commandApdu[3] & 0xFF;
        int lc = (commandApdu.length > 4) ? (commandApdu[4] & 0xFF) : 0;

        // SELECT (0xA4)
        if (ins == 0xA4 && p1 == 0x04 && p2 == 0x00 && lc > 0
                && commandApdu.length >= 5 + lc) {
            byte[] aid = new byte[lc];
            System.arraycopy(commandApdu, 5, aid, 0, lc);
            return handleSelect(aid);
        }

        // GPO (0xA8)
        if (ins == 0xA8) {
            return concat(TlvBuilder.buildGpoResponse(), TlvBuilder.sw_OK());
        }

        // GENERATE AC (0xAE)
        if (ins == 0xAE) {
            return handleGenerateAc(p1);
        }

        // READ RECORD (0xB2)
        if (ins == 0xB2) {
            int sfi = (p2 >> 3) & 0x1F;   // fix bug #13
            int recordNum = p1;
            return handleReadRecord(sfi, recordNum);
        }

        return new byte[]{(byte) 0x6A, (byte) 0x82};
    }

    private byte[] handleSelect(byte[] aid) {
        String aidHex = HexUtils.toHex(aid).toUpperCase();
        android.util.Log.d(TAG, "SELECT: " + aidHex);

        // SELECT PSE
        if (PSE_PPSE.equals(aidHex) || PSE_CONTACT.equals(aidHex)) {
            byte[] pseFci = TlvBuilder.buildPseFci(aidHex);
            return concat(pseFci, TlvBuilder.sw_OK());
        }

        // SELECT AID
        byte[] proprietary = TlvBuilder.buildFciProprietary();
        byte[] fci = TlvBuilder.buildFciTemplate(aidHex, proprietary);
        return concat(fci, TlvBuilder.sw_OK());
    }

    private byte[] handleGenerateAc(int p1) {
        android.util.Log.d(TAG, "GENERATE AC P1=0x" + String.format("%02X", p1));

        byte[] cryptogram;
        if (p1 == 0x80) {
            cryptogram = TlvBuilder.buildGenerateAcResponse_TC();
        } else if (p1 == 0x00) {
            cryptogram = TlvBuilder.buildGenerateAcResponse_ARQC();
        } else if (p1 == 0x40) {
            cryptogram = TlvBuilder.buildGenerateAcResponse_ARQC();
        } else {
            return new byte[]{(byte) 0x6A, (byte) 0x80};
        }

        return concat(cryptogram, TlvBuilder.sw_OK());
    }

private byte[] handleReadRecord(int sfi, int recordNum) {
    android.util.Log.d(TAG, "READ RECORD SFI=" + sfi + " REC=" + recordNum);

    TlvBuilder record = new TlvBuilder();

    if (sfi == 1) {
        // SFI 1 - Primary Account Data
        record.add("5A", "5312570022406247");
        record.add("5F24", "271020");
        record.add("5F34", "01");
        record.add("5F28", "0642");
        record.add("9F07", "FFFF");
    } else if (sfi == 2) {
        // SFI 2 - Track 2 Equivalent Data
        record.add("57", "5312570022406247D27102010000000000000000");
    } else if (sfi == 4) {
        // SFI 4 - CDOLs + CVM
        record.add("8C", "9F02069F03069F1A0295055F2A029A039C019F37049F35019F45029F4C089F3403");
        record.add("8D", "910A8A0295059F37049F4C08");
        record.add("9F34", "1F0302");
    } else {
        record.add("5A", "5312570022406247");
    }

    byte[] recordBody = TlvBuilder.wrap("70", record.build());
    return concat(recordBody, TlvBuilder.sw_OK());
}
    @Override
    public void onDeactivated(int reason) {
        android.util.Log.d(TAG, "HCE deactivated. Reason: " + reason);
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