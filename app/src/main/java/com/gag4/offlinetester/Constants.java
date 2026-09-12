package com.gag4.offlinetester;

public class Constants {

    // Timeout for test (milliseconds)
    public static final int TEST_TIMEOUT_MS = 45000; // 45 seconds

    // EMV AID-uri (ISO 7816-5)
    public static final class AIDs {
        // Mastercard variants
        public static final String MASTERCARD = "A0000000041010";
        public static final String MASTERCARD_PAYPASS = "A0000000041010";
        public static final String MAESTRO = "A0000000043060";
        public static final String MAESTRO_ALT = "A0000000043061";

        // Visa variants
        public static final String VISA = "A0000000031010";
        public static final String VISA_DEBIT = "A0000000031010";
        public static final String VISA_CREDIT = "A0000000031010";
        public static final String VISA_ELECTRON = "A0000000032010";
        public static final String VISA_PAYWAVE = "A0000000031010";

        // American Express
        public static final String AMEX = "A000000025010";

        // JCB
        public static final String JCB = "A0000000651010";

        // Discover
        public static final String DISCOVER = "A0000000651010";

        // Diners Club
        public static final String DINERS = "A0000000036000";

        // UnionPay (popular in Austria, Romania via terminals)
        public static final String UNIONPAY = "A0000000330101";

        // PSE (Payment System Environment)
        public static final String PSE = "325041592E5359532E444446303031";

        // Additional regional cards
        public static final String TROY = "A0000000998702";
        public static final String MIR = "A0000000555555";
    }

    // EMV Tags
    public static final class Tags {
        public static final int AID = 0x4F;
        public static final int AIP = 0x9F07;  // Application Interchange Profile
        public static final int AUC = 0x9F07;  // Application Usage Control
        public static final int IAD = 0x9F10;  // Issuer Application Data
        public static final int PDOL = 0x9F38; // Processing Data Object List
        public static final int TERMINAL_CAPS = 0x9F33;
        public static final int CRYPTOGRAM = 0x9F27;
        public static final int CVM_RESULT = 0x9F34;
        public static final int AMOUNT = 0x9F02;
        public static final int COUNTRY = 0x9F1A;
        public static final int CURRENCY = 0x9F42;
    }

    // Response codes (SW1 SW2)
    public static final class StatusCodes {
        public static final byte SW1_OK = (byte) 0x90;
        public static final byte SW2_OK = (byte) 0x00;

        public static final byte SW1_MORE_DATA = (byte) 0x61;

        public static final byte SW1_WARNING = (byte) 0x62;
        public static final byte SW2_EOF = (byte) 0x82;

        public static final byte SW1_WARNING_ALT = (byte) 0x63;

        public static final byte SW1_ERROR = (byte) 0x64;

        public static final byte SW1_SECURITY_ERROR = (byte) 0x66;

        public static final byte SW1_WRONG_LENGTH = (byte) 0x67;

        public static final byte SW1_SECURITY_RELATED = (byte) 0x69;

        public static final byte SW1_WRONG_PARAMS = (byte) 0x6A;
        public static final byte SW2_FIELD_NOT_FOUND = (byte) 0x82;
        public static final byte SW2_FILE_NOT_FOUND = (byte) 0x82;
        public static final byte SW2_WRONG_PARAMS = (byte) 0x80;

        public static final byte SW1_CONDITIONS_NOT_SAT = (byte) 0x69;
        public static final byte SW2_CONDITIONS_NOT_SAT = (byte) 0x85;

        public static final byte SW1_FUNCTION_NOT_SUPPORTED = (byte) 0x6A;
        public static final byte SW2_FUNCTION_NOT_SUPPORTED = (byte) 0x81;

        public static final byte SW1_SECURITY_ERROR_ALT = (byte) 0x6E;
    }

    // APDU Instructions
    public static final class Instructions {
        public static final byte SELECT = (byte) 0xA4;
        public static final byte READ_RECORD = (byte) 0xB2;
        public static final byte GET_RESPONSE = (byte) 0xC0;
        public static final byte GET_PROCESSING_OPTIONS = (byte) 0xA8;
        public static final byte GENERATE_AC = (byte) 0xAE;
        public static final byte VERIFY = (byte) 0x20;
        public static final byte PUT_DATA = (byte) 0xDA;
        public static final byte GET_DATA = (byte) 0xCA;
    }
}
