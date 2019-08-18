package com.hiratte.mifarecaccessrw;

import android.nfc.Tag;
import android.nfc.tech.MifareClassic;
import android.nfc.tech.NfcA;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.util.Log;
import android.util.SparseArray;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

public class MCReader {
    private static final String LOG_TAG = MCReader.class.getSimpleName();
    public static final String NO_KEY = "------------";
    public static final String NO_DATA = "--------------------------------";

    private final MifareClassic mMFC;
    private SparseArray<byte[][]> mKeyMap = new SparseArray<>();
    private int mKeyMapStatus = 0;
    private int mLastSector = -1;
    private int mFirstSector = 0;
    private ArrayList<byte[]> mKeysWithOrder;

    private MCReader(Tag tag) {
        MifareClassic tmpMFC;
        try {
            tmpMFC = MifareClassic.get(tag);
        } catch (Exception e) {
            Log.e(LOG_TAG, "Could not create MIFARE Classic reader for the"
                    + "provided tag (even after patching it).");
            throw e;
        }
        mMFC = tmpMFC;
    }

    public static Tag patchTag(Tag tag) {
        if (tag == null) {
            return null;
        }

        String[] techList = tag.getTechList();

        Parcel oldParcel = Parcel.obtain();
        tag.writeToParcel(oldParcel, 0);
        oldParcel.setDataPosition(0);

        int len = oldParcel.readInt();
        byte[] id = new byte[0];
        if (len >= 0) {
            id = new byte[len];
            oldParcel.readByteArray(id);
        }
        int[] oldTechList = new int[oldParcel.readInt()];
        oldParcel.readIntArray(oldTechList);
        Bundle[] oldTechExtras = oldParcel.createTypedArray(Bundle.CREATOR);
        int serviceHandle = oldParcel.readInt();
        int isMock = oldParcel.readInt();
        IBinder tagService;
        if (isMock == 0) {
            tagService = oldParcel.readStrongBinder();
        } else {
            tagService = null;
        }
        oldParcel.recycle();

        int nfcaIdx = -1;
        int mcIdx = -1;
        short sak = 0;
        boolean isFirstSak = true;

        for (int i = 0; i < techList.length; i++) {
            if (techList[i].equals(NfcA.class.getName())) {
                if (nfcaIdx == -1) {
                    nfcaIdx = i;
                }
                if (oldTechExtras[i] != null
                        && oldTechExtras[i].containsKey("sak")) {
                    sak = (short) (sak
                            | oldTechExtras[i].getShort("sak"));
                    isFirstSak = nfcaIdx == i;
                }
            } else if (techList[i].equals(MifareClassic.class.getName())) {
                mcIdx = i;
            }
        }

        boolean modified = false;

        // Patch the double NfcA issue (with different SAK) for
        // Sony Z3 devices.
        if (!isFirstSak) {
            oldTechExtras[nfcaIdx].putShort("sak", sak);
            modified = true;
        }

        // Patch the wrong index issue for HTC One devices.
        if (nfcaIdx != -1 && mcIdx != -1 && oldTechExtras[mcIdx] == null) {
            oldTechExtras[mcIdx] = oldTechExtras[nfcaIdx];
            modified = true;
        }

        if (!modified) {
            // Old tag was not modivied. Return the old one.
            return tag;
        }

        // Old tag was modified. Create a new tag with the new data.
        Parcel newParcel = Parcel.obtain();
        newParcel.writeInt(id.length);
        newParcel.writeByteArray(id);
        newParcel.writeInt(oldTechList.length);
        newParcel.writeIntArray(oldTechList);
        newParcel.writeTypedArray(oldTechExtras, 0);
        newParcel.writeInt(serviceHandle);
        newParcel.writeInt(isMock);
        if (isMock == 0) {
            newParcel.writeStrongBinder(tagService);
        }
        newParcel.setDataPosition(0);
        Tag newTag = Tag.CREATOR.createFromParcel(newParcel);
        newParcel.recycle();

        return newTag;
    }

    public static MCReader get(Tag tag) {
        MCReader mcr = null;
        if (tag != null) {
            try {
                mcr = new MCReader(tag);
                if (!mcr.isMifareClassic()) {
                    return null;
                }
            } catch (RuntimeException ex) {
                // Should not happen. However, it did happen for OnePlus5T
                // user according to Google Play crash reports.
                return null;
            }
        }
        return mcr;
    }

    public SparseArray<byte[][]> getKeyMap() {
        return mKeyMap;
    }

    public boolean isMifareClassic() {
        return mMFC != null;
    }

    public boolean isConnected() {
        return mMFC.isConnected();
    }

    public void connect() throws Exception {
        final AtomicBoolean error = new AtomicBoolean(false);

        // Do not connect if already connected.
        if (isConnected()) {
            return;
        }

        // Connect in a worker thread. (connect() might be blocking).
        Thread t = new Thread() {
            @Override
            public void run() {
                try {
                    mMFC.connect();
                } catch (IOException | IllegalStateException ex) {
                    error.set(true);
                } finally {

                }
            }
        };
        t.start();

        // Wait for the connection (max 500millis).
        try {
            t.join(500);
        } catch (InterruptedException ex) {
            error.set(true);
        }

        // If there was an error log it and throw an exception.
        if (error.get()) {
            Log.d(LOG_TAG, "Error while connecting to tag.");
            throw new Exception("Error while connecting to tag.");
        }
    }

    public void close() {
        try {
            mMFC.close();
        }
        catch (IOException e) {
            Log.d(LOG_TAG, "Error on closing tag.");
        }
    }
}
