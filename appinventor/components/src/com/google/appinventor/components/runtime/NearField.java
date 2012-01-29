// Copyright 2012 MIT All rights reserved

package com.google.appinventor.components.runtime;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentFilter.MalformedMimeTypeException;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.os.Parcelable;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import com.google.appinventor.components.annotations.DesignerComponent;
import com.google.appinventor.components.annotations.PropertyCategory;
import com.google.appinventor.components.annotations.SimpleEvent;
import com.google.appinventor.components.annotations.SimpleObject;
import com.google.appinventor.components.annotations.SimpleProperty;
import com.google.appinventor.components.annotations.UsesPermissions;
import com.google.appinventor.components.common.ComponentCategory;
import com.google.appinventor.components.common.YaVersion;


/**
 * Controller for Near Field Communication
 *
 */
@DesignerComponent(version = YaVersion.NEARFIELD_COMPONENT_VERSION,
    description = "<p>Non-visible component to provide NFC capabilities." +
    "INSERT DOCUMENTATION HERE</p>",
    // TODO: Change category to SENSORS some day.  But we need to
    // think about what to do with phones that do not provide NFC.
    category = ComponentCategory.INTERNAL,
    nonVisible = true,

		// the image nerField.png here is a dummy:  a copy of the image for location sensor.
		// you should find the image you want and replace the file. 
		iconName = "images/nearField.png")

@SimpleObject
@UsesPermissions(permissionNames = "android.permission.NFC")
public class NearField extends AndroidNonvisibleComponent
    implements Component, OnStopListener, OnResumeListener, Deleteable {
  private static final String TAG = "nearfield";
  private boolean mResumed = false;
  private boolean mWriteMode = false;
  NfcAdapter mNfcAdapter;
  TextView tv;
  Context context;
  Activity activity;
  private String tagContent = "";

	PendingIntent mNfcPendingIntent;
	IntentFilter[] mWriteTagFilters;
	IntentFilter[] mNdefExchangeFilters;

	/**
	 * Creates a new NearField component
	 * @param container  ignored (because this is a non-visible component)
	 */
	public NearField(ComponentContainer container) {
		super(container.$form());
		activity = container.$context();
		// defining context is useless here, but should make it easier to 
		// compare the code with other components
		context = activity;
	  mNfcAdapter = NfcAdapter.getDefaultAdapter(context);
		form.registerForOnResume(this);
		form.registerForOnStop(this);

		// Handle all of our received NFC intents in this activity.
		mNfcPendingIntent = PendingIntent.getActivity(context, 0,
				new Intent(activity, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);

		// Intent filters for reading a note from a tag or exchanging over p2p.
		IntentFilter ndefDetected = new IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED);
		try {
			ndefDetected.addDataType("text/plain"); //"text/plain" is a mime type, has to do with input
		} catch (MalformedMimeTypeException e) { }
		mNdefExchangeFilters = new IntentFilter[] { ndefDetected };
		Log.d(TAG, "Nearfield component created");
	}

	// Events

	/**
	 * Indicates that a new tag has been detected.
	 */
	@SimpleEvent
	public void nearFieldDetected(String message) {
		EventDispatcher.dispatchEvent(this, "NearFieldDetected", message);
	}


	// Properties

	/**
	 * Returns the content of the most recently received tag.
	 */
	@SimpleProperty(category = PropertyCategory.BEHAVIOR)//what does this mean?
	public String LastMessage() {
		Log.d(TAG, "String message method stared");
		return tagContent;
	}


	// here are examples of methods.  What should the NFC methods be?
	// When you define these functions the blocks will be created automatically.

	/**
	 * Empty method to implement.
	 */
	//@SimpleFunction(description = "Derives latitude of given address")
	//public double LatitudeFromAddress(String locationName) {

	// enable nfc communication in resume
	
	public void onResume() {
		Log.d(TAG, "OnResume method started.");
		mResumed = true;
		// Sticky notes received from Android. 
		/* This is needed if tag is to be read even if the application is in background-
           i.e. even if there isn't an open application that can be used with this message,
           the phone still receives and stores all of the data, even if it is not 
           clear yet how the data will be used.
		 */
		Intent foo1 = activity.getIntent();	
		Log.d(TAG, "activity intent is " + foo1);
		if (foo1 == null) {
		  Log.d(TAG, "????  activity intent is null  ???");
		}
		String foo2 = foo1.getAction();
		Log.d(TAG, "action is " + foo2);
	  if (foo2 == null) {
      Log.d(TAG, "????  action is null  ???");
    }
		if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(activity.getIntent().getAction())) {
		  Log.d(TAG, "Getting NDef Messages");
			NdefMessage[] messages = getNdefMessages(activity.getIntent());
			byte[] payload = messages[0].getRecords()[0].getPayload(); //extracts content
			Log.d(TAG, "Got payload: " + payload.toString());
			//toast(payload.toString());
			activity.setIntent(new Intent()); // Consume this intent.
		}
		enableNdefExchangeMode();//do not forget this- lets NFC communication resume
		Log.d(TAG, "ending onResume method");
	}

	// disable nfc communication in pause
	
	public void onPause() {
		Log.d(TAG, "OnPause method started.");
		mResumed = false;
		mNfcAdapter.disableForegroundNdefPush(activity); //disables NFC communication 
	}

	// Method invoked when nfc communication initiated
	
	protected void onNewIntent(Intent intent) {
		Log.d(TAG, "New NFC Communication initiated.");
		// NDEF exchange mode
		if (!mWriteMode && NfcAdapter.ACTION_NDEF_DISCOVERED.equals(intent.getAction())) {
			String msgs = new String(getNdefMessages(intent)[0].getRecords()[0].getPayload());
		//	toast(msgs);
			Log.d(TAG, "OnNewIntent got messages: " + msgs);
		}
	}

	//gets messages from the received intent
	NdefMessage[] getNdefMessages(Intent intent) { 
		// Parse the intent- extracts messages from intent received during onResume()
		NdefMessage[] msgs = null;
		String action = intent.getAction();
		if (NfcAdapter.ACTION_TAG_DISCOVERED.equals(action)
				|| NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action)) {
			Log.d(TAG, "NDEF Signal received.");
			//following line actually receives NDef signal and turns it into a list
			Parcelable[] rawMsgs = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
			if (rawMsgs != null) {
				Log.d(TAG, "Message received is not empty.");
				msgs = new NdefMessage[rawMsgs.length];
				for (int i = 0; i < rawMsgs.length; i++) {
					msgs[i] = (NdefMessage) rawMsgs[i];
				}
			} else {
				Log.d(TAG, "Message received empty or Unknown Tag Type.");
				// Unknown tag type
				byte[] empty = new byte[] {};
				//records are turned into messages which can then be processed/ddisplayed
				NdefRecord record = new NdefRecord(NdefRecord.TNF_UNKNOWN, empty, empty, empty);
				NdefMessage msg = new NdefMessage(new NdefRecord[] {
						record
				});
				msgs = new NdefMessage[] {
						msg
				};
			}
		} else {
			Log.d(TAG, "Unknown intent.");
			activity.finish();
		}
		nearFieldDetected(new String(msgs[0].getRecords()[0].getPayload()));
		tagContent = (new String(msgs[0].getRecords()[0].getPayload()));
		return msgs; //returns actual message that was stored in the tag
	}

	//turns NFC communication back on
	private void enableNdefExchangeMode() {
    Log.d(TAG, "entering enableNdefExchangeMode.");
		Log.d(TAG,"activity= " + activity.toString() + " PendingIntent= " + mNfcPendingIntent.toString() 
		    + " ExchangeFilters= " + mNdefExchangeFilters.toString());
		if (mNfcAdapter == null) {
		  Log.d(TAG, "mNFCAdapter is nul!!!");
		}
		mNfcAdapter.enableForegroundDispatch(activity, mNfcPendingIntent, mNdefExchangeFilters, null);
    Log.d(TAG, "exiting enableNdefExchangeMode.");
	}

	//makes pop up screen on phone
	private void toast(String text) {
		Toast.makeText(context, text, Toast.LENGTH_SHORT).show();
	}

	@Override
	public void onDelete() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onStop() {
		// TODO Auto-generated method stub
		
	}

}
