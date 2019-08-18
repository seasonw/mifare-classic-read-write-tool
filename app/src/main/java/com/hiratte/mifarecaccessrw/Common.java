package com.hiratte.mifarecaccessrw;

import android.Manifest;
import android.app.Activity;
import android.app.Application;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.MifareClassic;
import android.nfc.tech.NfcA;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.util.SparseArray;
import android.widget.Toast;

import java.io.File;
import java.util.Arrays;

public class Common extends Application {
    public static final int NUMBER_RETRY = 5;

    public static final byte[] KEYA_ZONE7 = { (byte)0xa1, (byte)0xf2, (byte)0xb3, (byte)0xe4, (byte)0xc5, (byte)0xd6};
    public static final byte[] KEYB_ZONE7 = { (byte)0xf6, (byte)0xa5, (byte)0xe4, (byte)0xb3, (byte)0xd2, (byte)0xc1};
    public static final byte[] ACCESSBIT_ZONE7 = { (byte)0xFF, (byte)0x07, (byte)0x80, (byte)0x69};
    public static final int ZONE7ID_SECTOR = 1; // ZONE7ID uses 2 bytes unsigned integer
    public static final int ZONE7ID_BLOCK = 0;
    public static final int ZONE7ADDRESS_SECTOR = 1;
    public static final int ZONE7ADDRESS_BLOCK = 1;
    private static final String LOG_TAG = Common.class.getSimpleName();

    private static Tag mTag = null;
    private static byte[] mUID = null;
    private static SparseArray<byte[][]> mKeyMap = null;

    private static NfcAdapter mNfcAdapter;
    private static int mHasMifareClassicSupport = 0;

    private static Context mAppContext;
    private static String mVersionCode;

    @Override
    public void onCreate() {
        super.onCreate();
        mAppContext = getApplicationContext();

        try {
            mVersionCode = getPackageManager().getPackageInfo(
                    getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            Log.d(LOG_TAG, "Version not found.");
        }
    }

    public static void enableNfcForegroundDispatch(Activity targetActivity) {
        if (mNfcAdapter != null && mNfcAdapter.isEnabled()) {

            Intent intent = new Intent(targetActivity,
                    targetActivity.getClass()).addFlags(
                    Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent pendingIntent = PendingIntent.getActivity(
                    targetActivity, 0, intent, 0);
            mNfcAdapter.enableForegroundDispatch(
                    targetActivity, pendingIntent, null, new String[][] {
                            new String[] { NfcA.class.getName() } });
        }
    }

    public static void disableNfcForegroundDispatch(Activity targetActivity) {
        if (mNfcAdapter != null && mNfcAdapter.isEnabled()) {
            mNfcAdapter.disableForegroundDispatch(targetActivity);
        }
    }

    public static int checkIfNewTag(Intent intent, Context context) {
        if (NfcAdapter.ACTION_TECH_DISCOVERED.equals(intent.getAction())) {
            Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            tag = MCReader.patchTag(tag);
            setTag(tag);

            // Show Toast message with UID.
            String id = context.getResources().getString(
                    R.string.text_new_tag) + " (UID: ";
            id += byte2HexString(tag.getId());
            id += ")";
            Toast.makeText(context, id, Toast.LENGTH_LONG).show();

            return checkMifareClassicSupport(tag, context);
        }
        return -4;
    }

    public static int checkMifareClassicSupport(Tag tag, Context context) {
        if (tag == null || context == null) {
            // Error.
            return -3;
        }

        if (Arrays.asList(tag.getTechList()).contains(
                MifareClassic.class.getName())) {
            // Device and tag support MIFARE Classic.
            return 0;

        } else {
            NfcA nfca = NfcA.get(tag);
            byte sak = (byte)nfca.getSak();
            if ((sak>>1 & 1) == 1) {
                // RFU.
                return -2;
            } else {
                if ((sak>>3 & 1) == 1) { // SAK bit 4 = 1?
                    if((sak>>4 & 1) == 1) { // SAK bit 5 = 1?
                        // MIFARE Classic 4k
                        // MIFARE SmartMX 4K
                        // MIFARE PlusS 4K SL1
                        // MIFARE PlusX 4K SL1
                        return -1;
                    } else {
                        if ((sak & 1) == 1) { // SAK bit 1 = 1?
                            // MIFARE Mini
                            return -1;
                        } else {
                            // MIFARE Classic 1k
                            // MIFARE SmartMX 1k
                            // MIFARE PlusS 2K SL1
                            // MIFARE PlusX 2K SL2
                            return -1;
                        }
                    }
                } else {
                    // Some MIFARE tag, but not Classic or Classic compatible.
                    return -2;
                }
            }
        }
    }

    public static boolean hasMifareClassicSupport() {
        if (mHasMifareClassicSupport != 0) {
            return mHasMifareClassicSupport == 1;
        }

        if (NfcAdapter.getDefaultAdapter(mAppContext) == null) {
            mHasMifareClassicSupport = -1;
            return false;
        }

        boolean isLenovoP2 = Build.MANUFACTURER.equals("LENOVO")
                && Build.MODEL.equals("Lenovo P2a42");
        File device = new File("/dev/bcm2079x-i2c");
        if (!isLenovoP2 && device.exists()) {
            mHasMifareClassicSupport = -1;
            return false;
        }

        device = new File("/dev/pn544");
        if (device.exists()) {
            mHasMifareClassicSupport = 1;
            return true;
        }

        File libsFolder = new File("/system/lib");
        File[] libs = libsFolder.listFiles();
        for (File lib : libs) {
            if (lib.isFile()
                    && lib.getName().startsWith("libnfc")
                    && lib.getName().contains("brcm")
                // Add here other non NXP NFC libraries.
                    ) {
                mHasMifareClassicSupport = -1;
                return false;
            }
        }

        mHasMifareClassicSupport = 1;
        return true;
    }

    public static MCReader checkForTagAndCreateReader(Context context) {
        MCReader reader;
        boolean tagLost = false;
        // Check for tag.
        if (mTag != null && (reader = MCReader.get(mTag)) != null) {
            try {
                reader.connect();
            } catch (Exception e) {
                tagLost = true;
            }
            if (!tagLost && !reader.isConnected()) {
                reader.close();
                tagLost = true;
            }
            if (!tagLost) {
                return reader;
            }
        }

        // Error. The tag is gone.
        Toast.makeText(context, R.string.info_no_tag_found,
                Toast.LENGTH_LONG).show();
        return null;
    }

    public static Tag getTag() {
        return mTag;
    }

    public static void setTag(Tag tag) {
        mTag = tag;
        mUID = tag.getId();
    }

    public static NfcAdapter getNfcAdapter() {
        return mNfcAdapter;
    }

    public static void setNfcAdapter(NfcAdapter nfcAdapter) {
        mNfcAdapter = nfcAdapter;
    }

    public static SparseArray<byte[][]> getKeyMap() {
        return mKeyMap;
    }

    public static void setKeyMap(SparseArray<byte[][]> value) {
        mKeyMap = value;
    }

    public static String byte2HexString(byte[] bytes) {
        StringBuilder ret = new StringBuilder();
        if (bytes != null) {
            for (Byte b : bytes) {
                ret.append(String.format("%02X", b.intValue() & 0xFF));
            }
        }
        return ret.toString();
    }

    public static boolean hasWritePermissionToExternalStorage(Context context) {
        return ContextCompat.checkSelfPermission(context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED;
    }

}
