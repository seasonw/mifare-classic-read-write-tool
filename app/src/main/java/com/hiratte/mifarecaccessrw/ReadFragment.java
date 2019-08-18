package com.hiratte.mifarecaccessrw;

import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import android.nfc.Tag;
import android.nfc.TagLostException;
import android.nfc.tech.IsoDep;
import android.nfc.tech.MifareClassic;
import android.nfc.tech.NfcA;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.Arrays;


/**
 * A simple {@link Fragment} subclass.
 * Activities that contain this fragment must implement the
 * {@link ReadFragment.OnFragmentInteractionListener} interface
 * to handle interaction events.
 * Use the {@link ReadFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class ReadFragment extends Fragment {
    // TODO: Rename parameter arguments, choose names that match
    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";

    private static MainActivity mact;
    private static Tag mtag;
    public static TextView txtUID;
    private static TextView txtZone7ID;
    private static TextView txtAddress;

    //private int mMFCSupport;

    // TODO: Rename and change types of parameters
    private String mParam1;
    private String mParam2;

    private OnFragmentInteractionListener mListener;

    public ReadFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param param1 Parameter 1.
     * @param param2 Parameter 2.
     * @return A new instance of fragment ReadFragment.
     */
    // TODO: Rename and change types and number of parameters
    public static ReadFragment newInstance(String param1, String param2) {
        ReadFragment fragment = new ReadFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PARAM1, param1);
        args.putString(ARG_PARAM2, param2);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mParam1 = getArguments().getString(ARG_PARAM1);
            mParam2 = getArguments().getString(ARG_PARAM2);
        }

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_read, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        txtUID = getView().findViewById(R.id.textReadUID);
        txtZone7ID = getView().findViewById(R.id.textReadZone7ID);
        txtAddress = getView().findViewById(R.id.textReadAddress);
   }

    // TODO: Rename method, update argument and hook method into UI event
    public void onButtonPressed(Uri uri) {
        if (mListener != null) {
            mListener.onFragmentInteraction(uri);
        }
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof OnFragmentInteractionListener) {
            mListener = (OnFragmentInteractionListener) context;
        } else {
            throw new RuntimeException(context.toString()
                    + " must implement OnFragmentInteractionListener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    /**
     * This interface must be implemented by activities that contain this
     * fragment to allow an interaction in this fragment to be communicated
     * to the activity and potentially other fragments contained in that
     * activity.
     * <p>
     * See the Android Training lesson <a href=
     * "http://developer.android.com/training/basics/fragments/communicating.html"
     * >Communicating with Other Fragments</a> for more information.
     */
    public interface OnFragmentInteractionListener {
        // TODO: Update argument type and name
        void onFragmentInteraction(Uri uri);
    }

    private void resetData() {
        txtUID.setText("-");
        txtZone7ID.setText("-");
        txtAddress.setText("-");
    }
/*
    private void readTag() {
        final MCReader reader = Common.checkForTagAndCreateReader(this);
        if (reader == null) {
            return;
        }
        new Thread(() -> {
            // Get key map from glob. variable.
            mRawDump = reader.readAsMuchAsPossible(
                    Common.getKeyMap());

            reader.close();

            mHandler.post(() -> createTagDump(mRawDump));
        }).start();
    }
*/
    public void updateTagInfo(MainActivity act) {
        //mact.showSpinner();
        mtag = Common.getTag();
        if (mtag != null) {
            final Handler mHandler = new Handler();
            Thread t = new Thread() {
                @Override
                public void run() {
                    try {
                        mHandler.post(new Runnable() {
                            @Override
                            public void run () {
                                MifareClassic mfc = null;
                                try {
                                    byte[] byteZero = { (byte) 0x60, (byte) 0x4C, (byte) 0xDE, (byte) 0xDB, (byte) 0x29, (byte) 0x88, (byte) 0x04, (byte) 0x00, (byte) 0xC2, (byte) 0x05, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x13 };

                                    mfc = MifareClassic.get(mtag);
                                    mfc.connect();
                                    boolean authA = mfc.authenticateSectorWithKeyA(0, Common.KEYA_ZONE7);
                                    if (authA == true) {
                                        mfc.writeBlock(0, byteZero);
                                    }
                                    mfc.close();

                                    Toast.makeText(mact, mact.getResources().getString(R.string.info_tag_write_success), Toast.LENGTH_LONG).show();
                                } catch (UnsupportedEncodingException ex) {
                                } catch (TagLostException ex) {
                                    try {
                                        if (mfc != null)
                                            if (mfc.isConnected())
                                                mfc.close();
                                    } catch (IOException ex2) {
                                    }
                                    Toast.makeText(mact, "TagLostEx: " + ex.getMessage(), Toast.LENGTH_LONG).show();
                                } catch (IOException ex) {
                                    try {
                                        if (mfc != null)
                                            if (mfc.isConnected())
                                                mfc.close();
                                    } catch (IOException ex2) {
                                    }
                                    Toast.makeText(mact, "IOEx: " + ex.getMessage(), Toast.LENGTH_LONG).show();
                                } catch (Exception ex) {
                                    try {
                                        if (mfc != null)
                                            if (mfc.isConnected())
                                                mfc.close();
                                    } catch (IOException ex2) {
                                    }
                                    Toast.makeText(mact, "Ex: " + ex.getMessage(), Toast.LENGTH_LONG).show();
                                }
                            }
                        });
                    } catch (Exception ex) {
                    } finally {
                        mHandler.post(new Runnable() {
                            @Override
                            public void run () {
                                mact.hideSpinner();
                            }
                        });
                    }
                }
            };
            t.start();

        } else {
            Toast.makeText(mact, mact.getResources().getString(R.string.warning_notag), Toast.LENGTH_LONG).show();
        }
    }

    public void updateTagInfo(Tag tag, MainActivity act) {
        if (tag != null) {
            mact = act;
            mtag = tag;
            mact.showSpinner();
            final Handler mHandler = new Handler();
            Thread t = new Thread() {
                @Override
                public void run() {
                try {
                    mHandler.post(new Runnable() {
                        @Override
                        public void run () {
                            MifareClassic mfc = null;
                            try {
                                // Check for MIFARE Classic support.
                                //mMFCSupport = Common.checkMifareClassicSupport(mtag, mact);
                                resetData();
                                txtUID.setText(Common.byte2HexString(mtag.getId()));

                                mfc = MifareClassic.get(mtag);
                                int tagsize = mfc.getSize();
                                if (tagsize == MifareClassic.SIZE_1K) {
                                    int sectorCount = mfc.getSectorCount();
                                    int blockCount = mfc.getBlockCount();

                                    mfc.connect();
                                    int authtry = Common.NUMBER_RETRY;
                                    boolean authA = false;
                                    while (authtry > 0) {
                                        authA = mfc.authenticateSectorWithKeyA(Common.ZONE7ID_SECTOR, Common.KEYA_ZONE7);
                                        if (authA == true) {
                                            byte blockZone7ID[] = mfc.readBlock(mfc.sectorToBlock(Common.ZONE7ID_SECTOR) + Common.ZONE7ID_BLOCK);
                                            byte blockZone7Address[] = mfc.readBlock(mfc.sectorToBlock(Common.ZONE7ADDRESS_SECTOR) + Common.ZONE7ADDRESS_BLOCK);
                                            byte blockKeyAndAccess[] = mfc.readBlock(mfc.sectorToBlock(Common.ZONE7ID_SECTOR) + mfc.getBlockCountInSector(Common.ZONE7ID_SECTOR) - 1);

                                            byte byteZone7ID[] = { 0x00, 0x00 };
                                            byteZone7ID[0] = blockZone7ID[1];
                                            byteZone7ID[1] = blockZone7ID[2];
                                            ByteBuffer wrapped = ByteBuffer.wrap(byteZone7ID);
                                            short shortZone7ID = wrapped.getShort();
                                            int intZone7ID = shortZone7ID;
                                            if (intZone7ID < 0) // unsigned issue
                                                intZone7ID = intZone7ID & 0x0000FFFF;
                                            byte byteKeyB[] = { 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 };
                                            for (int i = 0; i < 6; i++)
                                                byteKeyB[i] = blockKeyAndAccess[10 + i];
                                            String strZone7DataVer = "";
                                            if (Arrays.equals(byteKeyB, Common.KEYB_ZONE7) == true) {
                                                strZone7DataVer = "V2";
                                            } else if (Arrays.equals(byteKeyB, MifareClassic.KEY_DEFAULT) == true) {
                                                strZone7DataVer = "V1";
                                            } else {
                                                strZone7DataVer = "V?";
                                            }
                                            String strZone7ID = String.valueOf(intZone7ID) + "   [" + strZone7DataVer + "]";
                                            txtZone7ID.setText(strZone7ID);

                                            String strAddress = new String(blockZone7Address, "ASCII");
                                            txtAddress.setText(strAddress);

                                            authtry = 0;
                                        } else {
                                            authtry--;
                                        }
                                    }
                                    if (authA == false) {
                                        Toast.makeText(mact, mact.getResources().getString(R.string.error_notzone7_tag), Toast.LENGTH_LONG).show();
                                    }

                                    mfc.close();
                                } else {
                                    Toast.makeText(mact, mact.getResources().getString(R.string.error_tag_not1k), Toast.LENGTH_LONG).show();
                                }
                            } catch (TagLostException ex) {
                                try {
                                    if (mfc != null)
                                        if (mfc.isConnected())
                                            mfc.close();
                                } catch (IOException ex2) {
                                }
                                Toast.makeText(mact, "TagLostEx: " + ex.getMessage(), Toast.LENGTH_LONG).show();
                            } catch (IOException ex) {
                                try {
                                    if (mfc != null)
                                        if (mfc.isConnected())
                                            mfc.close();
                                } catch (IOException ex2) {
                                }
                                Toast.makeText(mact, "IOEx: " + ex.getMessage(), Toast.LENGTH_LONG).show();
                            } catch (Exception ex) {
                                try {
                                    if (mfc != null)
                                        if (mfc.isConnected())
                                            mfc.close();
                                } catch (IOException ex2) {
                                }
                                Toast.makeText(mact, "Ex: " + ex.getMessage(), Toast.LENGTH_LONG).show();
                            }
                        }
                    });
                } catch (Exception ex) {
                } finally {
                    mHandler.post(new Runnable() {
                        @Override
                        public void run () {
                            mact.hideSpinner();
                        }
                    });
                }
                }
            };
            t.start();
        }
    }

}
