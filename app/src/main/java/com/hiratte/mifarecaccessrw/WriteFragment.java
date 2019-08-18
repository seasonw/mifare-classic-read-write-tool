package com.hiratte.mifarecaccessrw;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.net.Uri;
import android.nfc.Tag;
import android.nfc.TagLostException;
import android.nfc.tech.MifareClassic;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.Arrays;


/**
 * A simple {@link Fragment} subclass.
 * Activities that contain this fragment must implement the
 * {@link WriteFragment.OnFragmentInteractionListener} interface
 * to handle interaction events.
 * Use the {@link WriteFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class WriteFragment extends Fragment {
    // TODO: Rename parameter arguments, choose names that match
    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";

    private static MainActivity mact;
    private static Tag mtag;
    private static EditText editZone7ID;
    private static EditText editAddress;
    private static Button btnWrite;

    // TODO: Rename and change types of parameters
    private String mParam1;
    private String mParam2;

    private OnFragmentInteractionListener mListener;

    public WriteFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param param1 Parameter 1.
     * @param param2 Parameter 2.
     * @return A new instance of fragment WriteFragment.
     */
    // TODO: Rename and change types and number of parameters
    public static WriteFragment newInstance(String param1, String param2) {
        WriteFragment fragment = new WriteFragment();
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
        return inflater.inflate(R.layout.fragment_write, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        mact = (MainActivity) getActivity();
        editZone7ID = getView().findViewById(R.id.editWriteZone7ID);
        editAddress = getView().findViewById(R.id.editWriteAddress);
        btnWrite = getView().findViewById(R.id.buttonWriteWrite);
        btnWrite.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                String strZone7ID = editZone7ID.getText().toString();
                final int intZone7ID = Integer.parseInt(strZone7ID);
                final String strAddress = editAddress.getText().toString();
                if (intZone7ID > 65535) {
                    Toast.makeText(mact, mact.getResources().getString(R.string.error_invalid_zone7id), Toast.LENGTH_LONG).show();
                    return;
                }
                if (strAddress == null || strAddress.length() > 15) {
                    Toast.makeText(mact, mact.getResources().getString(R.string.error_invalid_address), Toast.LENGTH_LONG).show();
                    return;
                }

                mact.showSpinner();
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
                                            byte[] byteZone7ID = new byte[16];
                                            byte[] byteZone7IDOri = Zone7IdIntToBytes(intZone7ID);
                                            System.arraycopy(byteZone7IDOri, 0, byteZone7ID, 1, byteZone7IDOri.length);
                                            byte[] byteAddress = new byte[16];
                                            byte[] byteAddressOri = strAddress.getBytes("ASCII");
                                            System.arraycopy(byteAddressOri, 0, byteAddress, 0, byteAddressOri.length);
                                            byte[] byteTrailer = new byte[16];
                                            System.arraycopy(Common.KEYA_ZONE7, 0, byteTrailer, 0, Common.KEYA_ZONE7.length);
                                            System.arraycopy(Common.ACCESSBIT_ZONE7, 0, byteTrailer, 6, Common.ACCESSBIT_ZONE7.length);
                                            System.arraycopy(Common.KEYB_ZONE7, 0, byteTrailer, 10, Common.KEYB_ZONE7.length);

                                            mfc = MifareClassic.get(mtag);
                                            int tagsize = mfc.getSize();
                                            if (tagsize == MifareClassic.SIZE_1K) {
                                                int sectorCount = mfc.getSectorCount();
                                                int blockCount = mfc.getBlockCount();
                                                boolean authfail = false;

                                                mfc.connect();
                                                int authtry = Common.NUMBER_RETRY;
                                                // test all blocks writable
                                                authfail = false;
                                                for (int i = 0; i < sectorCount; i++) {
                                                    boolean authA = false;
                                                    authtry = Common.NUMBER_RETRY;
                                                    while (authtry > 0) {
                                                        // auth V2
                                                        authA = mfc.authenticateSectorWithKeyA(i, Common.KEYA_ZONE7);
                                                        if (authA == true) {
                                                            authtry = -1;
                                                        } else {
                                                            // auth blank card
                                                            authA = mfc.authenticateSectorWithKeyA(i, MifareClassic.KEY_DEFAULT);
                                                            if (authA == true) {
                                                                authtry = -1;
                                                            } else {
                                                                authtry--;
                                                            }
                                                        }
                                                    }
                                                    if (authA == false && authtry == 0) {
                                                        authfail = true;
                                                        Toast.makeText(mact, "T" + mact.getResources().getString(R.string.error_notzone7blank_tag) + " : " + String.valueOf(i), Toast.LENGTH_LONG).show();
                                                        break;
                                                    }
                                                }

                                                if (authfail == false) {
                                                    for (int i = 0; i < sectorCount; i++) {
                                                        boolean authA = false;
                                                        authtry = Common.NUMBER_RETRY;
                                                        while (authtry > 0) {
                                                            // auth V2
                                                            authA = mfc.authenticateSectorWithKeyA(i, Common.KEYA_ZONE7);
                                                            if (authA == true) {
                                                                authtry = -1;
                                                            } else {
                                                                // auth blank card
                                                                authA = mfc.authenticateSectorWithKeyA(i, MifareClassic.KEY_DEFAULT);
                                                                if (authA == true) {
                                                                    authtry = -1;
                                                                } else {
                                                                    authtry--;
                                                                }
                                                            }
                                                        }
                                                        if (authA == true) {
                                                            // write block data
                                                            if (i == Common.ZONE7ID_SECTOR) {
                                                                mfc.writeBlock(mfc.sectorToBlock(Common.ZONE7ID_SECTOR) + Common.ZONE7ID_BLOCK, byteZone7ID);
                                                            }
                                                            if (i == Common.ZONE7ADDRESS_SECTOR) {
                                                                mfc.writeBlock(mfc.sectorToBlock(Common.ZONE7ADDRESS_SECTOR) + Common.ZONE7ADDRESS_BLOCK, byteAddress);
                                                            }
                                                            // write sector trailer
                                                            mfc.writeBlock(mfc.sectorToBlock(i) + mfc.getBlockCountInSector(i) - 1, byteTrailer);
                                                        }
                                                    }
                                                }
                                                mfc.close();

                                                Toast.makeText(mact, mact.getResources().getString(R.string.info_tag_write_success), Toast.LENGTH_LONG).show();
                                            } else {
                                                Toast.makeText(mact, mact.getResources().getString(R.string.error_tag_not1k), Toast.LENGTH_LONG).show();
                                            }
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
        });
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

    private byte[] Zone7IdIntToBytes(int i) {
        byte[] result = new byte[2];

        byte temp1 = (byte) (i >> 24);
        byte temp2 = (byte) (i >> 16);
        result[0] = (byte) (i >> 8);
        result[1] = (byte) (i /*>> 0*/);

        return result;
    }
}
