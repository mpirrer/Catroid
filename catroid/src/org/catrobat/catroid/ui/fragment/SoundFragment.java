/**
 *  Catroid: An on-device visual programming system for Android devices
 *  Copyright (C) 2010-2012 The Catrobat Team
 *  (<http://developer.catrobat.org/credits>)
 *  
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as
 *  published by the Free Software Foundation, either version 3 of the
 *  License, or (at your option) any later version.
 *  
 *  An additional term exception under section 7 of the GNU Affero
 *  General Public License, version 3, is available at
 *  http://developer.catrobat.org/license_additional_term
 *  
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU Affero General Public License for more details.
 *  
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.catrobat.catroid.ui.fragment;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import org.catrobat.catroid.ProjectManager;
import org.catrobat.catroid.R;
import org.catrobat.catroid.common.Constants;
import org.catrobat.catroid.common.SoundInfo;
import org.catrobat.catroid.io.StorageHandler;
import org.catrobat.catroid.ui.SoundActivity;
import org.catrobat.catroid.ui.adapter.SoundAdapter;
import org.catrobat.catroid.ui.adapter.SoundAdapter.OnSoundEditListener;
import org.catrobat.catroid.ui.dialogs.DeleteSoundDialog;
import org.catrobat.catroid.ui.dialogs.RenameSoundDialog;
import org.catrobat.catroid.utils.ErrorListenerInterface;
import org.catrobat.catroid.utils.Utils;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Adapter;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ImageView;
import android.widget.ListView;

import com.actionbarsherlock.app.SherlockListFragment;

public class SoundFragment extends SherlockListFragment implements OnSoundEditListener,
		LoaderManager.LoaderCallbacks<Cursor>, OnClickListener {

	private class CopyAudioFilesTask extends AsyncTask<String, Void, File> {
		private ProgressDialog progressDialog = new ProgressDialog(getActivity());

		@Override
		protected void onPreExecute() {
			progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
			progressDialog.setTitle(getString(R.string.loading));
			progressDialog.show();
		}

		@Override
		protected File doInBackground(String... path) {
			File file = null;
			try {
				file = StorageHandler.getInstance().copySoundFile(path[0]);
			} catch (IOException e) {
				Log.e("CATROID", "Cannot load sound.", e);
			}
			return file;
		}

		@Override
		protected void onPostExecute(File file) {
			progressDialog.dismiss();

			if (file != null) {
				String fileName = file.getName();
				String soundTitle = fileName.substring(fileName.indexOf('_') + 1, fileName.lastIndexOf('.'));
				updateSoundAdapter(soundTitle, fileName);
			} else {
				Utils.displayErrorMessageFragment(getActivity().getSupportFragmentManager(),
						getString(R.string.error_load_sound));
			}
		}
	}

	private static final String BUNDLE_ARGUMENTS_SELECTED_SOUND = "selected_sound";
	private static final int ID_LOADER_MEDIA_IMAGE = 1;

	public static final int REQUEST_SELECT_MUSIC = 0;

	private MediaPlayer mediaPlayer;
	private SoundAdapter adapter;
	private ArrayList<SoundInfo> soundInfoList;

	private SoundInfo selectedSoundInfo;

	private int currentSoundPosition = -1;

	private View viewBelowSoundlistNonScrollable;
	private View soundlistFooterView;
	private View currentPlayingView;

	private SoundDeletedReceiver soundDeletedReceiver;
	private SoundRenamedReceiver soundRenamedReceiver;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View rootView = inflater.inflate(R.layout.fragment_sound, null);
		return rootView;
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

		ListView listView = getListView();

		registerForContextMenu(listView);

		if (savedInstanceState != null) {
			selectedSoundInfo = (SoundInfo) savedInstanceState.getSerializable(BUNDLE_ARGUMENTS_SELECTED_SOUND);
		}

		viewBelowSoundlistNonScrollable = getActivity().findViewById(R.id.view_below_soundlist_non_scrollable);
		viewBelowSoundlistNonScrollable.setOnClickListener(this);

		View footerView = getActivity().getLayoutInflater().inflate(R.layout.fragment_sound_soundlist_footer, listView,
				false);
		soundlistFooterView = footerView.findViewById(R.id.soundlist_footerview);
		ImageView footerAddImage = (ImageView) footerView.findViewById(R.id.soundlist_footerview_add_image);
		footerAddImage.setAlpha(Constants.FOOTER_ADD_ALPHA_VALUE);
		soundlistFooterView.setOnClickListener(this);
		listView.addFooterView(footerView);

		soundInfoList = ProjectManager.getInstance().getCurrentSprite().getSoundList();

		adapter = new SoundAdapter(getActivity(), R.layout.fragment_sound_soundlist_item, soundInfoList);
		setListAdapter(adapter);

		try {
			Utils.loadProjectIfNeeded(getActivity(), (ErrorListenerInterface) getActivity());
		} catch (ClassCastException exception) {
			Log.e("CATROID", getActivity().toString() + " does not implement ErrorListenerInterface", exception);
		}
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		outState.putSerializable(BUNDLE_ARGUMENTS_SELECTED_SOUND, selectedSoundInfo);
		super.onSaveInstanceState(outState);
	}

	public void startSelectSoundIntent() {
		Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
		intent.setType("audio/*");

		Log.d("Catroid", "SoundFragmend: startSelectSoundIntent()");

		startActivityForResult(Intent.createChooser(intent, getString(R.string.sound_select_source)),
				REQUEST_SELECT_MUSIC);
	}

	@Override
	public void onStart() {
		super.onStart();
		mediaPlayer = new MediaPlayer();
		initClickListener();
	}

	@Override
	public void onResume() {
		super.onResume();

		if (!Utils.checkForSdCard(getActivity())) {
			return;
		}

		if (soundRenamedReceiver == null) {
			soundRenamedReceiver = new SoundRenamedReceiver();
		}

		if (soundDeletedReceiver == null) {
			soundDeletedReceiver = new SoundDeletedReceiver();
		}

		IntentFilter intentFilterRenameSound = new IntentFilter(SoundActivity.ACTION_SOUND_RENAMED);
		getActivity().registerReceiver(soundRenamedReceiver, intentFilterRenameSound);

		IntentFilter intentFilterDeleteSound = new IntentFilter(SoundActivity.ACTION_SOUND_DELETED);
		getActivity().registerReceiver(soundDeletedReceiver, intentFilterDeleteSound);

		stopSound();
		reloadAdapter();
		addSoundViewsSetClickableFlag(true);
	}

	@Override
	public void onPause() {
		super.onPause();

		ProjectManager projectManager = ProjectManager.getInstance();
		if (projectManager.getCurrentProject() != null) {
			projectManager.saveProject();
		}
		stopSound();

		if (soundRenamedReceiver != null) {
			getActivity().unregisterReceiver(soundRenamedReceiver);
		}

		if (soundDeletedReceiver != null) {
			getActivity().unregisterReceiver(soundDeletedReceiver);
		}
	}

	@Override
	public void onStop() {
		super.onStop();
		mediaPlayer.reset();
		mediaPlayer.release();
		mediaPlayer = null;
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {

		Log.d("Catroid", "SoundFragmend: onActivityResult() result:" + resultCode + " request:" + requestCode);

		super.onActivityResult(requestCode, resultCode, data);

		//when new sound title is selected and ready to be added to the catroid project
		if (resultCode == Activity.RESULT_OK && requestCode == REQUEST_SELECT_MUSIC && data != null) {
			Bundle arguments = new Bundle();
			arguments.putParcelable(BUNDLE_ARGUMENTS_SELECTED_SOUND, data.getData());

			if (getLoaderManager().getLoader(ID_LOADER_MEDIA_IMAGE) == null) {
				getLoaderManager().initLoader(ID_LOADER_MEDIA_IMAGE, arguments, this);
			} else {
				getLoaderManager().restartLoader(ID_LOADER_MEDIA_IMAGE, arguments, this);
			}
		}
	}

	@Override
	public void onSoundPlay(View v) {
		handlePlaySoundButton(v);
	}

	@Override
	public void onSoundPause(View v) {
		handlePauseSoundButton(v);
	}

	@Override
	public Loader<Cursor> onCreateLoader(int id, Bundle arguments) {
		Uri audioUri = null;

		if (arguments != null) {
			audioUri = (Uri) arguments.get(BUNDLE_ARGUMENTS_SELECTED_SOUND);
		}
		String[] projection = { MediaStore.Audio.Media.DATA };
		return new CursorLoader(getActivity(), audioUri, projection, null, null, null);
	}

	@Override
	public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
		String audioPath = "";
		CursorLoader cursorLoader = (CursorLoader) loader;

		if (data == null) {
			audioPath = cursorLoader.getUri().getPath();
		} else {
			data.moveToFirst();
			audioPath = data.getString(data.getColumnIndex(MediaStore.Audio.Media.DATA));
		}

		if (audioPath.equalsIgnoreCase("")) {
			Utils.displayErrorMessageFragment(getActivity().getSupportFragmentManager(),
					getString(R.string.error_load_sound));
		} else {
			new CopyAudioFilesTask().execute(audioPath);
		}

		getLoaderManager().destroyLoader(ID_LOADER_MEDIA_IMAGE);
	}

	@Override
	public void onLoaderReset(Loader<Cursor> loader) {
	}

	@Override
	public void onClick(View v) {
		switch (v.getId()) {
			case R.id.view_below_soundlist_non_scrollable:
				addSoundViewsSetClickableFlag(false);
				startSelectSoundIntent();
				break;
			case R.id.soundlist_footerview:
				addSoundViewsSetClickableFlag(false);
				startSelectSoundIntent();
				break;
		}
	}

	private void addSoundViewsSetClickableFlag(boolean setClickableFlag) {
		viewBelowSoundlistNonScrollable.setClickable(setClickableFlag);
		soundlistFooterView.setClickable(setClickableFlag);
	}

	private void reloadAdapter() {
		soundInfoList = ProjectManager.getInstance().getCurrentSprite().getSoundList();
		adapter = new SoundAdapter(getActivity(), R.layout.fragment_sound_soundlist_item, soundInfoList);
		adapter.setOnSoundEditListener(this);
		setListAdapter(adapter);
		adapter.notifyDataSetChanged();
	}

	private void updateSoundAdapter(String title, String fileName) {
		title = Utils.getUniqueSoundName(title);

		SoundInfo newSoundInfo = new SoundInfo();
		newSoundInfo.setTitle(title);
		newSoundInfo.setSoundFileName(fileName);
		soundInfoList.add(newSoundInfo);
		adapter.notifyDataSetChanged();

		//scroll down the list to the new item:
		{
			final ListView listView = getListView();
			listView.post(new Runnable() {
				@Override
				public void run() {
					listView.setSelection(listView.getCount() - 1);
				}
			});
		}
	}

	public void handlePlaySoundButton(View v) {
		final int position = (Integer) v.getTag();
		final SoundInfo soundInfo = soundInfoList.get(position);

		stopSound();
		if (!soundInfo.isPlaying) {
			startSound(soundInfo);
		}

		mediaPlayer.setOnCompletionListener(new OnCompletionListener() {
			@Override
			public void onCompletion(MediaPlayer mp) {
				soundInfo.isPlaying = false;
				((SoundAdapter) getListAdapter()).notifyDataSetChanged();
			}
		});

		((SoundAdapter) getListAdapter()).notifyDataSetChanged();
	}

	public void handlePauseSoundButton(View v) {
		final int position = (Integer) v.getTag();
		pauseSound(soundInfoList.get(position));
		((SoundAdapter) getListAdapter()).notifyDataSetChanged();
	}

	public void pauseSound(SoundInfo soundInfo) {
		mediaPlayer.pause();
		soundInfo.isPlaying = false;
	}

	public void stopSound() {
		if (mediaPlayer.isPlaying()) {
			mediaPlayer.stop();
		}

		for (int i = 0; i < soundInfoList.size(); i++) {
			soundInfoList.get(i).isPlaying = false;
		}
	}

	public void startSound(SoundInfo soundInfo) {
		if (!soundInfo.isPlaying) {
			try {
				mediaPlayer.reset();
				mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
				mediaPlayer.setDataSource(soundInfo.getAbsolutePath());
				mediaPlayer.prepare();
				mediaPlayer.start();

				soundInfo.isPlaying = true;
			} catch (IOException e) {
				Log.e("CATROID", "Cannot start sound.", e);
			}
		}
	}

	private void initClickListener() {

		Log.d("Catroid", "SoundFragmend: initClickListener()");

		getListView().setOnItemLongClickListener(new OnItemLongClickListener() {
			@Override
			public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
				currentSoundPosition = position;
				selectedSoundInfo = soundInfoList.get(currentSoundPosition);

				// return false to open contexMenu
				return false;
			}
		});
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);

		//handlePauseSoundButton(v);

		AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;

		Adapter contextAdapter = getListAdapter();
		selectedSoundInfo = (SoundInfo) contextAdapter.getItem(info.position);

		menu.setHeaderTitle(selectedSoundInfo.getTitle());

		getSherlockActivity().getMenuInflater().inflate(R.menu.context_menu_default, menu);
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.copy:
				break;

			case R.id.cut:
				break;

			case R.id.insert_below:
				break;

			case R.id.move:
				break;

			case R.id.rename:
				showRenameDialog();
				break;

			case R.id.delete:
				showDeleteDialog();
				break;
		}
		return super.onContextItemSelected(item);
	}

	private void showRenameDialog() {
		RenameSoundDialog renameSoundDialog = RenameSoundDialog.newInstance(selectedSoundInfo.getTitle());
		renameSoundDialog.show(getFragmentManager(), RenameSoundDialog.DIALOG_FRAGMENT_TAG);
	}

	private void showDeleteDialog() {
		if (currentSoundPosition != -1) {
			DeleteSoundDialog deleteSoundDialog = DeleteSoundDialog.newInstance(currentSoundPosition);
			deleteSoundDialog.show(getFragmentManager(), DeleteSoundDialog.DIALOG_FRAGMENT_TAG);
		} else {
			Log.e("CATROID", "No sound selected!");
		}
	}

	private class SoundDeletedReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (intent.getAction().equals(SoundActivity.ACTION_SOUND_DELETED)) {
				reloadAdapter();
			}
		}
	}

	private class SoundRenamedReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (intent.getAction().equals(SoundActivity.ACTION_SOUND_RENAMED)) {
				String newSoundTitle = intent.getExtras().getString(RenameSoundDialog.EXTRA_NEW_SOUND_TITLE);

				if (newSoundTitle != null && !newSoundTitle.equalsIgnoreCase("")) {
					selectedSoundInfo.setTitle(newSoundTitle);
					adapter.notifyDataSetChanged();
				}
			}
		}
	}
}
