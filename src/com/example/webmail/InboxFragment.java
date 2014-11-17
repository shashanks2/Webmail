package com.example.webmail;

import java.io.BufferedWriter;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.StreamCorruptedException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Properties;

import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Store;
import javax.net.ssl.SSLProtocolException;
import javax.net.ssl.SSLSocketFactory;

import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.SSLCertificateSocketFactory;
import android.net.SSLSessionCache;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.NetworkOnMainThreadException;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView.MultiChoiceModeListener;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

public class InboxFragment extends Fragment {

	ListView lv;
	static MyCustomAdapter adapter;
	static Message []message = null;
	static MessageParcel[] messageParcels = null;
	static MessageParcel[] messageParcelsTemp = null;
	static Context context = null;
	static String server = null;
	static String imap_address = null;
	static String imap_port = null;
	static String username = null;
	static String password = null;
	static String protocol = null;
	static String folder = null;

	static Receiver mReceiver;
	static boolean isInForeground = false;
	static String needNotification;
	static String needNotificationTone;

	public InboxFragment() {
	}

	@Override
	public void onStart(){
		super.onStart();
		setHasOptionsMenu(true);
		context = getActivity();

		isInForeground = true;

		//Registering BroadcastReceiver
		mReceiver = new Receiver();
		IntentFilter filter;
		filter = new IntentFilter("com.example.webmail.inbox");
		getActivity().registerReceiver(mReceiver, filter);

		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
		server = prefs.getString(getString(R.string.pref_server_settings_key), getString(R.string.pref_server_settings_default));
		if(server.equalsIgnoreCase("gmail")){
			imap_address = "imap.gmail.com";
			imap_port = "0";
			protocol = "imaps";
			folder = "Inbox";
		}
		else{
			imap_address = "172.16.1.11";
			imap_port = "143";
			protocol = "imap";
			folder = "Inbox";
		}
		//imap_address = prefs.getString(getString(R.string.pref_imap_key), getString(R.string.pref_imap_default));
		//imap_port = prefs.getString(getString(R.string.pref_imap_port_key), getString(R.string.pref_imap_port_default));
		username = prefs.getString(getString(R.string.pref_username_key), getString(R.string.pref_username_default));
		password = prefs.getString(getString(R.string.pref_password_key), getString(R.string.pref_password_default));


		Calendar cal = Calendar.getInstance(); 
		AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
		long interval = Long.parseLong(prefs.getString(getString(R.string.pref_timer_settings_key), getString(R.string.pref_timer_settings_default)));
		interval = interval * 60 *1000;

		Intent sync = new Intent(context, Sync.class);
		sync.putExtra("PROTOCOL", protocol);
		sync.putExtra("IMAP_ADDRESS", imap_address);
		sync.putExtra("IMAP_PORT", imap_port);
		sync.putExtra("USERNAME", username);
		sync.putExtra("PASSWORD", password);
		sync.putExtra("FOLDER", folder);
		sync.putExtra("FILE_NAME", "inbox");
		sync.putExtra("NOTIF_UNFLAG", true);

		PendingIntent servicePendingIntent = 
				PendingIntent.getService(context, 
						Sync.SERVICE_ID, // integer constant used to identify the service
						sync,
						PendingIntent.FLAG_CANCEL_CURRENT);   
		am.setRepeating( 
				AlarmManager.RTC_WAKEUP,//type of alarm. This one will wake up the device when it goes off, but there are others, check the docs
				cal.getTimeInMillis(),
				interval,
				servicePendingIntent
				);

		needNotification = prefs.getString(getString(R.string.pref_notification_toggle_key), getString(R.string.pref_notification_toggle_default));
		needNotificationTone = prefs.getString(getString(R.string.pref_notification_sound_key), getString(R.string.pref_notification_sound_default));
	}

	@Override
	public void onResume(){
		super.onResume();
		isInForeground = true;
	}

	@Override
	public void onStop(){
		super.onStop();
		isInForeground = false;
	}

	@Override
	public void onPause(){
		super.onPause();
		isInForeground = false;
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater){
		inflater.inflate(R.menu.folder_menu, menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item){
		int id = item.getItemId();
		if(id == R.id.action_refresh){
			Intent sync = new Intent(context, Sync.class);
			sync.putExtra("PROTOCOL", protocol);
			sync.putExtra("IMAP_ADDRESS", imap_address);
			sync.putExtra("IMAP_PORT", imap_port);
			sync.putExtra("USERNAME", username);
			sync.putExtra("PASSWORD", password);
			sync.putExtra("FOLDER", folder);
			sync.putExtra("FILE_NAME", "inbox");
			context.startService(sync);
			return true;
		}
		if(id == R.id.action_compose){
			Intent intent = new Intent(getActivity(), ComposeActivity.class);
			startActivity(intent);
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	@TargetApi(Build.VERSION_CODES.HONEYCOMB) @Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		View rootView = inflater.inflate(R.layout.fragment_main, container,
				false);

		context = getActivity();

		lv = (ListView)rootView.findViewById(R.id.main_list_view);

		Button fetch_view = new Button(getActivity());
		fetch_view.setText("Fetch More Messages");
		fetch_view.setGravity(Gravity.CENTER);
		fetch_view.setBackgroundColor(Color.WHITE);
		fetch_view.setPadding(0, 20, 0, 20);
		fetch_view.setTypeface(null, Typeface.ITALIC);
		fetch_view.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				Intent fetch = new Intent(context, Fetch.class);
				fetch.putExtra("PROTOCOL", protocol);
				fetch.putExtra("IMAP_ADDRESS", imap_address);
				fetch.putExtra("IMAP_PORT", imap_port);
				fetch.putExtra("USERNAME", username);
				fetch.putExtra("PASSWORD", password);
				fetch.putExtra("FOLDER", folder);
				fetch.putExtra("FILE_NAME", "inbox");
				int l = 0;
				if(messageParcels != null)
					l = messageParcels.length;
				fetch.putExtra("COUNT", l);
				context.startService(fetch);
			}
		});
		lv.addFooterView(fetch_view);

		adapter = new MyCustomAdapter(getActivity(), new ArrayList<String>());
		lv.setAdapter(adapter);

		// Multi Select Functionality
		lv.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);
		lv.setMultiChoiceModeListener(new MultiChoiceModeListener(){

			@Override
			public boolean onActionItemClicked(android.view.ActionMode mode,
					MenuItem item) {
				// TODO Auto-generated method stub
				switch (item.getItemId()) {
				case R.id.delete:
					// dialog here
					final android.view.ActionMode m=mode;
					AlertDialog ConfirmDel =new AlertDialog.Builder(getActivity()) 
					//set message, title, and icon
					.setTitle("Delete") 
					.setMessage("Are you sure?")
					.setPositiveButton("Delete", new DialogInterface.OnClickListener() {

						public void onClick(DialogInterface dialog, int whichButton) { 
							//your deleting code
							Log.e("DELETE CLICKED", "true");
							SparseBooleanArray selected = adapter
									.getSelectedIds();
							// Captures all selected ids with a loop
							ArrayList<Integer> position = new ArrayList<Integer>();
							ArrayList<Parcelable> msg = new ArrayList<Parcelable>();
							for (int i = (selected.size() - 1); i >= 0; i--) {
								if (selected.valueAt(i)) {
									String selecteditem = adapter
											.getItem(selected.keyAt(i));
									// Remove selected items following the ids
									Log.e("DELETE CLICKED", "true");
									int p = adapter.getPosition(selecteditem);
									MessageParcel m = messageParcels[messageParcels.length - 1 - p];

									position.add(p);
									msg.add(m);
								}
							}


							Intent intent = new Intent(getActivity(), DeleteMails.class);
							intent.putExtra("MY_INDEX", position);
							intent.putExtra("FOLDER", "Inbox");
							intent.putExtra("messageParcel", msg);
							getActivity().startService(intent);
							m.finish();
							//Intent goBack = new Intent(getActivity(), MainActivity.class);
							//startActivity(goBack);
							dialog.dismiss();
						}   

					})
					.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							m.finish();
							dialog.dismiss();

						}
					})
					.show();
					// dialog here
					
					return true;
				default:
					return false;
				}



			}

			@Override
			public boolean onCreateActionMode(android.view.ActionMode mode,
					Menu menu) {
				mode.getMenuInflater().inflate(R.menu.multi_select, menu);
				return true;

			}

			@Override
			public void onDestroyActionMode(android.view.ActionMode arg0) {
				// TODO Auto-generated method stub
				adapter.removeSelection();

			}

			@Override
			public boolean onPrepareActionMode(android.view.ActionMode arg0,
					Menu arg1) {
				return false;
			}

			@Override
			public void onItemCheckedStateChanged(android.view.ActionMode mode,
					int position, long id, boolean checked) {
				// TODO Auto-generated method stub
				// Capture total checked items
				Log.e("REACHED", "tue");
				final int checkedCount = lv.getCheckedItemCount();
				// Set the CAB title according to total checked items
				Log.e("checkedcount", Integer.toString(checkedCount));
				Log.e("positon", Integer.toString(position));
				mode.setTitle(checkedCount + " Selected");
				Log.e("positon2", Integer.toString(position));
				// Calls toggleSelection method from ListViewAdapter Class
				adapter.toggleSelection(position);
				Log.e("enter first", "true");

			}

		});

		read();

		//adding listener
		lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			public void onItemClick(AdapterView<?> adapterView, View view, int position, long l){

				int new_p = messageParcels.length - 1 - position;
				MessageParcel msg = messageParcels[new_p];

				if(msg.getRead() == 0){
					Intent setRead = new Intent(getActivity(), SetRead.class);
					Log.e("MY_INDEX", String.valueOf(position));
					setRead.putExtra("MY_INDEX", position);
					setRead.putExtra("FOLDER", "Inbox");
					setRead.putExtra("messageParcel", (Parcelable)msg);
					getActivity().startService(setRead);
				}


				Intent intent = new Intent(getActivity(), BodyActivity.class);
				intent.putExtra("messageParcel", (Parcelable)msg);
				intent.putExtra("MY_INDEX", position);
				intent.putExtra("FOLDER", "Inbox");
				startActivity(intent);


			}
		});

		return rootView;
	}

	private boolean isMyServiceRunning(Class<?> serviceClass) {
		ActivityManager manager = (ActivityManager) getActivity().getSystemService(Context.ACTIVITY_SERVICE);
		for (RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
			if (serviceClass.getName().equals(service.service.getClassName())) {
				return true;
			}
		}
		return false;
	}


	public static int read(){
		int top_unread = 0;
		//reading from file
		Log.e("READING", "TRUE");
		adapter.clear();
		ArrayList<MessageParcel> msgPs = new ArrayList<MessageParcel>();
		try{
			String path = context.getFilesDir().getPath();
			Log.e("PATH", path);
			FileInputStream fs = new FileInputStream(path+"/inbox.ser");
			ObjectInputStream os = new ObjectInputStream(fs);
			MessageParcel mp;
			while((mp = (MessageParcel)os.readObject()) != null){
				Log.e("Reading...", "true");
				msgPs.add(mp);
				String value="";

				value += mp.getFrom();
				Log.e("SUB", mp.getSub());
				value += "///" + mp.getDate();
				value += "///" + mp.getSub();

				if(mp.getRead() == 1)
					value += "///" + "read";
				else
					value += "///" + "unread";

				value += "///" + mp.getBody();

				if(mp.getFlagged() == 1)
					value += "///" + "flagged";
				else
					value += "///" + "unflagged";

				adapter.add(value);
			}

		} catch (FileNotFoundException e) {
			Log.e("Exception", "FileNotFound");
		} catch (EOFException e) {
			Log.e("Exception", "EOF");
		} catch(IOException e){
			Log.e("Exception", "IOException");
		} catch (ClassNotFoundException e) {
			Log.e("Exception", "ClassNotFound");
		}
		finally{
			if(msgPs.size() > 0){
				messageParcels = new MessageParcel[msgPs.size()];
				for(int i=msgPs.size()-1, j=0;i>=0;i--, j++){
					messageParcels[i] = msgPs.get(j);
				}
				for(int i=msgPs.size()-1;i>=0;i--){
					if(messageParcels[i].getRead() == 1){
						break;
					}
					else{
						top_unread +=1;
					}
				}
			}
		}
		return top_unread;
	}


	public class Receiver extends BroadcastReceiver
	{
		@Override
		public void onReceive(Context context, Intent intent)
		{
			Log.e("IN RECEIVER", "TRUE");
			int unread = read();

			if(unread != 0){
				String notif_text;
				if(unread == 1){

					notif_text = "";
					String path = context.getFilesDir().getPath();
					try {
						FileInputStream fs = new FileInputStream(path+"/inbox.ser");
						ObjectInputStream os = new ObjectInputStream(fs);
						MessageParcel mp = (MessageParcel) os.readObject();
						notif_text = mp.getFrom() + " -- " + mp.getSub();
					} catch (FileNotFoundException e) {
						e.printStackTrace();
					} catch (IOException e) {
						e.printStackTrace();
					} catch (ClassNotFoundException e) {
						e.printStackTrace();
					}
				}
				else
					notif_text = unread + " new messages.";

				Log.e("isInForeground", String.valueOf(isInForeground));
				if(!isInForeground && needNotification.equalsIgnoreCase("on")){
					Log.e("Notification", "true");
					Uri sound = Uri.parse("android.resource://" + context.getPackageName() + "/" + R.raw.notify);

					NotificationCompat.Builder mBuilder = 
							new NotificationCompat.Builder(context)
					.setSmallIcon(R.drawable.notification)
					.setContentTitle("Webmail")
					.setContentText(notif_text)
					.setAutoCancel(true)
					.setOnlyAlertOnce(true);

					if(needNotificationTone.equalsIgnoreCase("on"))
						mBuilder.setSound(sound);


					Intent resultIntent = new Intent(context, context.getClass());
					resultIntent.putExtra("NOTIF", "NOTIF");
					TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
					stackBuilder.addNextIntent(resultIntent);

					PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);

					mBuilder.setContentIntent(resultPendingIntent);

					NotificationManager mNotificationManager =
							(NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

					mNotificationManager.notify(3004, mBuilder.build());
				}

			}
		}
	}

}