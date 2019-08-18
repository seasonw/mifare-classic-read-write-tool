package com.hiratte.mifarecaccessrw;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.nfc.NfcAdapter;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.design.widget.BottomNavigationView;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatActivity;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;

public class MainActivity extends AppCompatActivity implements ReadFragment.OnFragmentInteractionListener, WriteFragment.OnFragmentInteractionListener, MiscFragment.OnFragmentInteractionListener {

    private MainActivity currAct;
    private ProgressBar spinner;
    final ReadFragment fragmentRead = new ReadFragment();
    final WriteFragment fragmentWrite = new WriteFragment();
    final MiscFragment fragmentMisc = new MiscFragment();
    Fragment fragmentActive = null;
    private Intent mOldIntent = null;

    private enum StartUpNode {
        HasNfc, HasMifareClassicSupport,
        HasNfcEnabled, HandleNewIntent
    }

    private BottomNavigationView.OnNavigationItemSelectedListener mOnNavigationItemSelectedListener
            = new BottomNavigationView.OnNavigationItemSelectedListener() {

        @Override
        public boolean onNavigationItemSelected(@NonNull MenuItem item) {
            Fragment lfm;
            switch (item.getItemId()) {
                case R.id.navigation_read:
                    fragmentActive = fragmentRead;
                    openFragment(fragmentRead);
                    return true;
                case R.id.navigation_write:
                    fragmentActive = fragmentWrite;
                    openFragment(fragmentWrite);
                    return true;
                case R.id.navigation_misc:
                    fragmentActive = fragmentMisc;
                    openFragment(fragmentMisc);
                    return true;
            }
            return false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        spinner = findViewById(R.id.progressBarMain);
        spinner.getIndeterminateDrawable().setColorFilter(0xFF0000FF, android.graphics.PorterDuff.Mode.MULTIPLY);
        spinner.setVisibility(View.GONE);

        BottomNavigationView navigation = findViewById(R.id.navigation);
        navigation.setOnNavigationItemSelectedListener(mOnNavigationItemSelectedListener);

        fragmentActive = fragmentRead;
        openFragment(fragmentRead);

        // Disable WRITE function
//        Menu menuNav = navigation.getMenu();
//        MenuItem itemWrite = menuNav.findItem(R.id.navigation_write);
//        itemWrite.setEnabled(false);

        currAct = this;
    }

    @Override
    public void onNewIntent(Intent intent) {
        Common.checkIfNewTag(intent, this);
        if (NfcAdapter.ACTION_TECH_DISCOVERED.equals(intent.getAction()) && fragmentActive == fragmentRead) {
            fragmentRead.updateTagInfo(Common.getTag(), this);
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        runStartUpNode(StartUpNode.HasNfc);
    }

    public void showSpinner() {
        if (spinner != null)
            spinner.setVisibility(View.VISIBLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
    }

    public void hideSpinner() {
        if (spinner != null)
            spinner.setVisibility(View.GONE);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
    }

    private void runStartUpNode(StartUpNode startUpNode) {
        switch (startUpNode) {
            case HasNfc:
                Common.setNfcAdapter(NfcAdapter.getDefaultAdapter(this));
                if (Common.getNfcAdapter() == null) {
                    Toast.makeText(this, this.getResources().getString(
                            R.string.text_no_nfc), Toast.LENGTH_LONG).show();
                } else {
                    runStartUpNode(StartUpNode.HasMifareClassicSupport);
                }
                break;
            case HasMifareClassicSupport:
                if (!Common.hasMifareClassicSupport()) {
                    Toast.makeText(this, this.getResources().getString(
                            R.string.text_not_support_mifareclassic), Toast.LENGTH_LONG).show();
                } else {
                    runStartUpNode(StartUpNode.HasNfcEnabled);
                }
                break;
            case HasNfcEnabled:
                Common.setNfcAdapter(NfcAdapter.getDefaultAdapter(this));
                if (!Common.getNfcAdapter().isEnabled()) {
                    Toast.makeText(this, this.getResources().getString(
                            R.string.text_enable_nfc), Toast.LENGTH_LONG).show();
                } else {
                    Common.enableNfcForegroundDispatch(this);
                    runStartUpNode(StartUpNode.HandleNewIntent);
                }
                break;
            case HandleNewIntent:
                Intent intent = getIntent();
                if (intent != null) {
                    boolean isIntentWithTag = intent.getAction().equals(
                            NfcAdapter.ACTION_TECH_DISCOVERED);
                    if (isIntentWithTag && intent != mOldIntent) {
                        mOldIntent = intent;
                        onNewIntent(getIntent());
                    } else {
                        // Last node. Do nothing.
                        break;
                    }
                }
                break;
        }
    }

    private void openFragment(Fragment fragment) {
        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction ft = fm.beginTransaction();
        ft.replace(R.id.container, fragment);
        ft.addToBackStack(null);
        ft.commit();
    }

    @Override
    public void onFragmentInteraction(Uri uri) {

    }
}
