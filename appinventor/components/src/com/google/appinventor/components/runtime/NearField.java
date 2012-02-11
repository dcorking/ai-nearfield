// Copyright 2012 MIT All rights reserved

package com.google.appinventor.components.runtime;

import android.app.Activity;
import android.content.Intent;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.os.Parcelable;
import android.util.Log;

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
    implements OnStopListener, OnResumeListener, OnPauseListener, OnNewIntentListener, Deleteable {
  private static final String TAG = "nearfield";
  private Activity activity;

  private String tagContent = "";
  
  /* Used to identify the call to startActivityForResult. Will be passed back into the
  resultReturned() callback method. */
  protected int requestCode;

	/**
	 * Creates a new NearField component
	 * @param container  ignored (because this is a non-visible component)
	 */
	public NearField(ComponentContainer container) {
		super(container.$form());
		activity = container.$context();
		// register with the forms to that OnResume and OnNewIntent
		// messages get sent to this component
		form.registerForOnResume(this);
		form.registerForOnNewIntent(this);
		Log.d(TAG, "Nearfield component created");
	}
	

	// Events

	/**
	 * Indicates that a new tag has been detected.
	 * Currently this is only a plain text tag, as specified in the
	 * manifest.  See Compiler.java.
	 */
	@SimpleEvent
	public void TagRead(String message) {
	  Log.d(TAG, "Tag read: got message " + message);
	  tagContent = message;
		EventDispatcher.dispatchEvent(this, "TagRead", message);
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


	// Here's an example of a method.  What should the NFC methods be, if any?
	// When you define these functions the blocks will be created automatically.

	/**
	 * Empty method to implement.
	 */
	//@SimpleFunction(description = "Derives latitude of given address")
	//public double LatitudeFromAddress(String locationName) {

	
	
	// When NFC is detected, the form's onNewIntent method is triggered (because of the
	// specification in the manifest.  The form then sends that intent here.
	@Override
	public void onNewIntent(Intent intent) {
	  Log.d(TAG, "Nearfield on onNewIntent.  Intent is: " + intent);
    resolveIntent(intent);
  }

	// TODO: Re-enable NFC communication if it had been disabled
	@Override
	public void onResume() {
	  Intent intent = activity.getIntent();
	  Log.d(TAG, "Nearfield on onResume.  Intent is: " + intent);
    resolveIntent(intent);
  }
	
	void resolveIntent(Intent intent) {
	  Log.d(TAG, "resolve intent. Intent is: " + intent);
	  // Parse the intent
	  String action = intent.getAction();
	  activity.setIntent(new Intent()); // Consume this intent.  Is this the right thing?
	  if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action)) {
	    // When a tag is discovered we send it to the service to be save. We
	    // include a PendingIntent for the service to call back onto. This
	    // will cause this activity to be restarted with onNewIntent(). At
	    // that time we read it from the database and view it.
	    // We'll keep this database code in here for now, but it's useless, because we
	    // can use AppInventor higher level operations to manipulate the tag data.
	    Parcelable[] rawMsgs = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
	    NdefMessage[] msgs;
	    if (rawMsgs != null) {
	      msgs = new NdefMessage[rawMsgs.length];
	      for (int i = 0; i < rawMsgs.length; i++) {
	        msgs[i] = (NdefMessage) rawMsgs[i];
	      }
	    } else {
	      // Unknown tag type
	      // For now, just ignore it. Later we might want to signal an error to the
	      // app user.
	      byte[] empty = new byte[] {};
	      NdefRecord record = new NdefRecord(NdefRecord.TNF_UNKNOWN, empty, empty, empty);
	      NdefMessage msg = new NdefMessage(new NdefRecord[] {record});
	      msgs = new NdefMessage[] {msg};
	    }
	    byte[] payload = msgs[0].getRecords()[0].getPayload();
	    String message = new String(payload);
	    Log.d(TAG, "Calling TagRead. Message received is " + message);
	    TagRead(message);
	  } else {
	    Log.e(TAG, "Unknown intent " + intent);
	  }
	}


	// TODO: Disable NFC communication in onPause and onDelete
	// and restore it in onResume

	public void onPause() {
	  Log.d(TAG, "OnPause method started.");
	}

	@Override
	public void onDelete() {
		// TODO Auto-generated method stub
	  // need to delete the nearfieldActivity
		
	}

	@Override
	public void onStop() {
		// TODO Auto-generated method stub		
	}

}
