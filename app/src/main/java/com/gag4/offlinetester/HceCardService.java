package com.gag4.offlinetester;

import android.nfc.cardemulation.HostApduService;
import android.os.Bundle;

public class HceCardService extends HostApduService {

    private static final String TAG = "HceCardService";

    private static final String PSE_PPSE = "325041592E5359532E4444463031";
    private static final String PSE_CONTACT = "315041592E5359532E4444463031";

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
            logger.log(this, LogEntry.Direction.TX, apduHex, "POS trimite APDU");

            byte[] response = handleApdu(commandApdu);

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

        if (ins == 0xA4 && p1 == 0x04 && p2 == 0x00 && lc > 0
                && commandApdu.length >= 5 + lc) {
            byte[] aid = new byte[lc];
            System.arraycopy(commandApdu, 5, aid, 0, lc);
            return handleSelect(aid);
        }

        if (ins == 0xA8) {
            return concat(TlvBuilder.buildGpoResponse(), TlvBuilder.sw_OK());
        }

        if (ins == 0xAE) {
            return handleGenerateAc(p1);
        }

        if (ins == 0xB2) {
            int sfi = (p2 >> 3) & 0x1F;
            return handleReadRecord(sfi, p1);
        }

        return new byte[]{(byte) 0x6A, (byte) 0x82};
    }

    private byte[] handleSelect(byte[] aid) {
        String aidHex = HexUtils.toHex(aid).toUpperCase();
        android.util.Log.d(TAG, "SELECT: " + aidHex);

        if (PSE_PPSE.equals(aidHex) || PSE_CONTACT.equals(aidHex)) {
            byte[] pseFci = TlvBuilder.buildPseFci(aidHex);
            return concat(pseFci, TlvBuilder.sw_OK());
        }

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

    /**
     * READ RECORD - date identice cu cardul Mastercard real scanat.
     * PAN: 5399820701945601
     * Expiry: 320331
     * Currency: 0946 (RON)
     */
    private byte[] handleReadRecord(int sfi, int recordNum) {
        android.util.Log.d(TAG, "READ RECORD SFI=" + sfi + " REC=" + recordNum);

        if (sfi == 2 && recordNum == 1) {
            TlvBuilder r = new TlvBuilder();
            r.add("9F42", "0946");
            r.add("5F28", "0642");
            r.add("5A", "5399820701945601");
            r.add("5F24", "320331");
            r.add("5F34", "00");
            r.add("9F07", "FFC0");
            r.add("9F08", "0002");
            r.add("8C", "9F02069F03069F1A0295055F2A029A039C019F37049F35019F45029F4C089F34039F21039F7C14");
            r.add("8D", "910A8A0295059F37049F4C08");
            r.add("8E", "000000000000000042031F03");
            r.add("9F0D", "B450840000");
            r.add("9F0E", "0000000000");
            r.add("9F0F", "B470848000");
            r.add("9F4A", "82");
            r.add("57", "5399820701945601D32032011492700673379F");
            return concat(TlvBuilder.wrap("70", r.build()), TlvBuilder.sw_OK());

        } else if (sfi == 4 && recordNum == 1) {
            // SFI 4 Rec 1 - doar 9F47 (exact ca pe card real)
            TlvBuilder r = new TlvBuilder();
            r.add("9F47", "03");
            return concat(TlvBuilder.wrap("70", r.build()), TlvBuilder.sw_OK());

        } else if (sfi == 4 && recordNum == 2) {
            // SFI 4 Rec 2 - 8F, 9F32, 92 (identic cu card real)
            TlvBuilder r = new TlvBuilder();
            r.add("8F", "06");
            r.add("9F32", "03");
            r.add("92", "244DA6FB4D655C3372E1452DBB2B69B4A964FE538E31BEFCA6031B122AF3BA3E507369");
            return concat(TlvBuilder.wrap("70", r.build()), TlvBuilder.sw_OK());

        } else if (sfi == 4 && recordNum == 3) {
            // SFI 4 Rec 3 - ICC PK Certificate (247 bytes, identic cu card real)
            String iccCert = "224029E99460EEE7144A539690CAB280D9DA34861F9390F1381DE79964CE3938C14F5E8C7FD07BCD91D798B323F79BB364ACD4A0942FF4DB2A5B51C2793B6B884D0EAD19B01149F5C98E09C035A073D1A8EBF34AD0DA379FBB3512B6BCED598D48BA829E6E83756576A126C94C1DB1F65729543797B777081BCFA749C3A9AD8716E6A529A8274D8334A48692724B37B801A747FE6D56AAFC15E2202D85D692FD784B76A70E2BC19DA152C2229F576DCDC09CEC2C9652566DF35829742C3A5721C38A08154323C791F0F7BA0C67CC10650D938D78D9046B7EEA0B676847B51AFFFE65DF65E66E610484503D4F87643EDFC24196F0F7CE77";
            TlvBuilder r = new TlvBuilder();
            r.add("9F46", iccCert);
            return concat(TlvBuilder.wrap("70", r.build()), TlvBuilder.sw_OK());

        } else if (sfi == 4 && recordNum == 4) {
            // SFI 4 Rec 4 - Issuer PK Certificate (248 bytes, identic cu card real)
            String issCert = "39A36971D15EC77779428A6D0ADE258ACFCE0EC3E0C4ED5BF6675B75A27FFCC74B61614FEAD95F79D751BC3BE6D274653191709F44BC650CF6A07CE1B799C62DF04D719CAA8EDCE9C31483FE90258E6F838BEAD3968FB90C1D3A183AC3F94912A422D1DB5E687FF4026146BE919F34CCB7D64109955264B20E6405C1990672ABDFD9D8D6ED1ACFAFAFFA7FE0E5FCF8CA4FC4A30C936157245DF221566A45D5640E60D4F9FE72CF78ED371973F90574284B14E4B23FBC927D1D9EB25CE11BCDCB2881CCC953E24748567C9F364E0728767A83CD0D82FD577DA2650F16BD40E540B5734816802ED939EC382AEB4AF2E0C4F947DCC72C6F1DC5";
            TlvBuilder r = new TlvBuilder();
            r.add("90", issCert);
            return concat(TlvBuilder.wrap("70", r.build()), TlvBuilder.sw_OK());

        } else if (sfi == 1) {
            TlvBuilder r = new TlvBuilder();
            r.add("5A", "5399820701945601");
            r.add("5F24", "320331");
            return concat(TlvBuilder.wrap("70", r.build()), TlvBuilder.sw_OK());

        } else {
            TlvBuilder r = new TlvBuilder();
            r.add("5A", "5399820701945601");
            return concat(TlvBuilder.wrap("70", r.build()), TlvBuilder.sw_OK());
        }
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